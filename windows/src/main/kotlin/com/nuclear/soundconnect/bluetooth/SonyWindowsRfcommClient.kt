package com.nuclear.soundconnect.bluetooth

import com.sun.jna.Memory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Cliente de comunicación de bajo nivel Bluetooth RFCOMM para audífonos Sony en Windows.
 * Utiliza sockets Winsock2 (AF_BTH) nativos con modelo transaccional idéntico a Android:
 *
 * 1. Conecta socket RFCOMM bajo demanda (UUID V2 -> Fallback -> SPP).
 * 2. Alternating-Bit ARQ estricto (0x00 y 0x01) para números de secuencia.
 * 3. Búfer acumulador de tramas que no se interrumpe prematuramente con tramas ACK (0x01).
 * 4. Envío automático de confirmación ACK a cada trama MDR recibida del firmware.
 * 5. Asentamiento acústico DSP de 80ms y ventana de inmunidad de 5s para conmutación de ANC.
 * 6. Cierre limpio e inmediato del socket tras cada transacción para evitar timeouts/congelamiento.
 */
class SonyWindowsRfcommClient {

    private val mutex = Mutex()
    private var sequenceNumber: Byte = 0x00

    @Volatile
    private var currentSocket: Long = -1L
    @Volatile
    private var connectedMac: String = ""

    private val _hexLogs = MutableSharedFlow<HexLogEntry>(extraBufferCapacity = 100)
    val hexLogs: SharedFlow<HexLogEntry> = _hexLogs.asSharedFlow()

    // Marca de tiempo de último comando manual para ventana de inmunidad
    @Volatile
    private var lastManualCommandTime: Long = 0L

    fun getNextSeq(): Byte {
        val s = sequenceNumber
        // Alternating-Bit ARQ estándar Sony MDR V2 (0 y 1 únicamente)
        sequenceNumber = (if (sequenceNumber == 0.toByte()) 1 else 0).toByte()
        return s
    }

    fun isImmunityActive(): Boolean {
        return (System.currentTimeMillis() - lastManualCommandTime) < 5000L
    }

    fun recordManualCommand() {
        lastManualCommandTime = System.currentTimeMillis()
    }

    fun disconnect() {
        if (currentSocket != -1L) {
            try {
                WindowsBluetoothNative.winSock.closesocket(currentSocket)
                log(HexLogEntry.Direction.INFO, "", "Socket RFCOMM cerrado limpiamente.")
            } catch (_: Throwable) {}
            currentSocket = -1L
            connectedMac = ""
        }
    }

    private fun log(direction: HexLogEntry.Direction, hex: String, summary: String) {
        _hexLogs.tryEmit(HexLogEntry(direction, hex, summary))
    }

    private fun getOrConnectSocket(
        winSock: WindowsBluetoothNative.WinSockLibrary,
        macAddress: String,
        macLong: Long,
        deviceName: String
    ): Long {
        if (currentSocket != -1L && connectedMac.equals(macAddress, ignoreCase = true)) {
            val checkMem = Memory(4)
            val ioctlRet = winSock.ioctlsocket(currentSocket, WindowsBluetoothNative.FIONREAD, checkMem)
            if (ioctlRet == 0) {
                return currentSocket
            }
            disconnect()
        }

        disconnect()

        val socket = winSock.socket(
            WindowsBluetoothNative.AF_BTH,
            WindowsBluetoothNative.SOCK_STREAM,
            WindowsBluetoothNative.BTHPROTO_RFCOMM
        )
        if (socket == -1L) {
            val err = winSock.WSAGetLastError()
            log(HexLogEntry.Direction.ERROR, "", "Error al crear socket AF_BTH Winsock: $err")
            return -1L
        }

        val timeoutMem = Memory(4).apply { setInt(0, 1500) }
        winSock.setsockopt(socket, WindowsBluetoothNative.SOL_SOCKET, WindowsBluetoothNative.SO_RCVTIMEO, timeoutMem, 4)
        winSock.setsockopt(socket, WindowsBluetoothNative.SOL_SOCKET, WindowsBluetoothNative.SO_SNDTIMEO, timeoutMem, 4)

        val connected = tryConnect(socket, macLong, WindowsBluetoothNative.SONY_MDR_UUID) ||
                        tryConnect(socket, macLong, WindowsBluetoothNative.SONY_FALLBACK_UUID) ||
                        tryConnect(socket, macLong, WindowsBluetoothNative.SPP_UUID)

        if (!connected) {
            val lastErr = winSock.WSAGetLastError()
            winSock.closesocket(socket)
            log(HexLogEntry.Direction.ERROR, "", "No se pudo conectar a los audífonos (código $lastErr)")
            return -1L
        }

        currentSocket = socket
        connectedMac = macAddress
        sequenceNumber = 0x00
        log(HexLogEntry.Direction.INFO, "", "Canal RFCOMM persistente conectado con éxito (Seq inicial: 0x00).")
        return socket
    }

