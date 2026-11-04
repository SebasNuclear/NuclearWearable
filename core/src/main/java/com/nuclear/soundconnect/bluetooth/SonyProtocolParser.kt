package com.nuclear.soundconnect.bluetooth

import java.io.ByteArrayOutputStream

/**
 * Parser y serializador de paquetes de bajo nivel para el Protocolo Sony MDR V2 (Tandem).
 *
 * Estructura de trama:
 * START: 0x3E, DATATYPE: 0x0C, SEQ, LENGTH: 4B BE, PAYLOAD, CHECKSUM: 1B, END: 0x3C
 * Con byte-escaping estandar Sony (0x3D):
 * 0x3E a 0x3D 0x5E
 * 0x3D a 0x3D 0x5D
 * 0x3C a 0x3D 0x5C
 */
object SonyProtocolParser {

    const val START_MARKER: Byte = 0x3E
    const val END_MARKER: Byte = 0x3C
    const val ESCAPE_BYTE: Byte = 0x3D

    const val DATA_TYPE_MDR: Byte = 0x0C
    const val DATA_TYPE_ACK: Byte = 0x01

    // Comandos de Batería Sony V2
    const val CMD_GET_BATTERY: Byte = 0x22
    const val CMD_RET_BATTERY: Byte = 0x23
    const val CMD_NOTIF_BATTERY_V1: Byte = 0x13
    const val CMD_GET_BATTERY_V1: Byte = 0x10

    // Comandos de Control de Ruido (NC / ASM) Sony V2
    const val CMD_GET_NC_ASM: Byte = 0x66
    const val CMD_RET_NC_ASM: Byte = 0x67
    const val CMD_SET_NC_ASM: Byte = 0x68

    const val CATEGORY_NC_ASM: Byte = 0x17

    /**
     * Construye un paquete Sony MDR V2 completo listo para enviar por el socket RFCOMM.
     */
    fun buildPacket(dataType: Byte, seq: Byte, payload: ByteArray, use4ByteLength: Boolean = true): ByteArray {
        val rawBuffer = ByteArrayOutputStream()
        rawBuffer.write(dataType.toInt())
        rawBuffer.write(seq.toInt())

        val len = payload.size
        if (use4ByteLength) {
            rawBuffer.write((len shr 24) and 0xFF)
            rawBuffer.write((len shr 16) and 0xFF)
            rawBuffer.write((len shr 8) and 0xFF)
            rawBuffer.write(len and 0xFF)
        } else {
            rawBuffer.write((len shr 8) and 0xFF)
            rawBuffer.write(len and 0xFF)
        }

        rawBuffer.write(payload)

        // Checksum: Suma simple de todos los bytes del cuerpo sin escapar mod 256
        val rawBytes = rawBuffer.toByteArray()
        var checksum = 0
        for (b in rawBytes) {
            checksum = (checksum + (b.toInt() and 0xFF)) and 0xFF
        }

        // Aplicar Byte-Escaping a (rawBytes + checksum)
        val escapedStream = ByteArrayOutputStream()
        escapedStream.write(START_MARKER.toInt())

        for (b in rawBytes) {
            escapeAndWrite(b, escapedStream)
        }
        escapeAndWrite(checksum.toByte(), escapedStream)

        escapedStream.write(END_MARKER.toInt())
        return escapedStream.toByteArray()
    }

    private fun escapeAndWrite(b: Byte, out: ByteArrayOutputStream) {
        when (b) {
            START_MARKER -> {
                out.write(ESCAPE_BYTE.toInt())
                out.write(0x5E)
            }
            ESCAPE_BYTE -> {
                out.write(ESCAPE_BYTE.toInt())
                out.write(0x5D)
            }
            END_MARKER -> {
                out.write(ESCAPE_BYTE.toInt())
                out.write(0x5C)
            }
            else -> out.write(b.toInt())
        }
    }

