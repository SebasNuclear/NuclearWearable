package com.nuclear.soundconnect.bluetooth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Cliente de comunicación de bajo nivel Bluetooth RFCOMM para audífonos Sony.
 * Soporta sesión persistente con lector asíncrono continuo (Zero Polling / Push Live),
 * protocolo dual V1/V2 (0x0C + 0x0E) y transacciones one-shot/Control Fase 2,
 * completamente independiente de Android y listo para Linux BlueZ / JNA RFCOMM.
 */
class SonyRfcommClient(private val connector: RfcommConnector) {

    companion object {
        // UUID Oficial Sony MDR Protocol V2 (Tándem / Modernos WF-1000XM & WH-1000XM)
        val UUID_SONY_V2: UUID = UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2")
        // UUID Alternativo / Fallback Sony V1
        val UUID_SONY_FALLBACK: UUID = UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba")
        // UUID Estándar Serial Port Profile (SPP Fallback)
        val UUID_SONY_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        fun isSonyDeviceName(name: String?): Boolean {
            val n = (name ?: "").uppercase()
            if (n.isBlank()) return false
            return n.contains("SONY") ||
                   n.startsWith("WF-") ||
                   n.startsWith("WH-") ||
                   n.startsWith("WI-") ||
                   n.startsWith("MDR-") ||
                   n.contains("1000XM") ||
                   n.contains("LINKBUDS") ||
                   n.contains("ULT WEAR") ||
                   n.contains("INZONE")
        }

        fun isSonyAudioDevice(device: SonyBluetoothDevice): Boolean {
            val name = device.name ?: ""
            if (isSonyDeviceName(name)) return true
            for (u in device.uuids) {
                if (u == UUID_SONY_V2 || u == UUID_SONY_FALLBACK) {
                    return true
                }
            }
            return false
        }
    }

    private val sessionLifecycleMutex = Mutex()
    private val oneShotMutex = Mutex()
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var persistentSession: SessionTransport? = null

    // Caché por dirección MAC de metadatos verificados para evitar re-handshakes lentos en widgets/one-shot
    private val verifiedProtocolVersions = ConcurrentHashMap<String, Int>()
    private val verifiedHardwareModels = ConcurrentHashMap<String, String>()
    private val verifiedFirmwareVersions = ConcurrentHashMap<String, String>()

    private val _hexLogs = MutableSharedFlow<HexLogEntry>(replay = 50, extraBufferCapacity = 64)
    val hexLogs: SharedFlow<HexLogEntry> = _hexLogs.asSharedFlow()

    private val _liveStateUpdates = MutableSharedFlow<HeadsetState>(extraBufferCapacity = 32)
    val liveStateUpdates: SharedFlow<HeadsetState> = _liveStateUpdates.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<Pair<String, Boolean>> = _connectionEvents.asSharedFlow()

    private suspend fun log(direction: HexLogEntry.Direction, hex: String, summary: String) {
        _hexLogs.emit(HexLogEntry(direction, hex, summary))
    }

    private fun logNonSuspend(direction: HexLogEntry.Direction, hex: String, summary: String) {
        _hexLogs.tryEmit(HexLogEntry(direction, hex, summary))
    }

    private fun isDeviceConnected(device: SonyBluetoothDevice): Boolean = device.isConnected

    fun isSessionActive(address: String? = null): Boolean {
        val current = persistentSession ?: return false
        if (!current.isAlive()) return false
        return address.isNullOrBlank() || current.address.equals(address, ignoreCase = true)
    }

    /**
     * Sincroniza preferencias del usuario (ej. modelo forzado manual) hacia la sesión persistente activa
     * para evitar que notificaciones push entrantes sobreescriban ajustes locales.
     */
    fun updateSessionContext(state: HeadsetState) {
        val current = persistentSession ?: return
        if (!current.isAlive()) return
        current.updateStateLocked { s ->
            if (!s.deviceAddress.equals(state.deviceAddress, ignoreCase = true) && s.deviceAddress.isNotEmpty()) {
                s
            } else {
                s.copy(
                    forcedModelFamily = state.forcedModelFamily,
                    cachedModelFamily = state.cachedModelFamily,
                    ambientLevel = state.ambientLevel,
                    voiceFocus = state.voiceFocus
                )
            }
        }
    }

    /**
     * Busca entre los dispositivos vinculados el dispositivo Sony más prioritario.
     * Prioriza dispositivos activamente conectados antes de caer en orden estático de vinculación.
     */
    fun findTargetDevice(preferredAddress: String? = null): SonyBluetoothDevice? {
        if (!connector.isAdapterPowered()) return null
        val bonded = connector.findPairedDevices()
        if (bonded.isEmpty()) return null

        // RESTRICCIÓN ESTRICTA A SONY: Filtrar únicamente dispositivos Sony
        val sonyBonded = bonded.filter { isSonyAudioDevice(it) }
        if (sonyBonded.isEmpty()) return null

        // 1. Si hay una dirección preferida especificada
        if (!preferredAddress.isNullOrBlank()) {
            val pref = sonyBonded.firstOrNull { it.address.equals(preferredAddress, ignoreCase = true) }
            if (pref != null) {
                if (isDeviceConnected(pref)) return pref
            }
        }

        // 2. Priorizar cualquier dispositivo Sony que esté ACTIVAMENTE CONECTADO
        val connectedSony = sonyBonded.filter { isDeviceConnected(it) }
        if (connectedSony.isNotEmpty()) {
            if (!preferredAddress.isNullOrBlank()) {
                val prefConnected = connectedSony.firstOrNull { it.address.equals(preferredAddress, ignoreCase = true) }
                if (prefConnected != null) return prefConnected
            }

            val connectedXm6 = connectedSony.firstOrNull {
                val name = it.name?.uppercase() ?: ""
                name.contains("WF-1000XM6") || name.contains("WH-1000XM6")
            }
            if (connectedXm6 != null) return connectedXm6

            val connectedFlagship = connectedSony.firstOrNull {
                val name = it.name?.uppercase() ?: ""
                name.contains("1000XM") || name.contains("LINKBUDS") || name.contains("ULT WEAR")
            }
            if (connectedFlagship != null) return connectedFlagship

            return connectedSony.first()
        }

        // 3. Si ninguno reporta conexión activa (fallback entre vinculados)
        if (!preferredAddress.isNullOrBlank()) {
            val pref = sonyBonded.firstOrNull { it.address.equals(preferredAddress, ignoreCase = true) }
            if (pref != null) return pref
        }

        val xm6 = sonyBonded.firstOrNull {
            val name = it.name?.uppercase() ?: ""
            name.contains("WF-1000XM6") || name.contains("WH-1000XM6")
        }
        if (xm6 != null) return xm6

        val sonyFlagship = sonyBonded.firstOrNull {
            val name = it.name?.uppercase() ?: ""
            name.contains("1000XM") || name.contains("LINKBUDS") || name.contains("ULT WEAR")
        }
        if (sonyFlagship != null) return sonyFlagship

        return sonyBonded.firstOrNull()
    }

    /**
     * Determina el tipo de dispositivo (TWS o Diadema Over-Ear) según el nombre comercial.
     */
    fun detectDeviceType(device: SonyBluetoothDevice, forcedFamily: SonyModelFamily? = null): DeviceType {
        if (forcedFamily != null) return forcedFamily.defaultDeviceType
        val name = device.name?.uppercase() ?: ""
        val hwModel = verifiedHardwareModels[device.address]
        val hwFamily = if (!hwModel.isNullOrBlank()) SonyModelFamily.detect(hwModel) else null
        val family = if (hwFamily != null && hwFamily != SonyModelFamily.GENERIC_TWS && hwFamily != SonyModelFamily.GENERIC_OVER_EAR) {
            hwFamily
        } else {
            SonyModelFamily.detect(name)
        }
        return family.defaultDeviceType
    }

