package com.nuclear.soundconnect.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Cliente de comunicación de bajo nivel Bluetooth RFCOMM para audífonos Sony.
 * Soporta conexión directa sin Root ni Shizuku usando únicamente permisos estándar.
 */
class SonyRfcommClient(private val context: Context) {

    companion object {
        // UUID Oficial Sony MDR Protocol V2 (Tándem / Modernos WF-1000XM & WH-1000XM)
        val UUID_SONY_V2: UUID = UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2")
        // UUID Alternativo / Fallback
        val UUID_SONY_FALLBACK: UUID = UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba")

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

        fun isSonyAudioDevice(device: BluetoothDevice): Boolean {
            val name = try { device.name ?: "" } catch (_: SecurityException) { "" }
            if (isSonyDeviceName(name)) return true
            val uuids = try { device.uuids } catch (_: SecurityException) { null }
            if (uuids != null) {
                for (u in uuids) {
                    if (u.uuid == UUID_SONY_V2 || u.uuid == UUID_SONY_FALLBACK) {
                        return true
                    }
                }
            }
            return false
        }
    }

    private val mutex = Mutex()
    private var sequenceNumber: Byte = 0x00

    private val _hexLogs = MutableSharedFlow<HexLogEntry>(replay = 50)
    val hexLogs: SharedFlow<HexLogEntry> = _hexLogs.asSharedFlow()

    private fun nextSeq(): Byte {
        val s = sequenceNumber
        // Alternating-Bit ARQ estándar Sony MDR V2 (0 y 1)
        sequenceNumber = (if (sequenceNumber == 0.toByte()) 1 else 0).toByte()
        return s
    }

    private suspend fun log(direction: HexLogEntry.Direction, hex: String, summary: String) {
        _hexLogs.emit(HexLogEntry(direction, hex, summary))
    }

    /**
     * Busca entre los dispositivos vinculados el dispositivo Sony más prioritario.
     */
    @SuppressLint("MissingPermission")
    fun findTargetDevice(bluetoothAdapter: BluetoothAdapter?, preferredAddress: String? = null): BluetoothDevice? {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return null
        val bonded = try {
            bluetoothAdapter.bondedDevices ?: return null
        } catch (_: SecurityException) {
            return null
        }

        // RESTRICCIÓN ESTRICTA A SONY: Filtrar únicamente dispositivos Sony
        val sonyBonded = bonded.filter { isSonyAudioDevice(it) }

        if (!preferredAddress.isNullOrBlank()) {
            val pref = sonyBonded.firstOrNull { it.address.equals(preferredAddress, ignoreCase = true) }
            if (pref != null) return pref
        }

        // Priorizar WF-1000XM6 y WH-1000XM6
        val xm6 = sonyBonded.firstOrNull {
            val name = try { it.name?.uppercase() ?: "" } catch (_: SecurityException) { "" }
            name.contains("WF-1000XM6") || name.contains("WH-1000XM6")
        }
        if (xm6 != null) return xm6

        // Priorizar cualquier WF o WH de la serie 1000X o LinkBuds
        val sonyFlagship = sonyBonded.firstOrNull {
            val name = try { it.name?.uppercase() ?: "" } catch (_: SecurityException) { "" }
            name.contains("1000XM") || name.contains("LINKBUDS") || name.contains("ULT WEAR")
        }
        if (sonyFlagship != null) return sonyFlagship

        // Cualquier dispositivo con "Sony" en el nombre
        return sonyBonded.firstOrNull()
    }

    /**
     * Determina el tipo de dispositivo (TWS o Diadema Over-Ear) según el nombre comercial.
     */
    @SuppressLint("MissingPermission")
    fun detectDeviceType(device: BluetoothDevice, forcedFamily: SonyModelFamily? = null): DeviceType {
        if (forcedFamily != null) return forcedFamily.defaultDeviceType
        val name = try { device.name?.uppercase() ?: "" } catch (_: SecurityException) { "" }
        val family = SonyModelFamily.detect(name)
        return family.defaultDeviceType
    }