    /**
     * Desescapa los bytes contenidos entre START_MARKER y END_MARKER.
     */
    fun unescape(data: ByteArray, startIdx: Int, endIdx: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var i = startIdx
        while (i < endIdx) {
            val b = data[i]
            if (b == ESCAPE_BYTE && i + 1 < endIdx) {
                when (data[i + 1]) {
                    0x5E.toByte() -> {
                        out.write(START_MARKER.toInt())
                        i += 2
                    }
                    0x5D.toByte() -> {
                        out.write(ESCAPE_BYTE.toInt())
                        i += 2
                    }
                    0x5C.toByte() -> {
                        out.write(END_MARKER.toInt())
                        i += 2
                    }
                    else -> {
                        out.write(b.toInt())
                        i++
                    }
                }
            } else {
                out.write(b.toInt())
                i++
            }
        }
        return out.toByteArray()
    }

    // Subtipos de Batería Sony MDR V2 (BatteryInquiredType)
    const val BATTERY_TYPE_SINGLE: Byte = 0x00
    const val BATTERY_TYPE_DUAL_LEGACY: Byte = 0x01
    const val BATTERY_TYPE_CASE_LEGACY: Byte = 0x02
    const val BATTERY_TYPE_DUAL_V2: Byte = 0x09
    const val BATTERY_TYPE_CASE_V2: Byte = 0x0A