    /**
     * Abre o reutiliza la sesión RFCOMM persistente hacia un dispositivo Sony, ejecutando el handshake
     * completo V1/V2 + consulta inicial y manteniendo el lector asíncrono en vivo activo.
     */
    suspend fun ensurePersistentSession(
        device: SonyBluetoothDevice,
        initialState: HeadsetState
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        sessionLifecycleMutex.withLock {
            val existing = persistentSession
            if (existing != null && existing.isAlive() && existing.address.equals(device.address, ignoreCase = true)) {
                updateSessionContext(initialState)
                return@withContext Result.success(existing.snapshotState())
            }

            // Cerrar sesión anterior si apuntaba a otro MAC o murió silenciosamente
            existing?.close("Reemplazando sesión anterior")
            persistentSession = null

            log(HexLogEntry.Direction.INFO, "", "Abriendo sesión RFCOMM persistente con ${safeDeviceName(device)}...")
            val conn = connectBestSocket(device)
                ?: return@withContext Result.failure(Exception("Socket RFCOMM no disponible"))

            val seededState = seedCachedMetadata(device, initialState)
            val transport = SessionTransport(
                device = device,
                socket = conn.first,
                connectedUuid = conn.second,
                initialState = seededState,
                emitLivePush = true,
                onClosed = { mac ->
                    if (persistentSession?.address.equals(mac, ignoreCase = true) && persistentSession?.isAlive() == false) {
                        persistentSession = null
                    }
                    _connectionEvents.tryEmit(mac to false)
                }
            )
            transport.startReader(clientScope)
            persistentSession = transport
            _connectionEvents.tryEmit(device.address to true)

            val resultState = runCatching {
                performHandshakeAndTelemetry(transport, includeExtendedMetadata = true)
            }.getOrElse { err ->
                transport.close("Fallo en handshake inicial: ${err.message}")
                if (persistentSession === transport) persistentSession = null
                return@withContext Result.failure(err)
            }

            Result.success(resultState.copy(isConnected = true, isBusy = false))
        }
    }

    suspend fun closePersistentSession(reason: String = "Desconexión solicitada") {
        sessionLifecycleMutex.withLock {
            persistentSession?.close(reason)
            persistentSession = null
        }
    }

    /**
     * Sincroniza telemetría de batería, códec y modo de control de ruido.
     * Reutiliza la sesión persistente si está abierta; en caso contrario ejecuta one-shot seguro (Wear / Widget / Tile).
     */
    suspend fun syncTelemetry(device: SonyBluetoothDevice, currentState: HeadsetState): Result<HeadsetState> =
        withContext(Dispatchers.IO) {
            val existing = persistentSession
            if (existing != null && existing.isAlive() && existing.address.equals(device.address, ignoreCase = true)) {
                updateSessionContext(currentState)
                val liveRes = runCatching {
                    performHandshakeAndTelemetry(
                        existing,
                        includeExtendedMetadata = !verifiedHardwareModels.containsKey(device.address)
                    )
                }
                if (liveRes.isSuccess) {
                    return@withContext Result.success(liveRes.getOrThrow().copy(isConnected = true, isBusy = false))
                }
                log(HexLogEntry.Direction.INFO, "", "Sesión persistente interrumpida durante sync, reintentando one-shot...")
                sessionLifecycleMutex.withLock {
                    if (persistentSession === existing) {
                        existing.close("Error en sync sobre sesión")
                        persistentSession = null
                    }
                }
            }

            oneShotMutex.withLock {
                var transport: SessionTransport? = null
                try {
                    log(HexLogEntry.Direction.INFO, "", "Iniciando sesión RFCOMM one-shot con ${safeDeviceName(device)}...")
                    val conn = connectBestSocket(device)
                        ?: return@withContext Result.failure(Exception("Socket RFCOMM no disponible"))

                    val seededState = seedCachedMetadata(device, currentState)
                    transport = SessionTransport(
                        device = device,
                        socket = conn.first,
                        connectedUuid = conn.second,
                        initialState = seededState,
                        emitLivePush = false,
                        onClosed = null
                    )
                    transport.startReader(this)

                    val needMetadata = seededState.hardwareModelName.isBlank() && !verifiedFirmwareVersions.containsKey(device.address)
                    val updated = performHandshakeAndTelemetry(transport, includeExtendedMetadata = needMetadata)
                    Result.success(updated.copy(isConnected = true, isBusy = false))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(HexLogEntry.Direction.ERROR, "", "Error en sesión RFCOMM: ${e.localizedMessage}")
                    Result.failure(e)
                } finally {
                    transport?.close("Fin de transacción one-shot")
                }
            }
        }

    /**
     * Envía comando para conmutar modo de ruido (ANC / OFF / AMBIENT).
     * Reutiliza el socket persistente (<15ms) si existe; de lo contrario conecta brevemente one-shot.
     */
    suspend fun setNoiseMode(
        device: SonyBluetoothDevice,
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        currentState: HeadsetState
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        val targetState = currentState.copy(
            noiseMode = mode,
            ambientLevel = ambientLevel,
            voiceFocus = voiceFocus
        )

        val existing = persistentSession
        if (existing != null && existing.isAlive() && existing.address.equals(device.address, ignoreCase = true)) {
            val res = runCatching {
                executeSetNoiseMode(
                    transport = existing,
                    mode = mode,
                    ambientLevel = ambientLevel,
                    voiceFocus = voiceFocus,
                    verifyProtocolFirst = false,
                    throwOnNoAck = true
                )
            }
            if (res.isSuccess) {
                return@withContext Result.success(res.getOrThrow().copy(isConnected = true, isBusy = false))
            }
            log(HexLogEntry.Direction.INFO, "", "Reintentando SET_NC_ASM fuera de sesión caída...")
            sessionLifecycleMutex.withLock {
                if (persistentSession === existing) {
                    existing.close("Socket caído en SET_NC_ASM")
                    persistentSession = null
                }
            }
        }

        oneShotMutex.withLock {
            var transport: SessionTransport? = null
            try {
                log(HexLogEntry.Direction.INFO, "", "SET_NC_ASM one-shot hacia ${safeDeviceName(device)}...")
                val conn = connectBestSocket(device)
                    ?: return@withContext Result.failure(Exception("Socket RFCOMM no disponible"))

                val seeded = seedCachedMetadata(device, targetState)
                transport = SessionTransport(
                    device = device,
                    socket = conn.first,
                    connectedUuid = conn.second,
                    initialState = seeded,
                    emitLivePush = false,
                    onClosed = null
                )
                transport.startReader(this)

                val needProtoProbe = !verifiedProtocolVersions.containsKey(device.address) && !isProtocolExplicitlyKnown(seeded, conn.second)
                val updated = executeSetNoiseMode(
                    transport = transport,
                    mode = mode,
                    ambientLevel = ambientLevel,
                    voiceFocus = voiceFocus,
                    verifyProtocolFirst = needProtoProbe,
                    throwOnNoAck = false
                )
                Result.success(updated.copy(isConnected = true, isBusy = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(HexLogEntry.Direction.ERROR, "", "Error en SET_NC_ASM RFCOMM: ${e.localizedMessage}")
                Result.failure(e)
            } finally {
                transport?.close("Fin de SET_NC_ASM one-shot")
            }
        }
    }

    private suspend fun executeSetNoiseMode(
        transport: SessionTransport,
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        verifyProtocolFirst: Boolean,
        throwOnNoAck: Boolean
    ): HeadsetState {
        return transport.commandMutex.withLock {
            if (!transport.isAlive()) throw IOException("Socket desconectado")

            var protoVersion = verifiedProtocolVersions[transport.address]
                ?: inferSafeProtocolVersion(transport.snapshotState(), transport.connectedUuid)

            if (verifyProtocolFirst) {
                val pFrame = transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_PROTOCOL_INFO, 0x00),
                    expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_PROTOCOL_INFO,
                    timeoutMs = 600L
                )
                if (pFrame != null) {
                    val detectedVer = SonyProtocolParser.parseProtocolInfoPayload(pFrame.payload)
                    if (detectedVer != null) {
                        protoVersion = detectedVer
                        verifiedProtocolVersions[transport.address] = detectedVer
                        transport.updateStateLocked { it.copy(protocolVersion = detectedVer) }
                    }
                }
            }

            val snap = transport.snapshotState()
            val setPayload = if (protoVersion == 1) {
                SonyProtocolParser.buildSetNcAsmV1Payload(mode, ambientLevel, voiceFocus)
            } else if (snap.capabilities.supportsWindNoiseReduction) {
                SonyProtocolParser.buildSetNcAsmWithWindPayload(mode, ambientLevel, voiceFocus, snap.windNoiseReduction)
            } else {
                SonyProtocolParser.buildSetNcAsmPayload(mode, ambientLevel, voiceFocus)
            }

            // Stop-and-Wait con confirmación ACK (1 reintento si la primera trama no recibe ACK)
            var acked = transport.sendAndAwaitAckLocked(setPayload, description = "SET_NC_ASM ($mode)", timeoutMs = 300L)
            if (!acked && transport.isAlive()) {
                acked = transport.sendAndAwaitAckLocked(setPayload, description = "SET_NC_ASM reintento", timeoutMs = 300L)
            }
            if (!acked && throwOnNoAck) {
                throw IOException("Sin ACK en sesión persistente")
            }

            // Delay acústico para asentamiento del DSP de los audífonos Sony
            delay(65L)

            val finalState = transport.updateStateLocked {
                it.copy(
                    noiseMode = mode,
                    ambientLevel = ambientLevel,
                    voiceFocus = voiceFocus,
                    protocolVersion = protoVersion,
                    isConnected = true,
                    isBusy = false
                )
            }
            if (transport.emitLivePush) {
                _liveStateUpdates.tryEmit(finalState)
            }
            finalState
        }
    }