    /**
     * Ejecuta una transacción ligera RFCOMM:
     * Conecta, envía paquetes, lee respuestas hasta el timeout y cierra el socket limpiamente.
     */
    suspend fun executeTransaction(
        device: BluetoothDevice,
        commandPackets: List<ByteArray>,
        stateToUpdate: HeadsetState,
        expectResponse: Boolean = true
    ): Result<HeadsetState> = withContext(Dispatchers.IO) {
        mutex.withLock {
            var socket: BluetoothSocket? = null
            var inStream: InputStream? = null
            var outStream: OutputStream? = null
            var updatedState = stateToUpdate

            try {
                log(HexLogEntry.Direction.INFO, "", "Iniciando conexión RFCOMM con ${device.name ?: device.address}...")

                // Intento 1: Socket Inseguro con UUID Sony V2 principal
                socket = connectSocket(device, UUID_SONY_V2)
                    ?: connectSocket(device, UUID_SONY_FALLBACK)

                if (socket == null || !socket.isConnected) {
                    log(HexLogEntry.Direction.ERROR, "", "No se pudo establecer socket RFCOMM (¿Audífonos ocupados o apagados?)")
                    return@withContext Result.failure(Exception("Socket RFCOMM no disponible"))
                }

                log(HexLogEntry.Direction.INFO, "", "Socket RFCOMM conectado con éxito.")
                inStream = socket.inputStream
                outStream = socket.outputStream

                // Enviar comandos solicitados con un breve espaciado para estabilidad del chip Sony
                for (packet in commandPackets) {
                    outStream.write(packet)
                    outStream.flush()
                    log(
                        HexLogEntry.Direction.TX,
                        SonyProtocolParser.bytesToHex(packet),
                        "Comando transmitido (${packet.size} bytes)"
                    )
                    kotlinx.coroutines.delay(50)
                }

                if (!expectResponse) {
                    // Para SET_NC_ASM: esperar confirmación (ACK) breve del firmware Sony
                    // y un delay de 80ms para que el chip DSP asiente el cambio antes de cerrar el socket.
                    val ackBuffer = ByteArray(256)
                    val ackStart = System.currentTimeMillis()
                    try {
                        while (System.currentTimeMillis() - ackStart < 200L) {
                            if (inStream.available() > 0) {
                                val c = inStream.read(ackBuffer)
                                if (c > 0) {
                                    val chunk = ackBuffer.copyOfRange(0, c)
                                    log(HexLogEntry.Direction.RX, SonyProtocolParser.bytesToHex(chunk), "ACK/Confirmación recibida ($c bytes)")
                                    break
                                }
                            }
                            kotlinx.coroutines.delay(20)
                        }
                    } catch (_: Exception) {}
                    kotlinx.coroutines.delay(80)
                    updatedState = updatedState.copy(isConnected = true, isBusy = false)
                    return@withContext Result.success(updatedState)
                }

                // Leer respuestas con timeout de 2.2 segundos
                val readTimeout = 2200L
                val readBuffer = ByteArray(1024)

                withTimeoutOrNull(readTimeout) {
                    var bytesReadTotal = 0
                    val responseAcc = mutableListOf<Byte>()
                    var lastDataReceivedTime = 0L

                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < readTimeout) {
                        if (inStream.available() > 0) {
                            val count = inStream.read(readBuffer)
                            if (count > 0) {
                                bytesReadTotal += count
                                lastDataReceivedTime = System.currentTimeMillis()
                                for (i in 0 until count) {
                                    responseAcc.add(readBuffer[i])
                                }

                                val chunk = readBuffer.copyOfRange(0, count)
                                log(
                                    HexLogEntry.Direction.RX,
                                    SonyProtocolParser.bytesToHex(chunk),
                                    "Datos recibidos (${count} bytes)"
                                )

                                // Intentar parsear tramas completas
                                val parsedFrames = SonyProtocolParser.parseFrames(responseAcc.toByteArray())
                                for (frame in parsedFrames) {
                                    // Responder con ACK al paquete recibido para confirmar recepción al firmware Sony
                                    if (frame.dataType == SonyProtocolParser.DATA_TYPE_MDR) {
                                        try {
                                            val ack = SonyProtocolParser.buildAckPacket(frame.sequence)
                                            outStream.write(ack)
                                            outStream.flush()
                                        } catch (_: Exception) {}
                                    }

                                    if (frame.payload.isNotEmpty()) {
                                        when (frame.payload[0]) {
                                            SonyProtocolParser.CMD_RET_BATTERY,
                                            SonyProtocolParser.CMD_NOTIF_BATTERY_V1,
                                            0x11.toByte() -> {
                                                updatedState = SonyProtocolParser.parseBatteryPayload(frame.payload, updatedState)
                                                log(
                                                    HexLogEntry.Direction.INFO,
                                                    "",
                                                    "Batería parseada -> L: ${updatedState.leftBattery.displayPercent}, R: ${updatedState.rightBattery.displayPercent}, Estuche: ${updatedState.caseBattery.displayPercent}"
                                                )
                                            }
                                            SonyProtocolParser.CMD_RET_NC_ASM -> {
                                                updatedState = SonyProtocolParser.parseNcAsmPayload(frame.payload, updatedState)
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
                            // Si ya se recibieron respuestas y han pasado > 350ms sin nuevos paquetes entrantes,
                            // podemos finalizar la transacción limpiamente sin agotar el timeout máximo
                            if (lastDataReceivedTime > 0 && (System.currentTimeMillis() - lastDataReceivedTime > 350L)) {
                                val hasBattery = if (updatedState.deviceType == DeviceType.OVER_EAR) {
                                    updatedState.singleBattery.isAvailable
                                } else {
                                    updatedState.leftBattery.isAvailable || updatedState.rightBattery.isAvailable || updatedState.singleBattery.isAvailable
                                }
                                val hasNcAsm = updatedState.lastSyncTime > 0
                                if (hasBattery && hasNcAsm) {
                                    break
                                }
                            }
                            kotlinx.coroutines.delay(40)
                        }
                    }
                }

                updatedState = updatedState.copy(isConnected = true, isBusy = false)
                return@withContext Result.success(updatedState)

            } catch (e: Exception) {
                log(HexLogEntry.Direction.ERROR, "", "Error en sesión RFCOMM: ${e.localizedMessage}")
                return@withContext Result.failure(e)
            } finally {
                try {
                    inStream?.close()
                    outStream?.close()
                    socket?.close()
                    log(HexLogEntry.Direction.INFO, "", "Socket RFCOMM cerrado limpiamente para liberar el canal.")
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Sincroniza telemetría de batería y modo de control de ruido.
     */
    suspend fun syncTelemetry(device: BluetoothDevice, currentState: HeadsetState): Result<HeadsetState> {
        val hasAnc = currentState.modelFamily.hasAnc
        val packets = if (currentState.deviceType == DeviceType.OVER_EAR) {
            val list = mutableListOf(SonyProtocolParser.buildSingleBatteryQueryPacket(nextSeq()))
            if (hasAnc) {
                list.add(SonyProtocolParser.buildNcAsmQueryPacket(nextSeq()))
            }
            list
        } else {
            val list = mutableListOf(
                SonyProtocolParser.buildDualBatteryQueryPacket(nextSeq()),
                SonyProtocolParser.buildCaseBatteryQueryPacket(nextSeq()),
                SonyProtocolParser.buildSingleBatteryQueryPacket(nextSeq()),
                SonyProtocolParser.buildDualLegacyBatteryQueryPacket(nextSeq())
            )
            if (hasAnc) {
                list.add(SonyProtocolParser.buildNcAsmQueryPacket(nextSeq()))
            }
            list
        }
        return executeTransaction(device, packets, currentState)
    }

    /**
     * Envía comando para conmutar modo de ruido (ANC / OFF / AMBIENT).
     * Solo envía el comando SET sin consultar inmediatamente para evitar que el estado
     * transitorio del DSP de los audífonos Sony devuelva OFF antes de asentarse.
     */
    suspend fun setNoiseMode(
        device: BluetoothDevice,
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        currentState: HeadsetState
    ): Result<HeadsetState> {
        val setPacket = SonyProtocolParser.buildSetNcAsmPacket(mode, ambientLevel, voiceFocus, nextSeq())
        val targetState = currentState.copy(
            noiseMode = mode,
            ambientLevel = ambientLevel,
            voiceFocus = voiceFocus
        )
        val result = executeTransaction(device, listOf(setPacket), targetState, expectResponse = false)
        return if (result.isSuccess) {
            Result.success(result.getOrThrow().copy(
                noiseMode = mode,
                ambientLevel = ambientLevel,
                voiceFocus = voiceFocus
            ))
        } else {
            result
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectSocket(device: BluetoothDevice, uuid: UUID): BluetoothSocket? {
        return try {
            val s = device.createInsecureRfcommSocketToServiceRecord(uuid)
            s.connect()
            s
        } catch (e1: Exception) {
            try {
                Thread.sleep(120)
                val s2 = device.createRfcommSocketToServiceRecord(uuid)
                s2.connect()
                s2
            } catch (e2: Exception) {
                null
            }
        }
    }
}