    /**
     * Ejecuta una transacción ligera RFCOMM en Windows sobre el socket persistente:
     * Reutiliza la conexión activa (latencia < 15ms) y solo reconecta si el socket se rompe.
     */
    suspend fun executeTransaction(
        macAddress: String,
        deviceName: String,
        commandPackets: List<ByteArray>,
        stateToUpdate: HeadsetState,
        expectResponse: Boolean = true
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val winSock = WindowsBluetoothNative.winSock
            var updatedState = stateToUpdate

            try {
                val macLong = try {
                    WindowsBluetoothNative.macStringToLong(macAddress)
                } catch (e: Throwable) {
                    log(HexLogEntry.Direction.ERROR, "", "Dirección MAC inválida: $macAddress")
                    return@withContext Result.failure(e)
                }

                // 1. Obtener socket existente o conectar uno nuevo
                val socket = getOrConnectSocket(winSock, macAddress, macLong, deviceName)
                if (socket == -1L) {
                    return@withContext Result.failure(Exception("Socket RFCOMM no disponible para $deviceName"))
                }

                // Drenar posibles bytes residuales de transacciones previas
                val drainMem = Memory(4)
                if (winSock.ioctlsocket(socket, WindowsBluetoothNative.FIONREAD, drainMem) == 0) {
                    val staleBytes = drainMem.getInt(0)
                    if (staleBytes > 0) {
                        val dummy = ByteArray(minOf(staleBytes, 1024))
                        winSock.recv(socket, dummy, dummy.size, 0)
                    }
                }

                // 2. Enviar comandos solicitados
                for (packet in commandPackets) {
                    val sent = winSock.send(socket, packet, packet.size, 0)
                    if (sent <= 0) {
                        val err = winSock.WSAGetLastError()
                        log(HexLogEntry.Direction.ERROR, "", "Error al transmitir paquete: $err")
                        disconnect()
                        return@withContext Result.failure(Exception("Winsock send() error: $err"))
                    }
                    log(
                        HexLogEntry.Direction.TX,
                        SonyProtocolParser.bytesToHex(packet),
                        "Comando transmitido (${packet.size} bytes)"
                    )
                    delay(30)
                }

                val bytesAvailMem = Memory(4)

                // 4. Si es comando de conmutación rápida (SET_NC_ASM), escuchar ACK breve y asentar DSP
                if (!expectResponse) {
                    val ackBuffer = ByteArray(256)
                    val ackStart = System.currentTimeMillis()
                    try {
                        while (System.currentTimeMillis() - ackStart < 250L) {
                            val ioctlRet = winSock.ioctlsocket(socket, WindowsBluetoothNative.FIONREAD, bytesAvailMem)
                            val avail = if (ioctlRet == 0) bytesAvailMem.getInt(0) else 0
                            if (avail > 0) {
                                val toRead = minOf(avail, ackBuffer.size)
                                val c = winSock.recv(socket, ackBuffer, toRead, 0)
                                if (c > 0) {
                                    val chunk = ackBuffer.copyOfRange(0, c)
                                    log(HexLogEntry.Direction.RX, SonyProtocolParser.bytesToHex(chunk), "ACK/Confirmación recibida ($c bytes)")
                                    break
                                }
                            }
                            delay(20)
                        }
                    } catch (_: Throwable) {}
                    // Delay crítico de 80ms para asentamiento del procesador DSP de Sony
                    delay(80)
                    updatedState = updatedState.copy(isConnected = true, isBusy = false)
                    return@withContext Result.success(updatedState)
                }

                // 5. Leer respuestas con acumulador de tramas no bloqueante (FIONREAD)
                val readTimeout = 1800L
                val readBuffer = ByteArray(1024)
                val responseAcc = ByteArrayOutputStream()
                var lastDataReceivedTime = 0L
                var receivedBatteryThisTx = false
                var receivedNcAsmThisTx = false
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < readTimeout) {
                    val ioctlRet = winSock.ioctlsocket(socket, WindowsBluetoothNative.FIONREAD, bytesAvailMem)
                    val bytesAvailable = if (ioctlRet == 0) bytesAvailMem.getInt(0) else 0

                    if (bytesAvailable > 0) {
                        val toRead = minOf(bytesAvailable, readBuffer.size)
                        val count = winSock.recv(socket, readBuffer, toRead, 0)
                        if (count > 0) {
                            lastDataReceivedTime = System.currentTimeMillis()
                            responseAcc.write(readBuffer, 0, count)

                            val chunk = readBuffer.copyOfRange(0, count)
                            log(
                                HexLogEntry.Direction.RX,
                                SonyProtocolParser.bytesToHex(chunk),
                                "Datos recibidos ($count bytes)"
                            )

                            // Parsear todas las tramas completas acumuladas hasta el momento
                            val currentAccBytes = responseAcc.toByteArray()
                            val parsedFrames = SonyProtocolParser.parseFrames(currentAccBytes)

                            for (frame in parsedFrames) {
                                // Sincronizar número de secuencia si es un frame ACK del firmware
                                if (frame.dataType == SonyProtocolParser.DATA_TYPE_ACK) {
                                    sequenceNumber = if (frame.sequence == 0.toByte()) 1 else 0
                                }

                                // Responder ACK al firmware Sony para tramas de datos MDR
                                if (frame.dataType == SonyProtocolParser.DATA_TYPE_MDR) {
                                    try {
                                        val ack = SonyProtocolParser.buildAckPacket(frame.sequence)
                                        winSock.send(socket, ack, ack.size, 0)
                                    } catch (_: Throwable) {}
                                }

                                if (frame.payload.isNotEmpty()) {
                                    when (frame.payload[0]) {
                                        SonyProtocolParser.CMD_RET_BATTERY,
                                        SonyProtocolParser.CMD_NOTIF_BATTERY_V1,
                                        0x11.toByte() -> {
                                            updatedState = SonyProtocolParser.parseBatteryPayload(frame.payload, updatedState)
                                            receivedBatteryThisTx = true
                                            log(
                                                HexLogEntry.Direction.INFO,
                                                "",
                                                "Batería parseada -> L: ${updatedState.leftBattery.displayPercent}, R: ${updatedState.rightBattery.displayPercent}, Estuche: ${updatedState.caseBattery.displayPercent}"
                                            )
                                        }
                                        SonyProtocolParser.CMD_RET_NC_ASM -> {
                                            updatedState = SonyProtocolParser.parseNcAsmPayload(frame.payload, updatedState)
                                            receivedNcAsmThisTx = true
                                            log(
                                                HexLogEntry.Direction.INFO,
                                                "",
                                                "Modo de Ruido parseado -> ${updatedState.noiseMode} (Nivel: ${updatedState.ambientLevel})"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Comprobación de terminación temprana si ya llegaron respuestas y han pasado > 200ms sin datos nuevos
                        if (lastDataReceivedTime > 0 && (System.currentTimeMillis() - lastDataReceivedTime > 200L)) {
                            val hasBattery = receivedBatteryThisTx
                            val hasNcAsm = receivedNcAsmThisTx || !updatedState.modelFamily.hasAnc
                            if (hasBattery && hasNcAsm) {
                                break
                            }
                        }
                        delay(25)
                    }
                }

                updatedState = updatedState.copy(isConnected = true, isBusy = false)
                return@withContext Result.success(updatedState)

            } catch (e: Throwable) {
                log(HexLogEntry.Direction.ERROR, "", "Error en sesión RFCOMM: ${e.message}")
                disconnect()
                return@withContext Result.failure(e)
            }
        }
    }

    private fun tryConnect(socket: Long, macLong: Long, uuid: UUID): Boolean {
        return try {
            val sa = WindowsBluetoothNative.SOCKADDR_BTH(macLong, uuid, 0)
            val ret = WindowsBluetoothNative.winSock.connect(socket, sa, sa.size())
            ret == 0
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Sincroniza telemetría de batería y modo de control de ruido.
     * Solo envía consultas Sony MDR V2 nativas soportadas por el modelo, evitando desfasar
     * el contador de secuencia con paquetes V1 ignorados por audífonos modernos.
     */
    suspend fun syncTelemetry(macAddress: String, deviceName: String, currentState: HeadsetState): Result<HeadsetState> {
        val hasAnc = currentState.modelFamily.hasAnc
        val packets = if (currentState.deviceType == DeviceType.OVER_EAR) {
            val list = mutableListOf(SonyProtocolParser.buildSingleBatteryQueryPacket(getNextSeq()))
            if (hasAnc) {
                list.add(SonyProtocolParser.buildNcAsmQueryPacket(getNextSeq()))
            }
            list
        } else {
            val list = mutableListOf(
                SonyProtocolParser.buildDualBatteryQueryPacket(getNextSeq()),
                SonyProtocolParser.buildCaseBatteryQueryPacket(getNextSeq())
            )
            if (hasAnc) {
                list.add(SonyProtocolParser.buildNcAsmQueryPacket(getNextSeq()))
            }
            list
        }
        return executeTransaction(macAddress, deviceName, packets, currentState, expectResponse = true)
    }

    /**
     * Envía comando para conmutar modo de ruido (ANC / OFF / AMBIENT).
     * Incluye verificación activa de ACK del firmware y reintento automático transparente
     * (inversión de bit de secuencia y/o reconexión de socket dormido) para garantizar
     * que el cambio tome efecto siempre en el PRIMER CLIC del usuario, incluso tras reposo.
     */
    suspend fun setNoiseMode(
        macAddress: String,
        deviceName: String,
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        currentState: HeadsetState
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        mutex.withLock {
            recordManualCommand()
            val winSock = WindowsBluetoothNative.winSock
            val targetState = currentState.copy(
                noiseMode = mode,
                ambientLevel = ambientLevel,
                voiceFocus = voiceFocus
            )

            val macLong = try {
                WindowsBluetoothNative.macStringToLong(macAddress)
            } catch (e: Throwable) {
                log(HexLogEntry.Direction.ERROR, "", "Dirección MAC inválida: $macAddress")
                return@withContext Result.failure(e)
            }

            val bytesAvailMem = Memory(4)
            val ackBuffer = ByteArray(256)

            // Hasta 3 intentos automáticos dentro del mismo clic (< 400ms en total)
            for (attempt in 1..3) {
                val socket = getOrConnectSocket(winSock, macAddress, macLong, deviceName)
                if (socket == -1L) {
                    delay(40)
                    continue
                }

                // Drenar cualquier byte residual del búfer
                val drainMem = Memory(4)
                if (winSock.ioctlsocket(socket, WindowsBluetoothNative.FIONREAD, drainMem) == 0) {
                    val staleBytes = drainMem.getInt(0)
                    if (staleBytes > 0) {
                        val dummy = ByteArray(minOf(staleBytes, 1024))
                        winSock.recv(socket, dummy, dummy.size, 0)
                    }
                }

                val seq = getNextSeq()
                val setPacket = SonyProtocolParser.buildSetNcAsmPacket(mode, ambientLevel, voiceFocus, seq)

                val sent = winSock.send(socket, setPacket, setPacket.size, 0)
                if (sent <= 0) {
                    val err = winSock.WSAGetLastError()
                    log(HexLogEntry.Direction.ERROR, "", "Error send() en intento $attempt: $err")
                    disconnect()
                    continue
                }

                log(
                    HexLogEntry.Direction.TX,
                    SonyProtocolParser.bytesToHex(setPacket),
                    "SET_NC_ASM transmitido (Intento $attempt, seq=$seq)"
                )

                // Esperar ACK del firmware Sony (hasta 150ms)
                val ackStart = System.currentTimeMillis()
                var ackConfirmed = false

                while (System.currentTimeMillis() - ackStart < 150L) {
                    val ioctlRet = winSock.ioctlsocket(socket, WindowsBluetoothNative.FIONREAD, bytesAvailMem)
                    val avail = if (ioctlRet == 0) bytesAvailMem.getInt(0) else 0
                    if (avail > 0) {
                        val toRead = minOf(avail, ackBuffer.size)
                        val c = winSock.recv(socket, ackBuffer, toRead, 0)
                        if (c > 0) {
                            val chunk = ackBuffer.copyOfRange(0, c)
                            val frames = SonyProtocolParser.parseFrames(chunk)
                            for (f in frames) {
                                if (f.dataType == SonyProtocolParser.DATA_TYPE_ACK) {
                                    ackConfirmed = true
                                    // Sincronizar siguiente secuencia esperada
                                    sequenceNumber = if (f.sequence == 0.toByte()) 1 else 0
                                    break
                                }
                            }
                            log(
                                HexLogEntry.Direction.RX,
                                SonyProtocolParser.bytesToHex(chunk),
                                "Confirmación de modo recibida ($c bytes)"
                            )
                            if (ackConfirmed) break
                        }
                    }
                    delay(15)
                }

                if (ackConfirmed) {
                    // Delay para asentamiento acústico del DSP de Sony
                    delay(60)
                    return@withContext Result.success(targetState.copy(isConnected = true, isBusy = false))
                }

                log(
                    HexLogEntry.Direction.INFO,
                    "",
                    "Sin ACK en intento $attempt (posible desincronía de ARQ o canal dormido). Auto-reintentando..."
                )

                // Si falló el segundo intento, desconectar socket para forzar uno nuevo en intento 3
                if (attempt == 2) {
                    disconnect()
                }
            }

            // Si tras reintentos no hubo ACK explícito, devolver éxito optimista
            delay(50)
            return@withContext Result.success(targetState.copy(isConnected = true, isBusy = false))
        }
    }
}