    /**
     * Handshake V1/V2 secuencial estricto con Stop-and-Wait ARQ + telemetría inicial/refresco.
     * Garantiza que NUNCA se envíe opcode 0x22 (POWER OFF en V1) sin confirmación de V2.
     */
    private suspend fun performHandshakeAndTelemetry(
        transport: SessionTransport,
        includeExtendedMetadata: Boolean
    ): HeadsetState {
        return transport.commandMutex.withLock {
            if (!transport.isAlive()) throw IOException("Socket RFCOMM cerrado")

            // 1. Handshake obligatorio PROTOCOL_INFO (0x00 -> 0x01) si no se ha verificado aún
            var protoVersion = verifiedProtocolVersions[transport.address]
            if (protoVersion == null) {
                var protoFrame = transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_PROTOCOL_INFO, 0x00),
                    expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_PROTOCOL_INFO,
                    timeoutMs = 1100L
                )
                if (protoFrame == null && transport.isAlive()) {
                    delay(120L)
                    protoFrame = transport.sendAndAwaitFrameLocked(
                        payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_PROTOCOL_INFO, 0x00),
                        expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_PROTOCOL_INFO,
                        timeoutMs = 900L
                    )
                }

                val detectedVer = if (protoFrame != null) {
                    SonyProtocolParser.parseProtocolInfoPayload(protoFrame.payload)
                } else null

