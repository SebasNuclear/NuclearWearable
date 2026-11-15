package com.nuclear.soundconnect.bluetooth

import com.sun.jna.Memory
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Cliente de comunicación Bluetooth Classic RFCOMM para audífonos Sony en Windows (Winsock2 AF_BTH).
 * Mantiene paridad completa con `SonyRfcommClient` de `:core`:
 *
 * 1. Sesión RFCOMM persistente con lector asíncrono continuo no bloqueante (`FIONREAD`) y push en vivo.
 * 2. Soporte completo de canal dual Table 1 (`0x0C`) y Table 2 (`0x0E = DATA_TYPE_MDR_NO2`).
 * 3. Protocolo Stop-and-Wait ARQ estricto (0x00 / 0x01) con ACK automático para 0x0C y 0x0E
 *    y deduplicación por `(dataType, seq, payload)` para evitar descartes cruzados entre tablas.
 * 4. Guard de seguridad V1: jamás envía `0x22` (POWER OFF en V1), Table 2 (`0x0E`) ni `0x24 0x03 0x01`
 *    sin confirmación explícita de protocolo V2.
 * 5. Fast-path para `setNoiseMode` sin esperar `CMD_RET_NC_ASM` + reintento/reconexión transparente
 *    y ventana de inmunidad de 5s contra reversiones de UI.
 */
class SonyWindowsRfcommClient {

    private val sessionLifecycleMutex = Mutex()
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var persistentSession: SessionTransport? = null

    private val verifiedProtocolVersions = ConcurrentHashMap<String, Int>()
    private val verifiedHardwareModels = ConcurrentHashMap<String, String>()
    private val verifiedFirmwareVersions = ConcurrentHashMap<String, String>()

    private val _hexLogs = MutableSharedFlow<HexLogEntry>(replay = 50, extraBufferCapacity = 100)
    val hexLogs: SharedFlow<HexLogEntry> = _hexLogs.asSharedFlow()