    /**
     * Comandos de consulta de batería específicos por componente en Sony MDR V2.
     */
    fun buildDualBatteryQueryPacket(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_BATTERY, BATTERY_TYPE_DUAL_V2))

    fun buildDualLegacyBatteryQueryPacket(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_BATTERY, BATTERY_TYPE_DUAL_LEGACY))

    fun buildCaseBatteryQueryPacket(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_BATTERY, BATTERY_TYPE_CASE_V2))

    fun buildCaseLegacyBatteryQueryPacket(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_BATTERY, BATTERY_TYPE_CASE_LEGACY))

    fun buildSingleBatteryQueryPacket(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_BATTERY, BATTERY_TYPE_SINGLE))

    fun buildBatteryQueryPacket(seq: Byte): ByteArray =
        buildDualBatteryQueryPacket(seq)

    /**
     * Construye un paquete de confirmación (ACK) para responder a tramas entrantes de Sony V2.
     * En el protocolo Sony MDR V2, el paquete ACK tiene DATA_TYPE = 0x01 y el mismo número de secuencia recibido.
     */
    fun buildAckPacket(seq: Byte): ByteArray {
        return buildPacket(DATA_TYPE_ACK, seq, byteArrayOf())
    }

    /**
     * Genera el comando para consultar el estado actual de NC / ASM.
     */
    fun buildNcAsmQueryPacket(seq: Byte): ByteArray {
        return buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_NC_ASM, CATEGORY_NC_ASM))
    }

    /**
     * Genera el comando para establecer el modo de control de ruido.
     * @param mode ANC, OFF o AMBIENT
     * @param ambientLevel Nivel de sonido ambiente de 1 a 20 (Sony estándar)
     * @param voiceFocus Activar enfoque en voz humana
     */
    fun buildSetNcAsmPacket(
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        seq: Byte
    ): ByteArray {
        val clampedLevel = ambientLevel.coerceIn(1, 20).toByte()
        val voiceFocusByte: Byte = if (voiceFocus) 0x01 else 0x00

        // Estructura Sony MDR V2 exacta para SET_NC_ASM (0x68, Categoría 0x17):
        // [CMD (0x68), CATEGORY (0x17), COMMIT (0x01), ENABLED (0x01/0x00), AMBIENT (0x01/0x00), VOICE_FOCUS (0x00/0x01), LEVEL (1..20)]
        // Trama exacta de 7 bytes verificada en open1000x y sony-headphones-linux para WF/WH-1000X:
        // - Byte 5 es Voice Focus: DEBE ser 0x00 (apagado) o 0x01 (encendido). NUNCA enviar 0x02,
        //   ya que 0x02 activa Quick Attention (Atención Rápida) y atenúa/reduce el volumen de la música tanto en ANC como en Ambiente.
        // - Byte 6 es el nivel graduado (1..20). Al eliminar el byte espurio 0x02, clampedLevel queda exactamente en Byte 6,
        //   permitiendo que el control deslizante regule físicamente el sonido ambiental en los audífonos.
        val payload = when (mode) {
            NoiseControlMode.ANC -> {
                // Cancelación activa: enabled=0x01, ambient=0x00, voiceFocus=0x00, level=0x00
                byteArrayOf(CMD_SET_NC_ASM, CATEGORY_NC_ASM, 0x01, 0x01, 0x00, 0x00, 0x00)
            }
            NoiseControlMode.OFF -> {
                // Desactivado: enabled=0x00, ambient=0x00, voiceFocus=0x00, level=0x00
                byteArrayOf(CMD_SET_NC_ASM, CATEGORY_NC_ASM, 0x01, 0x00, 0x00, 0x00, 0x00)
            }
            NoiseControlMode.AMBIENT -> {
                // Modo Ambiente: enabled=0x01, ambient=0x01, voiceFocus=voiceFocusByte (0x00 normal / 0x01 focus), level=clampedLevel (1..20)
                byteArrayOf(CMD_SET_NC_ASM, CATEGORY_NC_ASM, 0x01, 0x01, 0x01, voiceFocusByte, clampedLevel)
            }
        }
        return buildPacket(DATA_TYPE_MDR, seq, payload)
    }

    /**
     * Extrae y parsea los marcos de datos contenidos en un flujo recibido.
     * Devuelve una lista de payloads sin cabecera de trama ya validados por checksum.
     */
    fun parseFrames(buffer: ByteArray): List<ParsedFrame> {
        val frames = mutableListOf<ParsedFrame>()
        var start = -1

        for (i in buffer.indices) {
            if (buffer[i] == START_MARKER) {
                start = i
            } else if (buffer[i] == END_MARKER && start != -1 && i > start) {
                val unescaped = unescape(buffer, start + 1, i)
                if (unescaped.size >= 4) {
                    val receivedChecksum = unescaped.last().toInt() and 0xFF
                    var calcChecksum = 0
                    for (k in 0 until unescaped.size - 1) {
                        calcChecksum = (calcChecksum + (unescaped[k].toInt() and 0xFF)) and 0xFF
                    }

                    if (calcChecksum == receivedChecksum) {
                        val dataType = unescaped[0]
                        val seq = unescaped[1]

                        // Identificar si la longitud es de 4 o 2 bytes
                        val payload: ByteArray
                        if (unescaped.size >= 7 && unescaped[2] == 0.toByte() && unescaped[3] == 0.toByte()) {
                            // Longitud 4 bytes Big Endian
                            val len = ((unescaped[4].toInt() and 0xFF) shl 8) or (unescaped[5].toInt() and 0xFF)
                            val endPayload = (6 + len).coerceAtMost(unescaped.size - 1)
                            payload = unescaped.copyOfRange(6, endPayload)
                        } else if (unescaped.size >= 5) {
                            // Longitud 2 bytes Big Endian
                            val len = ((unescaped[2].toInt() and 0xFF) shl 8) or (unescaped[3].toInt() and 0xFF)
                            val endPayload = (4 + len).coerceAtMost(unescaped.size - 1)
                            payload = unescaped.copyOfRange(4, endPayload)
                        } else {
                            payload = unescaped.copyOfRange(2, unescaped.size - 1)
                        }

                        frames.add(ParsedFrame(dataType, seq, payload))
                    }
                }
                start = -1
            }
        }
        return frames
    }

    /**
     * Parsea una respuesta de batería Sony y actualiza el estado correspondiente.
     */
    fun parseBatteryPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 4) return currentState
        var updated = currentState
        val cmd = payload[0]

        if (cmd == CMD_RET_BATTERY || cmd == 0x25.toByte()) {
            val kind = payload[1].toInt() and 0xFF

            when (kind) {
                0x09 -> { // DUAL BATTERY V2: [0x23, 0x09, L_level, L_chg, R_level, R_chg]
                    if (payload.size >= 6) {
                        val leftRaw = payload[2].toInt() and 0xFF
                        val leftChg = (payload[3].toInt() and 0xFF) == 1
                        val leftLevel = if (leftRaw in 1..100) leftRaw else -1

                        val rightRaw = payload[4].toInt() and 0xFF
                        val rightChg = (payload[5].toInt() and 0xFF) == 1
                        val rightLevel = if (rightRaw in 1..100) rightRaw else -1

                        val single = when {
                            leftLevel > 0 -> BatteryInfo(leftLevel, leftChg)
                            rightLevel > 0 -> BatteryInfo(rightLevel, rightChg)
                            else -> currentState.singleBattery
                        }

                        updated = updated.copy(
                            leftBattery = BatteryInfo(leftLevel, leftChg),
                            rightBattery = BatteryInfo(rightLevel, rightChg),
                            singleBattery = single,
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }
                }
                0x01 -> { // RIGHT BUD (4B) or DUAL LEGACY (6B)
                    if (payload.size >= 6) {
                        // Dual legacy con 6 bytes: [0x23, 0x01, L_level, L_chg, R_level, R_chg]
                        val leftRaw = payload[2].toInt() and 0xFF
                        val leftChg = (payload[3].toInt() and 0xFF) == 1
                        val leftLevel = if (leftRaw in 1..100) leftRaw else -1

                        val rightRaw = payload[4].toInt() and 0xFF
                        val rightChg = (payload[5].toInt() and 0xFF) == 1
                        val rightLevel = if (rightRaw in 1..100) rightRaw else -1

                        val single = when {
                            leftLevel > 0 -> BatteryInfo(leftLevel, leftChg)
                            rightLevel > 0 -> BatteryInfo(rightLevel, rightChg)
                            else -> currentState.singleBattery
                        }

                        updated = updated.copy(
                            leftBattery = BatteryInfo(leftLevel, leftChg),
                            rightBattery = BatteryInfo(rightLevel, rightChg),
                            singleBattery = single,
                            lastSyncTime = System.currentTimeMillis()
                        )
                    } else if (payload.size >= 4) {
                        // Audífono Derecho (R) individual: [0x23, 0x01, R_level, R_chg]
                        val rightRaw = payload[2].toInt() and 0xFF
                        val rightChg = (payload[3].toInt() and 0xFF) == 1
                        val rightLevel = if (rightRaw in 1..100) rightRaw else -1

                        updated = updated.copy(
                            rightBattery = BatteryInfo(rightLevel, rightChg),
                            singleBattery = if (rightLevel > 0) BatteryInfo(rightLevel, rightChg) else currentState.singleBattery,
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }
                }
                0x0A, 0x02 -> { // CASE / CRADLE BATTERY: [0x23, 0x0A/0x02, Case_level, Case_chg]
                    val caseRaw = payload[2].toInt() and 0xFF
                    val caseChg = (payload[3].toInt() and 0xFF) == 1
                    val caseLevel = if (caseRaw in 1..100) caseRaw else -1

                    updated = updated.copy(
                        caseBattery = BatteryInfo(caseLevel, caseChg),
                        lastSyncTime = System.currentTimeMillis()
                    )
                }
                0x00 -> { // SINGLE / LEFT BUD: [0x23, 0x00, Single_level, Single_chg]
                    val singleRaw = payload[2].toInt() and 0xFF
                    val singleChg = (payload[3].toInt() and 0xFF) == 1
                    val singleLevel = if (singleRaw in 1..100) singleRaw else -1

                    // Si es Diadema, asigna directamente a singleBattery
                    if (updated.deviceType == DeviceType.OVER_EAR) {
                        updated = updated.copy(
                            singleBattery = BatteryInfo(singleLevel, singleChg),
                            lastSyncTime = System.currentTimeMillis()
                        )
                    } else {
                        // En TWS: 0x00 es el audífono izquierdo (L)
                        updated = updated.copy(
                            leftBattery = BatteryInfo(singleLevel, singleChg),
                            singleBattery = if (singleLevel > 0) BatteryInfo(singleLevel, singleChg) else currentState.singleBattery,
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }
                }
                else -> {
                    // Fallback para tramas compuestas multidestino
                    var idx = 1
                    while (idx + 1 < payload.size) {
                        val target = payload[idx].toInt() and 0xFF
                        val rawLevel = payload[idx + 1].toInt() and 0xFF
                        val isChg = if (idx + 2 < payload.size) (payload[idx + 2].toInt() and 0xFF) == 1 else false
                        val level = if (rawLevel in 1..100) rawLevel else -1
                        when (target) {
                            0x00 -> updated = updated.copy(leftBattery = BatteryInfo(level, isChg))
                            0x01 -> updated = updated.copy(rightBattery = BatteryInfo(level, isChg))
                            0x02 -> updated = updated.copy(caseBattery = BatteryInfo(level, isChg))
                        }
                        idx += 3
                    }
                }
            }
        } else if (cmd == CMD_NOTIF_BATTERY_V1 || cmd == 0x11.toByte()) {
            if (payload.size >= 2) {
                val rawLevel = payload[1].toInt() and 0xFF
                val level = if (rawLevel in 1..100) rawLevel else -1
                val isCharging = if (payload.size >= 3) (payload[2].toInt() and 0xFF) != 0 else false
                updated = updated.copy(
                    singleBattery = BatteryInfo(level, isCharging),
                    deviceType = DeviceType.OVER_EAR,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
        }

        return updated
    }

    /**
     * Parsea la respuesta de estado de NC / ASM.
     * Soporta tramas estándar de 7 bytes [0x67, 0x17, 0x01, enabled, ambient, voiceFocus, level]
     * y tramas con byte reservado de 8 bytes [0x67, 0x17, 0x01, enabled, ambient, reserved, voiceFocus, level].
     */
    fun parseNcAsmPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 4 || payload[0] != CMD_RET_NC_ASM) return currentState

        val enabledByte = payload[3].toInt() and 0xFF
        val ambientByte = if (payload.size > 4) payload[4].toInt() and 0xFF else 0

        // Extracción robusta de nivel y enfoque de voz (compatible con tramas de 7 y 8 bytes)
        val (parsedVoiceFocus, parsedLevel) = when {
            // Trama de 8 bytes: [..., enabled, ambient, reserved, voiceFocus, level]
            payload.size >= 8 && (payload[7].toInt() and 0xFF) in 1..20 -> {
                val vf = (payload[6].toInt() and 0xFF) == 1
                val lvl = payload[7].toInt() and 0xFF
                Pair(vf, lvl)
            }
            // Trama estándar de 7 bytes: [..., enabled, ambient, voiceFocus, level]
            payload.size >= 7 && (payload[6].toInt() and 0xFF) in 1..20 -> {
                val vf = (payload[5].toInt() and 0xFF) == 1
                val lvl = payload[6].toInt() and 0xFF
                Pair(vf, lvl)
            }
            // Fallback cuando el nivel es 0 (modo ANC u OFF)
            payload.size >= 6 -> {
                val vf = (payload[5].toInt() and 0xFF) == 1
                Pair(vf, currentState.ambientLevel)
            }
            else -> Pair(currentState.voiceFocus, currentState.ambientLevel)
        }

        // Mapeo seguro de modos: evita interpretar erróneamente respuestas de dialectos donde ncSel=0 y asmSel=1
        val mode = when {
            enabledByte == 0x01 && ambientByte == 0x01 -> NoiseControlMode.AMBIENT
            enabledByte == 0x00 && ambientByte == 0x01 -> NoiseControlMode.AMBIENT
            enabledByte == 0x01 && ambientByte == 0x00 -> NoiseControlMode.ANC
            enabledByte == 0x00 && ambientByte == 0x00 -> NoiseControlMode.OFF
            else -> currentState.noiseMode
        }

        return currentState.copy(
            noiseMode = mode,
            ambientLevel = parsedLevel,
            voiceFocus = parsedVoiceFocus,
            lastSyncTime = System.currentTimeMillis()
        )
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X ", b))
        }
        return sb.toString().trim()
    }

    data class ParsedFrame(
        val dataType: Byte,
        val sequence: Byte,
        val payload: ByteArray
    )
}