                protoVersion = if (detectedVer != null) {
                    verifiedProtocolVersions[transport.address] = detectedVer
                    transport.updateStateLocked { it.copy(protocolVersion = detectedVer) }
                    log(HexLogEntry.Direction.INFO, "", "Handshake Sony completado -> Protocolo V$detectedVer")
                    detectedVer
                } else {
                    val fallbackProto = inferSafeProtocolVersion(transport.snapshotState(), transport.connectedUuid)
                    transport.updateStateLocked { it.copy(protocolVersion = fallbackProto) }
                    log(HexLogEntry.Direction.INFO, "", "Sin respuesta PROTOCOL_INFO; usando inferencia segura V$fallbackProto")
                    fallbackProto
                }
            }

            // 2. Capa 1: Nombre real del modelo & Soporte ANC (V2) para resolver renombre Bluetooth antes de consultar NC/ASM
            if (includeExtendedMetadata && protoVersion >= 2 && transport.isAlive()) {
                if (transport.snapshotState().hardwareModelName.isBlank()) {
                    transport.sendAndAwaitFrameLocked(
                        payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_DEVICE_INFO, SonyProtocolParser.DEVICE_INFO_MODEL_NAME),
                        expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_DEVICE_INFO,
                        expectedSubtype = SonyProtocolParser.DEVICE_INFO_MODEL_NAME.toInt() and 0xFF,
                        timeoutMs = 650L
                    )
                }
                if (transport.snapshotState().supportFunctions.isEmpty() && transport.isAlive()) {
                    transport.sendAndAwaitFrameLocked(
                        payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_SUPPORT_FUNCTION, 0x00),
                        expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_SUPPORT_FUNCTION,
                        timeoutMs = 650L
                    )
                }
                if (transport.snapshotState().table2SupportFunctions.isEmpty() && transport.isAlive()) {
                    transport.sendAndAwaitFrameLocked(
                        payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_SUPPORT_FUNCTION, 0x00),
                        expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_SUPPORT_FUNCTION,
                        dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2,
                        timeoutMs = 550L
                    )
                }
            }

            // 3. Consulta de Batería estrictamente aislada por versión de protocolo (V1=0x10, V2=0x22)
            val currentSnapshot = transport.snapshotState()
            if (protoVersion == 1) {
                queryBatteryV1(transport, currentSnapshot.deviceType)
            } else {
                queryBatteryV2(transport, currentSnapshot.deviceType)
            }

            // 4. Estado actual de Cancelación de Ruido / Sonido Ambiente
            val afterBattSnapshot = transport.snapshotState()
            if (afterBattSnapshot.effectiveHasAnc && transport.isAlive()) {
                val category = SonyProtocolParser.ncAsmCategoryFor(protoVersion, afterBattSnapshot.modelFamily)
                transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_GET_NC_ASM, category),
                    expectedOpcode = SonyProtocolParser.CMD_RET_NC_ASM,
                    timeoutMs = 800L
                )
            }

            if (!transport.receivedAnyValidFrame) {
                throw IOException("Sin respuesta válida del protocolo Sony MDR")
            }

            // 5. Versión de firmware y códec Bluetooth activo (LDAC / AAC / SBC / aptX / LC3)
            if (includeExtendedMetadata && transport.snapshotState().firmwareVersion.isBlank() && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_DEVICE_INFO, SonyProtocolParser.DEVICE_INFO_FW_VERSION),
                    expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_DEVICE_INFO,
                    expectedSubtype = SonyProtocolParser.DEVICE_INFO_FW_VERSION.toInt() and 0xFF,
                    timeoutMs = 600L
                )
            }

            if (transport.isAlive()) {
                if (protoVersion == 1) {
                    transport.sendAndAwaitFrameLocked(
                        payload = byteArrayOf(SonyProtocolParser.CMD_AUDIO_CODEC_GET_V1, 0x00),
                        expectedOpcode = SonyProtocolParser.CMD_AUDIO_CODEC_RET_V1,
                        timeoutMs = 550L
                    )
                } else {
                    transport.sendAndAwaitFrameLocked(
                        payload = byteArrayOf(SonyProtocolParser.CMD_COMMON_GET_STATUS_V2, SonyProtocolParser.COMMON_TYPE_AUDIO_CODEC),
                        expectedOpcode = SonyProtocolParser.CMD_COMMON_RET_STATUS_V2,
                        expectedSubtype = SonyProtocolParser.COMMON_TYPE_AUDIO_CODEC.toInt() and 0xFF,
                        timeoutMs = 550L
                    )
                }
            }

            val finalState = transport.snapshotState()
            if (transport.emitLivePush) {
                _liveStateUpdates.tryEmit(finalState)
            }
            finalState
        }
    }

    private suspend fun queryBatteryV1(transport: SessionTransport, deviceType: DeviceType) {
        if (!transport.isAlive()) return
        val singleFrame = transport.sendAndAwaitFrameLocked(
            payload = byteArrayOf(SonyProtocolParser.CMD_GET_BATTERY_V1, SonyProtocolParser.BATTERY_TYPE_SINGLE),
            expectedOpcode = SonyProtocolParser.CMD_RET_BATTERY_V1,
            expectedSubtype = SonyProtocolParser.BATTERY_TYPE_SINGLE.toInt() and 0xFF,
            timeoutMs = 750L
        )
        val gotSingle = singleFrame != null && transport.snapshotState().singleBattery.isAvailable
        if (!gotSingle || deviceType == DeviceType.TWS) {
            if (transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_GET_BATTERY_V1, SonyProtocolParser.BATTERY_TYPE_DUAL_LEGACY),
                    expectedOpcode = SonyProtocolParser.CMD_RET_BATTERY_V1,
                    expectedSubtype = SonyProtocolParser.BATTERY_TYPE_DUAL_LEGACY.toInt() and 0xFF,
                    timeoutMs = 650L
                )
            }
            if (transport.isAlive() && deviceType == DeviceType.TWS) {
                transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_GET_BATTERY_V1, SonyProtocolParser.BATTERY_TYPE_CASE_LEGACY),
                    expectedOpcode = SonyProtocolParser.CMD_RET_BATTERY_V1,
                    expectedSubtype = SonyProtocolParser.BATTERY_TYPE_CASE_LEGACY.toInt() and 0xFF,
                    timeoutMs = 650L
                )
            }
        }
    }

    private suspend fun queryBatteryV2(transport: SessionTransport, deviceType: DeviceType) {
        if (!transport.isAlive()) return
        if (deviceType == DeviceType.OVER_EAR) {
            transport.sendAndAwaitFrameLocked(
                payload = byteArrayOf(SonyProtocolParser.CMD_GET_BATTERY, SonyProtocolParser.BATTERY_TYPE_SINGLE),
                expectedOpcode = SonyProtocolParser.CMD_RET_BATTERY,
                expectedSubtype = SonyProtocolParser.BATTERY_TYPE_SINGLE.toInt() and 0xFF,
                timeoutMs = 750L
            )
            return
        }

        // TWS V2: Dual (0x09) + Case (0x0A), con respaldo legacy 0x01 / 0x00 / 0x02 si el modelo usa subtipo clásico V2
        transport.sendAndAwaitFrameLocked(
            payload = byteArrayOf(SonyProtocolParser.CMD_GET_BATTERY, SonyProtocolParser.BATTERY_TYPE_DUAL_V2),
            expectedOpcode = SonyProtocolParser.CMD_RET_BATTERY,
            expectedSubtype = SonyProtocolParser.BATTERY_TYPE_DUAL_V2.toInt() and 0xFF,
            timeoutMs = 700L
        )
        if (transport.isAlive()) {
            transport.sendAndAwaitFrameLocked(
                payload = byteArrayOf(SonyProtocolParser.CMD_GET_BATTERY, SonyProtocolParser.BATTERY_TYPE_CASE_V2),
                expectedOpcode = SonyProtocolParser.CMD_RET_BATTERY,
                expectedSubtype = SonyProtocolParser.BATTERY_TYPE_CASE_V2.toInt() and 0xFF,
                timeoutMs = 650L
            )
        }

        val snapAfterDual = transport.snapshotState()
        if (!snapAfterDual.leftBattery.isAvailable && !snapAfterDual.rightBattery.isAvailable && transport.isAlive()) {
            transport.sendAndAwaitFrameLocked(
                payload = byteArrayOf(SonyProtocolParser.CMD_GET_BATTERY, SonyProtocolParser.BATTERY_TYPE_DUAL_LEGACY),
                expectedOpcode = SonyProtocolParser.CMD_RET_BATTERY,
                expectedSubtype = SonyProtocolParser.BATTERY_TYPE_DUAL_LEGACY.toInt() and 0xFF,
                timeoutMs = 600L
            )
            if (transport.isAlive() && !transport.snapshotState().caseBattery.isAvailable) {
                transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_GET_BATTERY, SonyProtocolParser.BATTERY_TYPE_CASE_LEGACY),
                    expectedOpcode = SonyProtocolParser.CMD_RET_BATTERY,
                    expectedSubtype = SonyProtocolParser.BATTERY_TYPE_CASE_LEGACY.toInt() and 0xFF,
                    timeoutMs = 550L
                )
            }
            if (transport.isAlive() && !transport.snapshotState().leftBattery.isAvailable && !transport.snapshotState().singleBattery.isAvailable) {
                transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_GET_BATTERY, SonyProtocolParser.BATTERY_TYPE_SINGLE),
                    expectedOpcode = SonyProtocolParser.CMD_RET_BATTERY,
                    expectedSubtype = SonyProtocolParser.BATTERY_TYPE_SINGLE.toInt() and 0xFF,
                    timeoutMs = 550L
                )
            }
        }
    }

    private fun seedCachedMetadata(device: SonyBluetoothDevice, base: HeadsetState): HeadsetState {
        val cachedProto = verifiedProtocolVersions[device.address] ?: inferSafeProtocolVersion(base, null)
        val cachedHw = base.hardwareModelName.ifBlank { verifiedHardwareModels[device.address] ?: "" }
        val cachedFw = base.firmwareVersion.ifBlank { verifiedFirmwareVersions[device.address] ?: "" }
        return base.copy(
            protocolVersion = cachedProto,
            hardwareModelName = cachedHw,
            firmwareVersion = cachedFw
        )
    }

    private fun isKnownV1Model(family: SonyModelFamily, rawName: String): Boolean {
        val n = "${family.name} ${family.displayName} $rawName".uppercase()
        return n.contains("XM3") ||
            n.contains("WH-1000XM4") || n.contains("WH_1000XM4") ||
            n.contains("SP800N") || n.contains("WI-1000X") ||
            n.contains("XB950") || n.contains("H910N") ||
            n.contains("CH710N") || n.contains("XB900")
    }

    private fun isKnownV2Model(family: SonyModelFamily, rawName: String): Boolean {
        val n = "${family.name} ${family.displayName} $rawName".uppercase()
        return n.contains("WF-1000XM4") || n.contains("WF_1000XM4") ||
            n.contains("XM5") || n.contains("XM6") ||
            n.contains("LINKBUDS") || n.contains("ULT") ||
            n.contains("CH720N") || n.contains("CH520") ||
            n.contains("INZONE") || n.contains("C700N") || n.contains("C510")
    }

    private fun isProtocolExplicitlyKnown(state: HeadsetState, uuid: UUID): Boolean {
        val raw = "${state.deviceName} ${state.hardwareModelName}"
        return isKnownV1Model(state.modelFamily, raw) ||
            (isKnownV2Model(state.modelFamily, raw) && uuid == UUID_SONY_V2)
    }

    private fun inferSafeProtocolVersion(state: HeadsetState, connectedUuid: UUID?): Int {
        val raw = "${state.deviceName} ${state.hardwareModelName}"
        if (isKnownV1Model(state.modelFamily, raw)) return 1
        if ((connectedUuid == UUID_SONY_FALLBACK || connectedUuid == UUID_SONY_SPP) && !isKnownV2Model(state.modelFamily, raw)) return 1
        return if (isKnownV2Model(state.modelFamily, raw)) 2 else 1
    }

    private fun connectBestSocket(device: SonyBluetoothDevice): Pair<RfcommSocketConnection, UUID>? {
        val s1 = connectSocket(device, UUID_SONY_V2)
        if (s1 != null) return s1 to UUID_SONY_V2
        val s2 = connectSocket(device, UUID_SONY_FALLBACK)
        if (s2 != null) return s2 to UUID_SONY_FALLBACK
        val s3 = connectSocket(device, UUID_SONY_SPP)
        if (s3 != null) return s3 to UUID_SONY_SPP
        return null
    }

    // =========================================================================
    // FASE 2: TRANSACCIONES GENÉRICAS Y CONTROL AVANZADO SONY MDR (V1 / V2 / T2)
    // =========================================================================

    private suspend fun executeTransaction(
        device: SonyBluetoothDevice,
        currentState: HeadsetState,
        block: suspend (SessionTransport) -> Unit
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        val existing = persistentSession
        if (existing != null && existing.isAlive() && existing.address.equals(device.address, ignoreCase = true)) {
            existing.updateStateLocked { currentState }
            val liveRes = runCatching {
                existing.commandMutex.withLock {
                    if (!existing.isAlive()) throw IOException("Socket desconectado")
                    block(existing)
                    existing.snapshotState()
                }
            }
            if (liveRes.isSuccess) {
                val outState = liveRes.getOrThrow().copy(isConnected = true, isBusy = false)
                if (existing.emitLivePush) _liveStateUpdates.tryEmit(outState)
                return@withContext Result.success(outState)
            }
            sessionLifecycleMutex.withLock {
                if (persistentSession === existing) {
                    existing.close("Error en transacción sobre sesión")
                    persistentSession = null
                }
            }
        }

        oneShotMutex.withLock {
            var transport: SessionTransport? = null
            try {
                val conn = connectBestSocket(device)
                    ?: return@withContext Result.failure(Exception("Socket RFCOMM no disponible"))
                val seeded = seedCachedMetadata(device, currentState)
                val t = SessionTransport(
                    device = device,
                    socket = conn.first,
                    connectedUuid = conn.second,
                    initialState = seeded,
                    emitLivePush = false,
                    onClosed = null
                )
                transport = t
                t.startReader(this)
                val updated = t.commandMutex.withLock {
                    if (!verifiedProtocolVersions.containsKey(device.address) && !isProtocolExplicitlyKnown(seeded, conn.second)) {
                        t.sendAndAwaitFrameLocked(
                            payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_PROTOCOL_INFO, 0x00),
                            expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_PROTOCOL_INFO,
                            timeoutMs = 600L
                        )
                    }
                    block(t)
                    t.snapshotState()
                }
                Result.success(updated.copy(isConnected = true, isBusy = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(HexLogEntry.Direction.ERROR, "", "Error en transacción RFCOMM: ${e.localizedMessage}")
                Result.failure(e)
            } finally {
                transport?.close("Fin de transacción one-shot")
            }
        }
    }

    private suspend fun sendWriteWithRetry(
        transport: SessionTransport,
        payload: ByteArray,
        description: String,
        dataType: Byte = SonyProtocolParser.DATA_TYPE_MDR
    ): Boolean {
        var acked = transport.sendAndAwaitAckLocked(payload, description, dataType, timeoutMs = 350L)
        if (!acked && transport.isAlive()) {
            acked = transport.sendAndAwaitAckLocked(payload, "$description reintento", dataType, timeoutMs = 350L)
        }
        return acked
    }

    /**
     * Consulta los parámetros extendidos de Fase 2 según capacidades descubiertas (T1 y T2).
     */
    suspend fun syncExtendedControls(device: SonyBluetoothDevice, currentState: HeadsetState): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            var snap = transport.snapshotState()
            if (snap.protocolVersion >= 2 && snap.table2SupportFunctions.isEmpty() && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_SUPPORT_FUNCTION, 0x00),
                    expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_SUPPORT_FUNCTION,
                    dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2,
                    timeoutMs = 550L
                )
                snap = transport.snapshotState()
            }

            // Consultar capacidades BOOLEAN para GENERAL_SETTING_2 (Multipoint fallback) y GENERAL_SETTING_3 (Touch lock)
            if (snap.protocolVersion >= 2) {
                val gsTypesToCheck = buildList {
                    if (0xD2 in snap.supportFunctions) add(SonyProtocolParser.GS_TYPE_MULTIPOINT)
                    if (0xD3 in snap.supportFunctions) add(SonyProtocolParser.GS_TYPE_TOUCH_PANEL)
                }
                for (gsType in gsTypesToCheck) {
                    if (!transport.isAlive()) break
                    val sub = gsType.toInt() and 0xFF
                    if (sub !in snap.gsBooleanConfirmed) {
                        transport.sendAndAwaitFrameLocked(
                            payload = SonyProtocolParser.buildGsCapabilityGetPayload(gsType),
                            expectedOpcode = SonyProtocolParser.CMD_GS_RET_CAPABILITY,
                            expectedSubtype = sub,
                            timeoutMs = 450L
                        )
                        snap = transport.snapshotState()
                    }
                    if (sub in snap.gsBooleanConfirmed && transport.isAlive()) {
                        transport.sendAndAwaitFrameLocked(
                            payload = SonyProtocolParser.buildGsParamGetPayload(gsType),
                            expectedOpcode = SonyProtocolParser.CMD_GS_RET_PARAM,
                            expectedSubtype = sub,
                            timeoutMs = 450L
                        )
                        snap = transport.snapshotState()
                    }
                }
            }

            val caps = snap.capabilities

            if (caps.supportsEq && transport.isAlive()) {
                val eqType = SonyProtocolParser.eqInquiredTypeFor(snap).toInt() and 0xFF
                transport.sendAndAwaitFrameLocked(
                    payload = SonyProtocolParser.buildEqualizerGetPayload(snap),
                    expectedOpcode = SonyProtocolParser.CMD_EQEBB_RET_PARAM,
                    expectedSubtype = eqType,
                    timeoutMs = 550L
                )
            }

            if (caps.supportsDsee && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = SonyProtocolParser.buildDseeGetPayload(),
                    expectedOpcode = SonyProtocolParser.CMD_AUDIO_RET_PARAM,
                    expectedSubtype = SonyProtocolParser.AUDIO_TYPE_UPSCALING.toInt() and 0xFF,
                    timeoutMs = 450L
                )
            }

            if (caps.supportsConnectionPriority && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = SonyProtocolParser.buildConnectionPriorityGetPayload(caps.connectionPriorityInquiredType),
                    expectedOpcode = SonyProtocolParser.CMD_AUDIO_RET_PARAM,
                    expectedSubtype = caps.connectionPriorityInquiredType,
                    timeoutMs = 450L
                )
            }

            if (caps.supportsAutoPowerOff && transport.isAlive()) {
                val withWear = caps.supportsAutoPowerOffWear
                val expectedSub = if (withWear) {
                    SonyProtocolParser.POWER_TYPE_AUTO_POWER_OFF_WEAR.toInt() and 0xFF
                } else {
                    SonyProtocolParser.POWER_TYPE_AUTO_POWER_OFF.toInt() and 0xFF
                }
                transport.sendAndAwaitFrameLocked(
                    payload = SonyProtocolParser.buildAutoPowerOffGetPayload(withWear),
                    expectedOpcode = SonyProtocolParser.CMD_POWER_RET_PARAM,
                    expectedSubtype = expectedSub,
                    timeoutMs = 450L
                )
            }

            if (caps.supportsPauseWhenRemoved && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = SonyProtocolParser.buildPauseWhenRemovedGetPayload(),
                    expectedOpcode = SonyProtocolParser.CMD_SYSTEM_RET_PARAM,
                    expectedSubtype = SonyProtocolParser.SYSTEM_TYPE_WEAR_PLAYBACK.toInt() and 0xFF,
                    timeoutMs = 450L
                )
            }

            if (caps.supportsSpeakToChat && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = SonyProtocolParser.buildSpeakToChatGetPayload(caps.speakToChatInquiredType),
                    expectedOpcode = SonyProtocolParser.CMD_SYSTEM_RET_PARAM,
                    expectedSubtype = caps.speakToChatInquiredType,
                    timeoutMs = 450L
                )
                if (snap.protocolVersion >= 2 && transport.isAlive()) {
                    transport.sendAndAwaitFrameLocked(
                        payload = SonyProtocolParser.buildSpeakToChatExtGetPayload(caps.speakToChatInquiredType),
                        expectedOpcode = SonyProtocolParser.CMD_SYSTEM_RET_EXT_PARAM,
                        expectedSubtype = caps.speakToChatInquiredType,
                        timeoutMs = 450L
                    )
                }
            }

            if (caps.supportsTouchAssignment && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = SonyProtocolParser.buildTouchAssignmentGetPayload(caps.touchAssignmentInquiredType),
                    expectedOpcode = SonyProtocolParser.CMD_SYSTEM_RET_PARAM,
                    expectedSubtype = caps.touchAssignmentInquiredType,
                    timeoutMs = 450L
                )
            }

            if (caps.multipointViaPeri && transport.isAlive()) {
                val pType = caps.multipointPeriInquiredType
                transport.sendAndAwaitFrameLocked(
                    payload = SonyProtocolParser.buildPeriStatusGetPayload(pType),
                    expectedOpcode = SonyProtocolParser.CMD_PERI_RET_STATUS,
                    expectedSubtype = pType,
                    dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2,
                    timeoutMs = 500L
                )
                if (transport.isAlive()) {
                    transport.sendAndAwaitFrameLocked(
                        payload = SonyProtocolParser.buildPeriParamGetPayload(pType),
                        expectedOpcode = SonyProtocolParser.CMD_PERI_RET_PARAM,
                        expectedSubtype = pType,
                        dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2,
                        timeoutMs = 550L
                    )
                }
            }

            if (caps.supportsVoiceGuidance && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = SonyProtocolParser.buildVoiceGuidanceParamGetPayload(caps.voiceGuidanceInquiredType),
                    expectedOpcode = SonyProtocolParser.CMD_VG_RET_PARAM,
                    expectedSubtype = caps.voiceGuidanceInquiredType,
                    dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2,
                    timeoutMs = 450L
                )
                if (caps.supportsVoiceGuidanceVolume && transport.isAlive()) {
                    transport.sendAndAwaitFrameLocked(
                        payload = SonyProtocolParser.buildVoiceGuidanceVolumeGetPayload(caps.voiceGuidanceVolumeInquiredType),
                        expectedOpcode = SonyProtocolParser.CMD_VG_RET_PARAM,
                        expectedSubtype = caps.voiceGuidanceVolumeInquiredType,
                        dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2,
                        timeoutMs = 450L
                    )
                }
            }
        }
    }

    suspend fun setEqualizerPreset(device: SonyBluetoothDevice, currentState: HeadsetState, presetId: Int): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val snap = transport.snapshotState()
            val payload = SonyProtocolParser.buildEqualizerSetPresetPayload(snap, presetId)
            sendWriteWithRetry(transport, payload, "EQ Preset 0x${"%02X".format(presetId)}")
            delay(50L)
            transport.sendAndAwaitFrameLocked(
                payload = SonyProtocolParser.buildEqualizerGetPayload(snap),
                expectedOpcode = SonyProtocolParser.CMD_EQEBB_RET_PARAM,
                expectedSubtype = SonyProtocolParser.eqInquiredTypeFor(snap).toInt() and 0xFF,
                timeoutMs = 500L
            )
            transport.updateStateLocked {
                it.copy(eqState = it.eqState.copy(isSupported = true, isLoaded = true, presetId = presetId))
            }
        }
    }

    suspend fun setEqualizerCustom(
        device: SonyBluetoothDevice,
        currentState: HeadsetState,
        clearBass: Int,
        bands: List<Int>
    ): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val snap = transport.snapshotState()
            val payload = SonyProtocolParser.buildEqualizerSetCustomPayload(snap, clearBass, bands)
            sendWriteWithRetry(transport, payload, "EQ Custom (${bands.size} bands)")
            transport.updateStateLocked {
                it.copy(
                    eqState = EqState(
                        isSupported = true,
                        isLoaded = true,
                        presetId = EqPreset.CUSTOM.id,
                        clearBass = if (it.capabilities.tenBandEq) 0 else clearBass.coerceIn(-10, 10),
                        bands = bands,
                        isTenBand = it.capabilities.tenBandEq
                    )
                )
            }
        }
    }

    suspend fun setDseeEnabled(device: SonyBluetoothDevice, currentState: HeadsetState, enabled: Boolean): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            sendWriteWithRetry(transport, SonyProtocolParser.buildDseeSetPayload(enabled), "DSEE=$enabled")
            transport.updateStateLocked { it.copy(dseeEnabled = enabled) }
        }
    }

    suspend fun setConnectionPriority(
        device: SonyBluetoothDevice,
        currentState: HeadsetState,
        priority: ConnectionPriority
    ): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val type = transport.snapshotState().capabilities.connectionPriorityInquiredType
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildConnectionPrioritySetPayload(type, priority),
                "Audio Priority=$priority"
            )
            transport.updateStateLocked { it.copy(connectionPriority = priority) }
        }
    }

    suspend fun setAutoPowerOff(
        device: SonyBluetoothDevice,
        currentState: HeadsetState,
        option: AutoPowerOffOption
    ): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val withWear = transport.snapshotState().capabilities.supportsAutoPowerOffWear
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildAutoPowerOffSetPayload(withWear, option),
                "AutoPowerOff=$option"
            )
            transport.updateStateLocked { it.copy(autoPowerOff = option) }
        }
    }

    suspend fun setSpeakToChatEnabled(device: SonyBluetoothDevice, currentState: HeadsetState, enabled: Boolean): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val snap = transport.snapshotState()
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildSpeakToChatSetPayload(snap.capabilities.speakToChatInquiredType, enabled, snap.protocolVersion),
                "SpeakToChat=$enabled"
            )
            transport.updateStateLocked { it.copy(speakToChatEnabled = enabled) }
        }
    }

    suspend fun setSpeakToChatConfig(
        device: SonyBluetoothDevice,
        currentState: HeadsetState,
        sensitivity: SpeakToChatSensitivity,
        returnTime: SpeakToChatReturnTime,
        focusOnVoice: Boolean
    ): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val caps = transport.snapshotState().capabilities
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildSpeakToChatExtSetPayload(
                    caps.speakToChatInquiredType,
                    caps.speakToChatExtMode,
                    sensitivity,
                    returnTime,
                    focusOnVoice
                ),
                "SpeakToChat Config ($sensitivity / $returnTime)"
            )
            transport.updateStateLocked {
                it.copy(
                    speakToChatSensitivity = sensitivity,
                    speakToChatReturnTime = returnTime,
                    speakToChatFocusOnVoice = focusOnVoice
                )
            }
        }
    }

    suspend fun setPauseWhenRemoved(device: SonyBluetoothDevice, currentState: HeadsetState, enabled: Boolean): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            sendWriteWithRetry(transport, SonyProtocolParser.buildPauseWhenRemovedSetPayload(enabled), "PauseWhenRemoved=$enabled")
            transport.updateStateLocked { it.copy(pauseWhenRemoved = enabled) }
        }
    }

    suspend fun setWindNoiseReduction(device: SonyBluetoothDevice, currentState: HeadsetState, enabled: Boolean): Result<HeadsetState> {
        if (!currentState.capabilities.supportsWindNoiseReduction) {
            return Result.failure(UnsupportedOperationException("Wind reduction only supported on 0x15 layout"))
        }
        return executeTransaction(device, currentState) { transport ->
            val snap = transport.snapshotState()
            val payload = SonyProtocolParser.buildSetNcAsmWithWindPayload(
                snap.noiseMode,
                snap.ambientLevel,
                snap.voiceFocus,
                enabled
            )
            sendWriteWithRetry(transport, payload, "WindNoiseReduction=$enabled")
            transport.updateStateLocked { it.copy(windNoiseReduction = enabled) }
        }
    }

    suspend fun setTouchAssignment(
        device: SonyBluetoothDevice,
        currentState: HeadsetState,
        presets: List<TouchPresetOption>
    ): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val caps = transport.snapshotState().capabilities
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildTouchAssignmentSetPayload(caps.touchAssignmentInquiredType, presets),
                "TouchAssign=${presets.joinToString()}"
            )
            transport.updateStateLocked {
                it.copy(
                    touchLeftPreset = presets.getOrNull(0) ?: it.touchLeftPreset,
                    touchRightPreset = presets.getOrNull(1) ?: it.touchRightPreset,
                    touchThirdPreset = presets.getOrNull(2) ?: it.touchThirdPreset
                )
            }
        }
    }

    suspend fun setTouchPanelEnabled(device: SonyBluetoothDevice, currentState: HeadsetState, enabled: Boolean): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildGsBooleanSetPayload(SonyProtocolParser.GS_TYPE_TOUCH_PANEL, enabled),
                "TouchPanel=$enabled"
            )
            transport.updateStateLocked { it.copy(touchPanelEnabled = enabled) }
        }
    }

    suspend fun setMultipointEnabled(device: SonyBluetoothDevice, currentState: HeadsetState, enabled: Boolean): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val caps = transport.snapshotState().capabilities
            if (caps.multipointViaPeri) {
                sendWriteWithRetry(
                    transport,
                    SonyProtocolParser.buildPeriStatusSetPayload(caps.multipointPeriInquiredType, enabled),
                    "PERI Multipoint=$enabled",
                    dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2
                )
            } else {
                sendWriteWithRetry(
                    transport,
                    SonyProtocolParser.buildGsBooleanSetPayload(SonyProtocolParser.GS_TYPE_MULTIPOINT, enabled),
                    "GS Multipoint=$enabled"
                )
            }
            transport.updateStateLocked { it.copy(multipointEnabled = enabled) }
        }
    }

    suspend fun setMultipointDeviceConnection(
        device: SonyBluetoothDevice,
        currentState: HeadsetState,
        targetMac: String,
        connect: Boolean
    ): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val pType = transport.snapshotState().capabilities.multipointPeriInquiredType
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildPeriConnectDisconnectPayload(pType, connect, targetMac),
                "PERI ${if (connect) "Connect" else "Disconnect"} $targetMac",
                dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2
            )
            delay(250L)
            transport.sendAndAwaitFrameLocked(
                payload = SonyProtocolParser.buildPeriParamGetPayload(pType),
                expectedOpcode = SonyProtocolParser.CMD_PERI_RET_PARAM,
                expectedSubtype = pType,
                dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2,
                timeoutMs = 550L
            )
        }
    }

    suspend fun switchMultipointActiveSource(
        device: SonyBluetoothDevice,
        currentState: HeadsetState,
        targetMac: String
    ): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val pType = transport.snapshotState().capabilities.multipointPeriInquiredType
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildPeriSourceSwitchPayload(targetMac),
                "PERI SwitchSource $targetMac",
                dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2
            )
            delay(200L)
            transport.sendAndAwaitFrameLocked(
                payload = SonyProtocolParser.buildPeriParamGetPayload(pType),
                expectedOpcode = SonyProtocolParser.CMD_PERI_RET_PARAM,
                expectedSubtype = pType,
                dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2,
                timeoutMs = 550L
            )
        }
    }

    suspend fun setVoiceGuidance(
        device: SonyBluetoothDevice,
        currentState: HeadsetState,
        enabled: Boolean,
        language: VoiceGuidanceLanguageOption
    ): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val inquired = transport.snapshotState().capabilities.voiceGuidanceInquiredType
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildVoiceGuidanceSetParamPayload(inquired, enabled, language),
                "VoiceGuidance=$enabled ($language)",
                dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2
            )
            transport.updateStateLocked { it.copy(voiceGuidanceEnabled = enabled, voiceGuidanceLanguage = language) }
        }
    }

    suspend fun setVoiceGuidanceVolume(device: SonyBluetoothDevice, currentState: HeadsetState, volume: Int): Result<HeadsetState> {
        return executeTransaction(device, currentState) { transport ->
            val inquired = transport.snapshotState().capabilities.voiceGuidanceVolumeInquiredType
            val clamped = volume.coerceIn(-2, 2)
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildVoiceGuidanceVolumeSetPayload(inquired, clamped, playFeedbackSound = true),
                "VoiceGuidanceVol=$clamped",
                dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2
            )
            transport.updateStateLocked { it.copy(voiceGuidanceVolume = clamped) }
        }
    }

    /**
     * Apagado remoto seguro: estrictamente reservado a Sony MDR V2 (0x24 0x03 0x01).
     */
    suspend fun remotePowerOff(device: SonyBluetoothDevice, currentState: HeadsetState): Result<Unit> {
        if (currentState.protocolVersion < 2 || !currentState.capabilities.supportsRemotePowerOff) {
            return Result.failure(UnsupportedOperationException("Remote Power Off V2 only"))
        }
        val res = executeTransaction(device, currentState) { transport ->
            sendWriteWithRetry(transport, SonyProtocolParser.buildRemotePowerOffV2Payload(), "Power Off V2 (0x24 0x03 0x01)")
            delay(100L)
        }
        closePersistentSession("Apagado remoto completado")
        return res.map { }
    }

    private fun safeDeviceName(device: SonyBluetoothDevice): String {
        return device.name?.takeIf { it.isNotBlank() } ?: device.address
    }

    private fun connectSocket(device: SonyBluetoothDevice, uuid: UUID): RfcommSocketConnection? {
        return try {
            connector.connect(device, uuid)
        } catch (_: Exception) {
            null
        }
    }

    private data class PendingResponse(
        val expectedDataType: Byte,
        val expectedOpcode: Byte,
        val expectedSubtype: Int,
        val deferred: CompletableDeferred<SonyProtocolParser.ParsedFrame>
    )

    /**
     * Sesión RFCOMM dúplex con lector continuo asíncrono, control estricto de ARQ (Stop-and-Wait)
     * y sincronización libre de interbloqueos entre ACK de lectura y comandos de escritura.
     */
    private inner class SessionTransport(
        val device: SonyBluetoothDevice,
        private val socket: RfcommSocketConnection,
        val connectedUuid: UUID,
        initialState: HeadsetState,
        val emitLivePush: Boolean,
        private val onClosed: ((String) -> Unit)?
    ) {
        val address: String = device.address
        val commandMutex = Mutex()
        private val ioWriteMutex = Mutex()
        private val stateLock = Any()
        private val waiterLock = Any()

        private val inStream: InputStream = socket.inputStream
        private val outStream: OutputStream = socket.outputStream

        @Volatile
        private var state: HeadsetState = initialState

        @Volatile
        private var open: Boolean = true

        @Volatile
        private var sequenceNumber: Byte = 0x00

        @Volatile
        var receivedAnyValidFrame: Boolean = false
            private set

        private var lastIncomingDataType: Byte? = null
        private var lastIncomingSeq: Byte? = null
        private var lastIncomingPayload: ByteArray? = null

        private var pendingAck: CompletableDeferred<Byte>? = null
        private var pendingResponse: PendingResponse? = null
        private var readerJob: Job? = null

        fun isAlive(): Boolean = open && socket.isConnected

        fun snapshotState(): HeadsetState = synchronized(stateLock) { state }

        fun updateStateLocked(transform: (HeadsetState) -> HeadsetState): HeadsetState {
            synchronized(stateLock) {
                state = transform(state)
                return state
            }
        }

        fun startReader(scope: CoroutineScope) {
            readerJob = scope.launch(Dispatchers.IO) {
                val readBuffer = ByteArray(1024)
                val streamBuffer = ByteArrayOutputStream()
                try {
                    while (isActive && open) {
                        val count = inStream.read(readBuffer)
                        if (count <= 0) break

                        val chunk = readBuffer.copyOfRange(0, count)
                        logNonSuspend(
                            HexLogEntry.Direction.RX,
                            SonyProtocolParser.bytesToHex(chunk),
                            "Datos recibidos ($count bytes)"
                        )

                        streamBuffer.write(readBuffer, 0, count)
                        val (frames, remainder) = SonyProtocolParser.extractFrames(streamBuffer.toByteArray())
                        streamBuffer.reset()
                        if (remainder.isNotEmpty() && remainder.size <= 8192) {
                            streamBuffer.write(remainder)
                        }

                        for (frame in frames) {
                            handleIncomingFrame(frame)
                        }
                    }
                } catch (_: IOException) {
                    // Cierre normal del socket o desconexión física del audífono
                } catch (_: Exception) {
                } finally {
                    close("Canal RFCOMM finalizado")
                }
            }
        }

        private suspend fun handleIncomingFrame(frame: SonyProtocolParser.ParsedFrame) {
            receivedAnyValidFrame = true
            if (frame.dataType == SonyProtocolParser.DATA_TYPE_ACK) {
                if (frame.sequence == 0.toByte() || frame.sequence == 1.toByte()) {
                    sequenceNumber = frame.sequence
                }
                val waiter = synchronized(waiterLock) { pendingAck }
                waiter?.complete(frame.sequence)
                return
            }

            if (frame.dataType != SonyProtocolParser.DATA_TYPE_MDR &&
                frame.dataType != SonyProtocolParser.DATA_TYPE_MDR_NO2
            ) return

            // Responder ACK inmediatamente (si nuestro ACK previo se perdió, el firmware retransmitirá con el mismo seq)
            val ackSeq = SonyProtocolParser.nextAckSequenceForIncoming(frame.sequence)
            val ackBytes = SonyProtocolParser.buildAckPacket(ackSeq)
            try {
                ioWriteMutex.withLock {
                    outStream.write(ackBytes)
                    outStream.flush()
                }
            } catch (_: IOException) {
                close("Fallo enviando ACK")
                return
            }

            if (frame.payload.isEmpty()) return
            val opcode = frame.payload[0]

            // Filtrado estricto de retransmisión duplicada del mismo canal, payload y secuencia ARQ
            val isDuplicate = synchronized(stateLock) {
                val dup = lastIncomingDataType == frame.dataType &&
                    lastIncomingSeq == frame.sequence &&
                    (lastIncomingPayload?.contentEquals(frame.payload) == true)
                lastIncomingDataType = frame.dataType
                lastIncomingSeq = frame.sequence
                lastIncomingPayload = frame.payload.copyOf()
                dup
            }
            if (isDuplicate) return

            val updated = applyPayloadToState(frame.dataType, frame.payload)
            if (emitLivePush && updated != null) {
                _liveStateUpdates.tryEmit(updated)
            }

            val respWaiter = synchronized(waiterLock) {
                val pr = pendingResponse
                if (pr != null &&
                    !pr.deferred.isCompleted &&
                    pr.expectedDataType == frame.dataType &&
                    pr.expectedOpcode == opcode
                ) {
                    val subMatch = pr.expectedSubtype < 0 ||
                        (frame.payload.size >= 2 && (frame.payload[1].toInt() and 0xFF) == pr.expectedSubtype)
                    if (subMatch) pr else null
                } else {
                    null
                }
            }
            respWaiter?.deferred?.complete(frame)
        }

        private fun applyPayloadToState(dataType: Byte, payload: ByteArray): HeadsetState? {
            val opcode = payload[0]
            var changed = false
            val next = synchronized(stateLock) {
                val prev = state
                val candidate = if (dataType == SonyProtocolParser.DATA_TYPE_MDR_NO2) {
                    SonyProtocolParser.parseIncomingPayload(dataType, payload, prev)
                } else {
                    when (opcode) {
                        SonyProtocolParser.CMD_CONNECT_RET_PROTOCOL_INFO -> {
                            val detectedVer = SonyProtocolParser.parseProtocolInfoPayload(payload)
                            if (detectedVer != null) {
                                verifiedProtocolVersions[address] = detectedVer
                                prev.copy(protocolVersion = detectedVer, lastSyncTime = System.currentTimeMillis())
                            } else {
                                prev
                            }
                        }
                        SonyProtocolParser.CMD_CONNECT_RET_DEVICE_INFO -> {
                            val p = SonyProtocolParser.parseDeviceInfoPayload(payload, prev)
                            if (p.hardwareModelName.isNotBlank()) verifiedHardwareModels[address] = p.hardwareModelName
                            if (p.firmwareVersion.isNotBlank()) verifiedFirmwareVersions[address] = p.firmwareVersion
                            p
                        }
                        SonyProtocolParser.CMD_RET_BATTERY,
                        SonyProtocolParser.CMD_RET_BATTERY_V1,
                        SonyProtocolParser.CMD_NTFY_BATTERY -> {
                            val b = SonyProtocolParser.parseBatteryPayload(payload, prev)
                            logNonSuspend(
                                HexLogEntry.Direction.INFO,
                                "",
                                "Batería parseada -> L: ${b.leftBattery.displayPercent}, R: ${b.rightBattery.displayPercent}, Estuche: ${b.caseBattery.displayPercent}"
                            )
                            b
                        }
                        SonyProtocolParser.CMD_RET_NC_ASM,
                        SonyProtocolParser.CMD_NTFY_NC_ASM -> {
                            val n = SonyProtocolParser.parseNcAsmPayload(payload, prev)
                            logNonSuspend(
                                HexLogEntry.Direction.INFO,
                                "",
                                "Modo de Ruido parseado -> ${n.noiseMode} (Nivel: ${n.ambientLevel})"
                            )
                            n
                        }
                        else -> SonyProtocolParser.parseIncomingPayload(dataType, payload, prev)
                    }
                }
                if (candidate != prev) {
                    state = candidate
                    changed = true
                }
                state
            }
            return if (changed) next else null
        }

        suspend fun sendAndAwaitAckLocked(
            payload: ByteArray,
            description: String,
            dataType: Byte = SonyProtocolParser.DATA_TYPE_MDR,
            timeoutMs: Long = 350L
        ): Boolean {
            if (!isAlive()) return false
            val ackWaiter = CompletableDeferred<Byte>()
            synchronized(waiterLock) {
                pendingAck?.cancel()
                pendingAck = ackWaiter
            }
            return try {
                writePayloadLocked(payload, description, dataType)
                withTimeoutOrNull(timeoutMs) { ackWaiter.await() } != null
            } catch (_: IOException) {
                close("Error de escritura RFCOMM")
                false
            } finally {
                synchronized(waiterLock) {
                    if (pendingAck === ackWaiter) pendingAck = null
                }
            }
        }

        suspend fun sendAndAwaitFrameLocked(
            payload: ByteArray,
            expectedOpcode: Byte,
            expectedSubtype: Int = -1,
            dataType: Byte = SonyProtocolParser.DATA_TYPE_MDR,
            timeoutMs: Long = 800L
        ): SonyProtocolParser.ParsedFrame? {
            if (!isAlive()) return null
            val ackWaiter = CompletableDeferred<Byte>()
            val respWaiter = CompletableDeferred<SonyProtocolParser.ParsedFrame>()
            synchronized(waiterLock) {
                pendingAck?.cancel()
                pendingAck = ackWaiter
                pendingResponse?.deferred?.cancel()
                pendingResponse = PendingResponse(dataType, expectedOpcode, expectedSubtype, respWaiter)
            }

            return try {
                writePayloadLocked(payload, "Comando 0x${"%02X".format(payload[0])}", dataType)
                withTimeoutOrNull(timeoutMs) { respWaiter.await() }
            } catch (_: IOException) {
                close("Error de escritura RFCOMM")
                null
            } finally {
                synchronized(waiterLock) {
                    if (pendingAck === ackWaiter) pendingAck = null
                    if (pendingResponse?.deferred === respWaiter) pendingResponse = null
                }
            }
        }

        private suspend fun writePayloadLocked(
            payload: ByteArray,
            summary: String,
            dataType: Byte = SonyProtocolParser.DATA_TYPE_MDR
        ) {
            val packet: ByteArray
            ioWriteMutex.withLock {
                if (!open) throw IOException("Sesión cerrada")
                val txSeq = sequenceNumber
                packet = SonyProtocolParser.buildPacket(dataType, txSeq, payload)
                sequenceNumber = ((1 - (txSeq.toInt() and 1)) and 0x01).toByte()
                outStream.write(packet)
                outStream.flush()
            }
            log(HexLogEntry.Direction.TX, SonyProtocolParser.bytesToHex(packet), "$summary (${packet.size} bytes)")
        }

        fun close(reason: String) {
            if (!open) return
            open = false
            synchronized(waiterLock) {
                pendingAck?.cancel()
                pendingAck = null
                pendingResponse?.deferred?.cancel()
                pendingResponse = null
            }
            try { inStream.close() } catch (_: Exception) {}
            try { outStream.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
            readerJob?.cancel()
            logNonSuspend(HexLogEntry.Direction.INFO, "", "Socket RFCOMM cerrado ($reason).")
            onClosed?.invoke(address)
        }
    }
}