    private val _liveStateUpdates = MutableSharedFlow<HeadsetState>(extraBufferCapacity = 32)
    val liveStateUpdates: SharedFlow<HeadsetState> = _liveStateUpdates.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 16)
    val connectionEvents: SharedFlow<Pair<String, Boolean>> = _connectionEvents.asSharedFlow()

    // Marca de tiempo de último comando manual para ventana de inmunidad en sondeo pasivo
    @Volatile
    private var lastManualCommandTime: Long = 0L

    fun isImmunityActive(): Boolean {
        return (System.currentTimeMillis() - lastManualCommandTime) < 5000L
    }

    fun recordManualCommand() {
        lastManualCommandTime = System.currentTimeMillis()
    }

    private suspend fun log(direction: HexLogEntry.Direction, hex: String, summary: String) {
        _hexLogs.emit(HexLogEntry(direction, hex, summary))
    }

    private fun logNonSuspend(direction: HexLogEntry.Direction, hex: String, summary: String) {
        _hexLogs.tryEmit(HexLogEntry(direction, hex, summary))
    }

    fun isSessionActive(address: String? = null): Boolean {
        val current = persistentSession ?: return false
        if (!current.isAlive()) return false
        return address.isNullOrBlank() || current.address.equals(address, ignoreCase = true)
    }

    /**
     * Sincroniza preferencias del usuario (modelo forzado, nivel ambiente, etc.) hacia la sesión activa
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
     * Cierre síncrono seguro para ciclos de apagado / desconexión de Windows.
     */
    fun disconnect(reason: String = "Desconexión solicitada") {
        val existing = persistentSession
        persistentSession = null
        existing?.close(reason)
    }

    suspend fun closePersistentSession(reason: String = "Desconexión solicitada") {
        sessionLifecycleMutex.withLock {
            persistentSession?.close(reason)
            persistentSession = null
        }
    }

    /**
     * Abre o reutiliza la sesión RFCOMM persistente sobre Winsock2 hacia un dispositivo Sony.
     */
    suspend fun ensurePersistentSession(
        macAddress: String,
        deviceName: String,
        initialState: HeadsetState
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        sessionLifecycleMutex.withLock {
            val existing = persistentSession
            if (existing != null && existing.isAlive() && existing.address.equals(macAddress, ignoreCase = true)) {
                updateSessionContext(initialState)
                return@withContext Result.success(existing.snapshotState())
            }

            existing?.close("Reemplazando sesión anterior")
            persistentSession = null

            val transport = openSessionTransport(macAddress, deviceName, initialState, emitLivePush = true)
                ?: return@withContext Result.failure(Exception("Socket RFCOMM no disponible para $deviceName"))

            persistentSession = transport
            _connectionEvents.tryEmit(macAddress to true)

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

    private suspend fun getOrOpenPersistentSessionLocked(
        macAddress: String,
        deviceName: String,
        initialState: HeadsetState
    ): SessionTransport? {
        val existing = persistentSession
        if (existing != null && existing.isAlive() && existing.address.equals(macAddress, ignoreCase = true)) {
            updateSessionContext(initialState)
            return existing
        }

        existing?.close("Reemplazando sesión anterior")
        persistentSession = null

        val created = openSessionTransport(macAddress, deviceName, initialState, emitLivePush = true)
            ?: return null

        persistentSession = created
        _connectionEvents.tryEmit(macAddress to true)
        return created
    }

    /**
     * Sincroniza telemetría de batería, códec y modo de control de ruido reutilizando la sesión persistente.
     */
    suspend fun syncTelemetry(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        for (attempt in 1..2) {
            val transport = sessionLifecycleMutex.withLock {
                getOrOpenPersistentSessionLocked(macAddress, deviceName, currentState)
            } ?: return@withContext Result.failure(Exception("Socket RFCOMM no disponible para $deviceName"))

            val macKey = macAddress.uppercase()
            val needExtended = !verifiedHardwareModels.containsKey(macKey) ||
                transport.snapshotState().hardwareModelName.isBlank() ||
                (transport.snapshotState().protocolVersion >= 2 && transport.snapshotState().supportFunctions.isEmpty())

            val res = runCatching {
                performHandshakeAndTelemetry(transport, includeExtendedMetadata = needExtended)
            }
            if (res.isSuccess) {
                return@withContext Result.success(res.getOrThrow().copy(isConnected = true, isBusy = false))
            }

            val err = res.exceptionOrNull()
            if (err is CancellationException) throw err

            log(HexLogEntry.Direction.INFO, "", "Sesión RFCOMM interrumpida en sync (intento $attempt): ${err?.message}")
            sessionLifecycleMutex.withLock {
                if (persistentSession === transport) {
                    transport.close("Error en sync sobre sesión")
                    persistentSession = null
                }
            }
            if (attempt == 2) {
                return@withContext Result.failure(err ?: IOException("Error en sincronización RFCOMM"))
            }
        }
        Result.failure(IOException("No se pudo sincronizar telemetría RFCOMM"))
    }

    /**
     * Envía comando para conmutar modo de ruido (ANC / OFF / AMBIENT) usando fast-path sin espera de CMD_RET_NC_ASM.
     * Si el socket persistente cayó tras reposo, reconecta automáticamente y reintenta en el mismo clic.
     */
    suspend fun setNoiseMode(
        macAddress: String,
        deviceName: String,
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        currentState: HeadsetState
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        recordManualCommand()
        val targetState = currentState.copy(
            noiseMode = mode,
            ambientLevel = ambientLevel,
            voiceFocus = voiceFocus
        )

        var lastError: Throwable? = null
        for (attempt in 1..2) {
            val transport = sessionLifecycleMutex.withLock {
                getOrOpenPersistentSessionLocked(macAddress, deviceName, targetState)
            }
            if (transport == null) {
                delay(40L)
                continue
            }

            val res = runCatching {
                executeSetNoiseMode(
                    transport = transport,
                    mode = mode,
                    ambientLevel = ambientLevel,
                    voiceFocus = voiceFocus,
                    verifyProtocolFirst = !verifiedProtocolVersions.containsKey(macAddress.uppercase()) &&
                        !isProtocolExplicitlyKnown(transport.snapshotState(), transport.connectedUuid),
                    throwOnNoAck = (attempt == 1)
                )
            }
            if (res.isSuccess) {
                return@withContext Result.success(res.getOrThrow().copy(isConnected = true, isBusy = false))
            }

            val err = res.exceptionOrNull()
            if (err is CancellationException) throw err
            lastError = err

            log(HexLogEntry.Direction.INFO, "", "Reintentando SET_NC_ASM en socket fresco tras fallo (intento $attempt)...")
            sessionLifecycleMutex.withLock {
                if (persistentSession === transport) {
                    transport.close("Socket dormido/caído en SET_NC_ASM")
                    persistentSession = null
                }
            }
        }

        // Fallback optimista si el comando pudo haber salido o la reconexión fue rechazada por canal ocupado
        if (lastError != null) {
            Result.failure(lastError)
        } else {
            Result.failure(IOException("Socket RFCOMM no disponible para SET_NC_ASM"))
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

            val macKey = transport.address.uppercase()
            var protoVersion = verifiedProtocolVersions[macKey]
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
                        verifiedProtocolVersions[macKey] = detectedVer
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

            // Stop-and-Wait rápido sin esperar CMD_RET_NC_ASM (1 reintento automático)
            var acked = transport.sendAndAwaitAckLocked(setPayload, description = "SET_NC_ASM ($mode)", timeoutMs = 250L)
            if (!acked && transport.isAlive()) {
                acked = transport.sendAndAwaitAckLocked(setPayload, description = "SET_NC_ASM reintento", timeoutMs = 250L)
            }
            if (!acked && throwOnNoAck) {
                throw IOException("Sin ACK en sesión persistente")
            }

            // Delay acústico crítico para asentamiento del DSP de Sony
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
     * Handshake secuencial estricto V1/V2 + telemetría inicial/refresco.
     * Garantiza que jamás se envíen tramas 0x22 o Table 2 (0x0E) a modelos V1.
     */
    private suspend fun performHandshakeAndTelemetry(
        transport: SessionTransport,
        includeExtendedMetadata: Boolean
    ): HeadsetState {
        return transport.commandMutex.withLock {
            if (!transport.isAlive()) throw IOException("Socket RFCOMM cerrado")

            val macKey = transport.address.uppercase()
            var protoVersion = verifiedProtocolVersions[macKey]
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
                    verifiedProtocolVersions[macKey] = detectedVer
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

            // Capa 1: Nombre real del hardware y soporte funcional V2 (Table 1 & Table 2)
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

            // Batería estrictamente aislada por versión (V1=0x10, V2=0x22)
            val currentSnapshot = transport.snapshotState()
            if (protoVersion == 1) {
                queryBatteryV1(transport, currentSnapshot.deviceType)
            } else {
                queryBatteryV2(transport, currentSnapshot.deviceType)
            }

            // Modo NC / ASM actual (omitir actualización visual de modo si el usuario acaba de presionar botón)
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

            // Versión de firmware y códec Bluetooth activo (LDAC / AAC / SBC / aptX / LC3)
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

    // =========================================================================
    // FASE 2: TRANSACCIONES GENÉRICAS Y CONTROL AVANZADO SONY MDR (V1 / V2 / T2)
    // =========================================================================

    private suspend fun executeTransaction(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        block: suspend (SessionTransport) -> Unit
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        for (attempt in 1..2) {
            val transport = sessionLifecycleMutex.withLock {
                getOrOpenPersistentSessionLocked(macAddress, deviceName, currentState)
            } ?: return@withContext Result.failure(Exception("Socket RFCOMM no disponible para $deviceName"))

            transport.updateStateLocked { currentState }
            val liveRes = runCatching {
                transport.commandMutex.withLock {
                    if (!transport.isAlive()) throw IOException("Socket desconectado")
                    val macKey = macAddress.uppercase()
                    if (!verifiedProtocolVersions.containsKey(macKey) && !isProtocolExplicitlyKnown(currentState, transport.connectedUuid)) {
                        transport.sendAndAwaitFrameLocked(
                            payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_PROTOCOL_INFO, 0x00),
                            expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_PROTOCOL_INFO,
                            timeoutMs = 600L
                        )
                    }
                    block(transport)
                    transport.snapshotState()
                }
            }
            if (liveRes.isSuccess) {
                val outState = liveRes.getOrThrow().copy(isConnected = true, isBusy = false)
                if (transport.emitLivePush) _liveStateUpdates.tryEmit(outState)
                return@withContext Result.success(outState)
            }

            val err = liveRes.exceptionOrNull()
            if (err is CancellationException) throw err

            sessionLifecycleMutex.withLock {
                if (persistentSession === transport) {
                    transport.close("Error en transacción RFCOMM")
                    persistentSession = null
                }
            }
            if (attempt == 2) {
                log(HexLogEntry.Direction.ERROR, "", "Error en transacción RFCOMM: ${err?.message}")
                return@withContext Result.failure(err ?: IOException("Error en transacción RFCOMM"))
            }
        }
        Result.failure(IOException("Transacción RFCOMM fallida"))
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
     * Consulta los parámetros extendidos de Fase 2 según capacidades descubiertas (T1 y T2),
     * respetando el guard V1 (nunca envía Table 2 ni opcodes incompatibles a audífonos V1).
     */
    suspend fun syncExtendedControls(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
            var snap = transport.snapshotState()

            if (snap.protocolVersion >= 2 && snap.supportFunctions.isEmpty() && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_SUPPORT_FUNCTION, 0x00),
                    expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_SUPPORT_FUNCTION,
                    timeoutMs = 600L
                )
                snap = transport.snapshotState()
            }

            if (snap.protocolVersion >= 2 && snap.table2SupportFunctions.isEmpty() && transport.isAlive()) {
                transport.sendAndAwaitFrameLocked(
                    payload = byteArrayOf(SonyProtocolParser.CMD_CONNECT_GET_SUPPORT_FUNCTION, 0x00),
                    expectedOpcode = SonyProtocolParser.CMD_CONNECT_RET_SUPPORT_FUNCTION,
                    dataType = SonyProtocolParser.DATA_TYPE_MDR_NO2,
                    timeoutMs = 550L
                )
                snap = transport.snapshotState()
            }

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

    suspend fun setEqualizerPreset(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        presetId: Int
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        clearBass: Int,
        bands: List<Int>
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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

    suspend fun setDseeEnabled(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        enabled: Boolean
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
            sendWriteWithRetry(transport, SonyProtocolParser.buildDseeSetPayload(enabled), "DSEE=$enabled")
            transport.updateStateLocked { it.copy(dseeEnabled = enabled) }
        }
    }

    suspend fun setConnectionPriority(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        priority: ConnectionPriority
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        option: AutoPowerOffOption
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
            val withWear = transport.snapshotState().capabilities.supportsAutoPowerOffWear
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildAutoPowerOffSetPayload(withWear, option),
                "AutoPowerOff=$option"
            )
            transport.updateStateLocked { it.copy(autoPowerOff = option) }
        }
    }

    suspend fun setSpeakToChatEnabled(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        enabled: Boolean
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        sensitivity: SpeakToChatSensitivity,
        returnTime: SpeakToChatReturnTime,
        focusOnVoice: Boolean
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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

    suspend fun setPauseWhenRemoved(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        enabled: Boolean
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
            sendWriteWithRetry(transport, SonyProtocolParser.buildPauseWhenRemovedSetPayload(enabled), "PauseWhenRemoved=$enabled")
            transport.updateStateLocked { it.copy(pauseWhenRemoved = enabled) }
        }
    }

    suspend fun setWindNoiseReduction(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        enabled: Boolean
    ): Result<HeadsetState> {
        if (!currentState.capabilities.supportsWindNoiseReduction) {
            return Result.failure(UnsupportedOperationException("Wind reduction only supported on 0x15 layout"))
        }
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        presets: List<TouchPresetOption>
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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

    suspend fun setTouchPanelEnabled(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        enabled: Boolean
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
            sendWriteWithRetry(
                transport,
                SonyProtocolParser.buildGsBooleanSetPayload(SonyProtocolParser.GS_TYPE_TOUCH_PANEL, enabled),
                "TouchPanel=$enabled"
            )
            transport.updateStateLocked { it.copy(touchPanelEnabled = enabled) }
        }
    }

    suspend fun setMultipointEnabled(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        enabled: Boolean
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        targetMac: String,
        connect: Boolean
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        targetMac: String
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        enabled: Boolean,
        language: VoiceGuidanceLanguageOption
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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

    suspend fun setVoiceGuidanceVolume(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState,
        volume: Int
    ): Result<HeadsetState> {
        return executeTransaction(macAddress, deviceName, currentState) { transport ->
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
     * Apagado remoto seguro: estrictamente reservado a Sony MDR V2 (`0x24 0x03 0x01`).
     */
    suspend fun remotePowerOff(
        macAddress: String,
        deviceName: String,
        currentState: HeadsetState
    ): Result<Unit> {
        if (currentState.protocolVersion < 2 || !currentState.capabilities.supportsRemotePowerOff) {
            return Result.failure(UnsupportedOperationException("Remote Power Off V2 only"))
        }
        val res = executeTransaction(macAddress, deviceName, currentState) { transport ->
            sendWriteWithRetry(transport, SonyProtocolParser.buildRemotePowerOffV2Payload(), "Power Off V2 (0x24 0x03 0x01)")
            delay(100L)
        }
        closePersistentSession("Apagado remoto completado")
        return res.map { }
    }

    // =========================================================================
    // CONEXIÓN WINSOCK2 AF_BTH Y TRANSPORTE DÚPLEX POR SESIÓN
    // =========================================================================

    private fun seedCachedMetadata(macAddress: String, base: HeadsetState): HeadsetState {
        val macKey = macAddress.uppercase()
        val cachedProto = verifiedProtocolVersions[macKey] ?: inferSafeProtocolVersion(base, null)
        val cachedHw = base.hardwareModelName.ifBlank { verifiedHardwareModels[macKey] ?: "" }
        val cachedFw = base.firmwareVersion.ifBlank { verifiedFirmwareVersions[macKey] ?: "" }
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

    private fun isProtocolExplicitlyKnown(state: HeadsetState, uuid: UUID?): Boolean {
        val raw = "${state.deviceName} ${state.hardwareModelName}"
        return isKnownV1Model(state.modelFamily, raw) ||
            (isKnownV2Model(state.modelFamily, raw) && uuid == WindowsBluetoothNative.SONY_MDR_UUID)
    }

    private fun inferSafeProtocolVersion(state: HeadsetState, connectedUuid: UUID?): Int {
        val raw = "${state.deviceName} ${state.hardwareModelName}"
        if (isKnownV1Model(state.modelFamily, raw)) return 1
        if ((connectedUuid == WindowsBluetoothNative.SONY_FALLBACK_UUID || connectedUuid == WindowsBluetoothNative.SPP_UUID) &&
            !isKnownV2Model(state.modelFamily, raw)
        ) {
            return 1
        }
        return if (isKnownV2Model(state.modelFamily, raw)) 2 else 1
    }

    private fun connectBestSocket(macLong: Long): Pair<Long, UUID>? {
        val winSock = WindowsBluetoothNative.winSock
        for (uuid in listOf(
            WindowsBluetoothNative.SONY_MDR_UUID,
            WindowsBluetoothNative.SONY_FALLBACK_UUID,
            WindowsBluetoothNative.SPP_UUID
        )) {
            val s = connectSocket(winSock, macLong, uuid)
            if (s != -1L) return s to uuid
        }
        return null
    }

    private fun connectSocket(
        winSock: WindowsBluetoothNative.WinSockLibrary,
        macLong: Long,
        uuid: UUID
    ): Long {
        val s = winSock.socket(
            WindowsBluetoothNative.AF_BTH,
            WindowsBluetoothNative.SOCK_STREAM,
            WindowsBluetoothNative.BTHPROTO_RFCOMM
        )
        if (s == -1L) return -1L
        return try {
            val timeoutMem = Memory(4).apply { setInt(0, 1500) }
            winSock.setsockopt(s, WindowsBluetoothNative.SOL_SOCKET, WindowsBluetoothNative.SO_RCVTIMEO, timeoutMem, 4)
            winSock.setsockopt(s, WindowsBluetoothNative.SOL_SOCKET, WindowsBluetoothNative.SO_SNDTIMEO, timeoutMem, 4)
            val sa = WindowsBluetoothNative.SOCKADDR_BTH(macLong, uuid, 0)
            val ret = winSock.connect(s, sa, sa.size())
            if (ret == 0) {
                s
            } else {
                winSock.closesocket(s)
                -1L
            }
        } catch (_: Throwable) {
            try { winSock.closesocket(s) } catch (_: Throwable) {}
            -1L
        }
    }

    private suspend fun openSessionTransport(
        macAddress: String,
        deviceName: String,
        initialState: HeadsetState,
        emitLivePush: Boolean
    ): SessionTransport? {
        val macLong = try {
            WindowsBluetoothNative.macStringToLong(macAddress)
        } catch (e: Throwable) {
            log(HexLogEntry.Direction.ERROR, "", "Dirección MAC inválida: $macAddress")
            return null
        }

        log(HexLogEntry.Direction.INFO, "", "Abriendo canal RFCOMM Winsock2 con $deviceName...")
        val conn = connectBestSocket(macLong) ?: run {
            val err = try { WindowsBluetoothNative.winSock.WSAGetLastError() } catch (_: Throwable) { -1 }
            log(HexLogEntry.Direction.ERROR, "", "No se pudo conectar RFCOMM a $deviceName (WSA=$err)")
            return null
        }

        val seeded = seedCachedMetadata(macAddress, initialState)
        val transport = SessionTransport(
            address = macAddress,
            socket = conn.first,
            connectedUuid = conn.second,
            initialState = seeded,
            emitLivePush = emitLivePush,
            onClosed = { mac ->
                if (persistentSession?.address.equals(mac, ignoreCase = true) && persistentSession?.isAlive() == false) {
                    persistentSession = null
                }
                _connectionEvents.tryEmit(mac to false)
            }
        )
        transport.startReader(clientScope)
        log(HexLogEntry.Direction.INFO, "", "Canal RFCOMM conectado (UUID=${conn.second.toString().take(8)}, Seq=0x00).")
        return transport
    }

    private data class PendingResponse(
        val expectedDataType: Byte,
        val expectedOpcode: Byte,
        val expectedSubtype: Int,
        val deferred: CompletableDeferred<SonyProtocolParser.ParsedFrame>
    )

    private inner class SessionTransport(
        val address: String,
        private val socket: Long,
        val connectedUuid: UUID,
        initialState: HeadsetState,
        val emitLivePush: Boolean,
        private val onClosed: ((String) -> Unit)?
    ) {
        private val winSock = WindowsBluetoothNative.winSock
        private val macKey = address.uppercase()
        val commandMutex = Mutex()
        private val ioWriteMutex = Mutex()
        private val stateLock = Any()
        private val waiterLock = Any()

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

        fun isAlive(): Boolean = open && socket != -1L

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
                val bytesAvailMem = Memory(4)
                try {
                    while (isActive && open) {
                        val ioctlRet = winSock.ioctlsocket(socket, WindowsBluetoothNative.FIONREAD, bytesAvailMem)
                        if (ioctlRet != 0) break

                        val avail = bytesAvailMem.getInt(0)
                        if (avail > 0) {
                            val toRead = minOf(avail, readBuffer.size)
                            val count = winSock.recv(socket, readBuffer, toRead, 0)
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
                        } else {
                            delay(12L)
                        }
                    }
                } catch (_: CancellationException) {
                    // Cancelación normal
                } catch (_: Throwable) {
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

            // Responder ACK inmediatamente para ambos canales Table 1 (0x0C) y Table 2 (0x0E)
            val ackSeq = SonyProtocolParser.nextAckSequenceForIncoming(frame.sequence)
            val ackBytes = SonyProtocolParser.buildAckPacket(ackSeq)
            val ackSent = ioWriteMutex.withLock {
                if (!open) false else winSock.send(socket, ackBytes, ackBytes.size, 0) > 0
            }
            if (!ackSent) {
                close("Fallo enviando ACK")
                return
            }

            if (frame.payload.isEmpty()) return
            val opcode = frame.payload[0]

            // Deduplicación ARQ por (dataType, seq, payload) para no descartar secuencias 0/1 entre 0x0C y 0x0E
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
                                verifiedProtocolVersions[macKey] = detectedVer
                                prev.copy(protocolVersion = detectedVer, lastSyncTime = System.currentTimeMillis())
                            } else {
                                prev
                            }
                        }
                        SonyProtocolParser.CMD_CONNECT_RET_DEVICE_INFO -> {
                            val p = SonyProtocolParser.parseDeviceInfoPayload(payload, prev)
                            if (p.hardwareModelName.isNotBlank()) verifiedHardwareModels[macKey] = p.hardwareModelName
                            if (p.firmwareVersion.isNotBlank()) verifiedFirmwareVersions[macKey] = p.firmwareVersion
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
                val sent = winSock.send(socket, packet, packet.size, 0)
                if (sent <= 0) {
                    val err = winSock.WSAGetLastError()
                    throw IOException("Winsock send() error: $err")
                }
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
            try { winSock.closesocket(socket) } catch (_: Throwable) {}
            readerJob?.cancel()
            logNonSuspend(HexLogEntry.Direction.INFO, "", "Socket RFCOMM cerrado ($reason).")
            onClosed?.invoke(address)
        }
    }
}
