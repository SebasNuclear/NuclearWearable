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
    const val DATA_TYPE_MDR_NO2: Byte = 0x0E
    const val DATA_TYPE_ACK: Byte = 0x01

    // Comandos de Handshake y Descubrimiento Sony MDR (V1 / V2)
    const val CMD_CONNECT_GET_PROTOCOL_INFO: Byte = 0x00
    const val CMD_CONNECT_RET_PROTOCOL_INFO: Byte = 0x01
    const val CMD_CONNECT_GET_DEVICE_INFO: Byte = 0x04
    const val CMD_CONNECT_RET_DEVICE_INFO: Byte = 0x05
    const val CMD_CONNECT_GET_SUPPORT_FUNCTION: Byte = 0x06
    const val CMD_CONNECT_RET_SUPPORT_FUNCTION: Byte = 0x07

    const val DEVICE_INFO_MODEL_NAME: Byte = 0x01
    const val DEVICE_INFO_FW_VERSION: Byte = 0x02
    const val DEVICE_INFO_SERIES_AND_COLOR: Byte = 0x03

    // Comandos Comunes / Códec de Audio
    const val CMD_COMMON_GET_STATUS_V2: Byte = 0x12
    const val CMD_COMMON_RET_STATUS_V2: Byte = 0x13
    const val CMD_COMMON_NTFY_STATUS_V2: Byte = 0x15
    const val COMMON_TYPE_AUDIO_CODEC: Byte = 0x02
    const val CMD_AUDIO_CODEC_GET_V1: Byte = 0x18
    const val CMD_AUDIO_CODEC_RET_V1: Byte = 0x19
    const val CMD_AUDIO_CODEC_NTFY_V1: Byte = 0x1B

    // Comandos de Batería Sony V2 (0x22) y V1 (0x10 — NUNCA usar 0x22 en V1 porque significa POWER OFF)
    const val CMD_GET_BATTERY: Byte = 0x22
    const val CMD_RET_BATTERY: Byte = 0x23
    const val CMD_NTFY_BATTERY: Byte = 0x25
    const val CMD_GET_BATTERY_V1: Byte = 0x10
    const val CMD_RET_BATTERY_V1: Byte = 0x11
    const val CMD_NOTIF_BATTERY_V1: Byte = 0x13

    // Comandos de Control de Ruido (NC / ASM) Sony V1 & V2
    const val CMD_GET_NC_ASM: Byte = 0x66
    const val CMD_RET_NC_ASM: Byte = 0x67
    const val CMD_SET_NC_ASM: Byte = 0x68
    const val CMD_NTFY_NC_ASM: Byte = 0x69

    const val CATEGORY_NC_ASM: Byte = 0x17
    const val CATEGORY_NC_ASM_V1: Byte = 0x02
    const val CATEGORY_NC_ASM_WF_XM4: Byte = 0x15
    const val CATEGORY_NC_ASM_XM6: Byte = 0x19

    // Power & Auto Power Off (Table 1 / 0x0C)
    const val CMD_POWER_SET_STATUS: Byte = 0x24
    const val CMD_POWER_GET_PARAM: Byte = 0x26
    const val CMD_POWER_RET_PARAM: Byte = 0x27
    const val CMD_POWER_SET_PARAM: Byte = 0x28
    const val CMD_POWER_NTFY_PARAM: Byte = 0x29
    const val POWER_TYPE_POWER_OFF: Byte = 0x03
    const val POWER_TYPE_AUTO_POWER_OFF: Byte = 0x04
    const val POWER_TYPE_AUTO_POWER_OFF_WEAR: Byte = 0x05
    const val POWER_OFF_USER: Byte = 0x01

    // Equalizer (Table 1 / 0x0C)
    const val CMD_EQEBB_GET_STATUS: Byte = 0x52
    const val CMD_EQEBB_RET_STATUS: Byte = 0x53
    const val CMD_EQEBB_NTFY_STATUS: Byte = 0x55
    const val CMD_EQEBB_GET_PARAM: Byte = 0x56
    const val CMD_EQEBB_RET_PARAM: Byte = 0x57
    const val CMD_EQEBB_SET_PARAM: Byte = 0x58
    const val CMD_EQEBB_NTFY_PARAM: Byte = 0x59
    const val EQ_TYPE_PRESET_V2: Byte = 0x00
    const val EQ_TYPE_PRESET_V1: Byte = 0x01
    const val EQ_TYPE_PRESET_XM6_10BAND: Byte = 0x04

    // General Settings (Table 1 / 0x0C)
    const val CMD_GS_GET_CAPABILITY: Byte = 0xD0.toByte()
    const val CMD_GS_RET_CAPABILITY: Byte = 0xD1.toByte()
    const val CMD_GS_GET_PARAM: Byte = 0xD6.toByte()
    const val CMD_GS_RET_PARAM: Byte = 0xD7.toByte()
    const val CMD_GS_SET_PARAM: Byte = 0xD8.toByte()
    const val CMD_GS_NTFY_PARAM: Byte = 0xD9.toByte()
    const val GS_TYPE_MULTIPOINT: Byte = 0xD2.toByte()
    const val GS_TYPE_TOUCH_PANEL: Byte = 0xD3.toByte()

    // Audio Priority & DSEE Upscaling (Table 1 / 0x0C)
    const val CMD_AUDIO_GET_PARAM: Byte = 0xE6.toByte()
    const val CMD_AUDIO_RET_PARAM: Byte = 0xE7.toByte()
    const val CMD_AUDIO_SET_PARAM: Byte = 0xE8.toByte()
    const val CMD_AUDIO_NTFY_PARAM: Byte = 0xE9.toByte()
    const val AUDIO_TYPE_CONNECTION_MODE: Byte = 0x00
    const val AUDIO_TYPE_UPSCALING: Byte = 0x01
    const val AUDIO_TYPE_CONNECTION_MODE_LDAC: Byte = 0x02
    const val AUDIO_TYPE_CONNECTION_MODE_CLASSIC_LE: Byte = 0x05

    // System Features: Wear Sensor, Speak-to-Chat, Touch Assignment (Table 1 / 0x0C)
    const val CMD_SYSTEM_GET_PARAM: Byte = 0xF6.toByte()
    const val CMD_SYSTEM_RET_PARAM: Byte = 0xF7.toByte()
    const val CMD_SYSTEM_SET_PARAM: Byte = 0xF8.toByte()
    const val CMD_SYSTEM_NTFY_PARAM: Byte = 0xF9.toByte()
    const val CMD_SYSTEM_GET_EXT_PARAM: Byte = 0xFA.toByte()
    const val CMD_SYSTEM_RET_EXT_PARAM: Byte = 0xFB.toByte()
    const val CMD_SYSTEM_SET_EXT_PARAM: Byte = 0xFC.toByte()
    const val CMD_SYSTEM_NTFY_EXT_PARAM: Byte = 0xFD.toByte()
    const val SYSTEM_TYPE_WEAR_PLAYBACK: Byte = 0x01
    const val SYSTEM_TYPE_SMART_TALKING_1: Byte = 0x02
    const val SYSTEM_TYPE_ASSIGNABLE: Byte = 0x03
    const val SYSTEM_TYPE_SMART_TALKING_V1: Byte = 0x05
    const val SYSTEM_TYPE_SMART_TALKING_2: Byte = 0x0C
    const val SYSTEM_TYPE_ASSIGNABLE_LIMIT: Byte = 0x0E

    // Table 2 (0x0E): PERI (Multipoint & Source Switch)
    const val CMD_PERI_GET_STATUS: Byte = 0x32
    const val CMD_PERI_RET_STATUS: Byte = 0x33
    const val CMD_PERI_SET_STATUS: Byte = 0x34
    const val CMD_PERI_NTFY_STATUS: Byte = 0x35
    const val CMD_PERI_GET_PARAM: Byte = 0x36
    const val CMD_PERI_RET_PARAM: Byte = 0x37
    const val CMD_PERI_SET_PARAM: Byte = 0x38
    const val CMD_PERI_NTFY_PARAM: Byte = 0x39
    const val CMD_PERI_SET_EXT_PARAM: Byte = 0x3C
    const val CMD_PERI_NTFY_EXT_PARAM: Byte = 0x3D
    const val PERI_TYPE_PAIRING_CLASSIC: Byte = 0x00
    const val PERI_TYPE_SOURCE_SWITCH: Byte = 0x01
    const val PERI_TYPE_PAIRING_COD: Byte = 0x02

    // Table 2 (0x0E): VOICE_GUIDANCE
    const val CMD_VG_GET_PARAM: Byte = 0x46
    const val CMD_VG_RET_PARAM: Byte = 0x47
    const val CMD_VG_SET_PARAM: Byte = 0x48
    const val CMD_VG_NTFY_PARAM: Byte = 0x49
    const val VG_TYPE_VOLUME: Byte = 0x20
    const val VG_TYPE_VOLUME_5STEP: Byte = 0x21

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
        // Estándar oficial Sony MDR (b & 0xEF): 0x3E->0x3D 0x2E, 0x3D->0x3D 0x2D, 0x3C->0x3D 0x2C
        when (b) {
            START_MARKER -> {
                out.write(ESCAPE_BYTE.toInt())
                out.write(0x2E)
            }
            ESCAPE_BYTE -> {
                out.write(ESCAPE_BYTE.toInt())
                out.write(0x2D)
            }
            END_MARKER -> {
                out.write(ESCAPE_BYTE.toInt())
                out.write(0x2C)
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
                    0x2E.toByte() -> {
                        out.write(START_MARKER.toInt())
                        i += 2
                    }
                    0x2D.toByte() -> {
                        out.write(ESCAPE_BYTE.toInt())
                        i += 2
                    }
                    0x2C.toByte() -> {
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

    // Handshake y Descubrimiento Sony MDR
    fun buildProtocolInfoQueryPacket(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_CONNECT_GET_PROTOCOL_INFO, 0x00))

    fun buildModelNameQueryPacket(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_CONNECT_GET_DEVICE_INFO, DEVICE_INFO_MODEL_NAME))

    fun buildFirmwareVersionQueryPacket(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_CONNECT_GET_DEVICE_INFO, DEVICE_INFO_FW_VERSION))

    fun buildSupportFunctionQueryPacket(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_CONNECT_GET_SUPPORT_FUNCTION, 0x00))

    fun buildCodecQueryPacket(seq: Byte, protocolVersion: Int = 2): ByteArray =
        if (protocolVersion == 1) {
            buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_AUDIO_CODEC_GET_V1, 0x00))
        } else {
            buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_COMMON_GET_STATUS_V2, COMMON_TYPE_AUDIO_CODEC))
        }

    // Consultas de batería Sony MDR V1 (exclusivas para protocolo V1: 0x10)
    fun buildSingleBatteryQueryV1Packet(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_BATTERY_V1, BATTERY_TYPE_SINGLE))

    fun buildDualBatteryQueryV1Packet(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_BATTERY_V1, BATTERY_TYPE_DUAL_LEGACY))

    fun buildCaseBatteryQueryV1Packet(seq: Byte): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_BATTERY_V1, BATTERY_TYPE_CASE_LEGACY))

    /**
     * Calcula el bit de secuencia esperado en el paquete ACK de respuesta ante una trama MDR recibida.
     */
    fun nextAckSequenceForIncoming(incomingSeq: Byte): Byte =
        ((1 - (incomingSeq.toInt() and 1)) and 0x01).toByte()

    /**
     * Construye un paquete de confirmación (ACK) con DATA_TYPE = 0x01 y el número de secuencia indicado.
     */
    fun buildAckPacket(seq: Byte): ByteArray {
        return buildPacket(DATA_TYPE_ACK, seq, byteArrayOf())
    }

    fun ncAsmCategoryFor(
        protocolVersion: Int = 2,
        modelFamily: SonyModelFamily = SonyModelFamily.GENERIC_TWS
    ): Byte = when {
        protocolVersion == 1 -> CATEGORY_NC_ASM_V1
        modelFamily == SonyModelFamily.WH_1000XM6 -> CATEGORY_NC_ASM_XM6
        modelFamily == SonyModelFamily.WF_1000XM4 -> CATEGORY_NC_ASM_WF_XM4
        else -> CATEGORY_NC_ASM
    }

    /**
     * Genera el comando para consultar el estado actual de NC / ASM según protocolo V1 o V2 y familia.
     */
    fun buildNcAsmQueryPacket(
        seq: Byte,
        protocolVersion: Int = 2,
        modelFamily: SonyModelFamily = SonyModelFamily.GENERIC_TWS
    ): ByteArray =
        buildPacket(DATA_TYPE_MDR, seq, byteArrayOf(CMD_GET_NC_ASM, ncAsmCategoryFor(protocolVersion, modelFamily)))

    fun buildSetNcAsmForProtocol(
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        seq: Byte,
        protocolVersion: Int
    ): ByteArray {
        return if (protocolVersion == 1) {
            buildSetNcAsmV1Packet(mode, ambientLevel, voiceFocus, seq)
        } else {
            buildSetNcAsmPacket(mode, ambientLevel, voiceFocus, seq)
        }
    }

    fun buildSetNcAsmV1Payload(
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean
    ): ByteArray {
        val level = if (mode == NoiseControlMode.AMBIENT) ambientLevel.coerceIn(1, 20).toByte() else 0x00.toByte()
        val effect: Byte = if (mode == NoiseControlMode.OFF) 0x00 else 0x11
        val dualSingle: Byte = if (mode == NoiseControlMode.ANC) 0x02 else 0x00
        val voiceFocusByte: Byte = if (voiceFocus) 0x01 else 0x00
        return byteArrayOf(
            CMD_SET_NC_ASM,
            CATEGORY_NC_ASM_V1,
            effect,
            0x01,
            dualSingle,
            0x01,
            voiceFocusByte,
            level
        )
    }

    /**
     * Comando SET_NC_ASM para Sony MDR V1 (WH-1000XM3, WH-1000XM4, WF-1000XM3, etc.).
     */
    fun buildSetNcAsmV1Packet(
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        seq: Byte
    ): ByteArray = buildPacket(DATA_TYPE_MDR, seq, buildSetNcAsmV1Payload(mode, ambientLevel, voiceFocus))

    fun buildSetNcAsmPayload(
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean
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
        return when (mode) {
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
    ): ByteArray = buildPacket(DATA_TYPE_MDR, seq, buildSetNcAsmPayload(mode, ambientLevel, voiceFocus))

    /**
     * Extrae y parsea los marcos de datos contenidos en un flujo recibido.
     * Devuelve una lista de payloads sin cabecera de trama ya validados por checksum.
     */
    fun parseFrames(buffer: ByteArray): List<ParsedFrame> = extractFrames(buffer).first

    /**
     * Extrae todas las tramas completas delimitadas por START_MARKER..END_MARKER
     * y retorna las tramas válidas junto con el remanente incompleto del stream.
     */
    fun extractFrames(buffer: ByteArray): Pair<List<ParsedFrame>, ByteArray> {
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

        val remaining = if (start != -1 && start < buffer.size) {
            buffer.copyOfRange(start, buffer.size)
        } else {
            ByteArray(0)
        }
        return frames to remaining
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
                    updated = updated.copy(lastSyncTime = System.currentTimeMillis())
                }
            }
        } else if (cmd == CMD_RET_BATTERY_V1 || (cmd == CMD_NOTIF_BATTERY_V1 && currentState.protocolVersion == 1)) {
            if (payload.size >= 4) {
                val kind = payload[1].toInt() and 0xFF
                when (kind) {
                    0x00 -> { // Single V1: [0x11, 0x00, level, charging]
                        val raw = payload[2].toInt() and 0xFF
                        val lvl = if (raw in 1..100) raw else -1
                        val chg = (payload[3].toInt() and 0xFF) == 1
                        updated = updated.copy(
                            singleBattery = BatteryInfo(lvl, chg),
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }
                    0x01 -> { // Dual V1: [0x11, 0x01, L_lvl, L_chg, R_lvl, R_chg]
                        if (payload.size >= 6) {
                            val leftRaw = payload[2].toInt() and 0xFF
                            val leftChg = (payload[3].toInt() and 0xFF) == 1
                            val leftLvl = if (leftRaw in 1..100) leftRaw else -1
                            val rightRaw = payload[4].toInt() and 0xFF
                            val rightChg = (payload[5].toInt() and 0xFF) == 1
                            val rightLvl = if (rightRaw in 1..100) rightRaw else -1

                            if (rightLvl == -1 && leftLvl > 0 && updated.deviceType == DeviceType.OVER_EAR) {
                                // Diademas V1 reflejan su batería en el slot izquierdo al responder consulta dual
                                updated = updated.copy(
                                    singleBattery = BatteryInfo(leftLvl, leftChg),
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            } else {
                                updated = updated.copy(
                                    leftBattery = BatteryInfo(leftLvl, leftChg),
                                    rightBattery = BatteryInfo(rightLvl, rightChg),
                                    singleBattery = if (leftLvl > 0) BatteryInfo(leftLvl, leftChg) else if (rightLvl > 0) BatteryInfo(rightLvl, rightChg) else currentState.singleBattery,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }
                        }
                    }
                    0x02 -> { // Case V1: [0x11, 0x02, level, charging]
                        val raw = payload[2].toInt() and 0xFF
                        val lvl = if (raw in 1..100) raw else -1
                        val chg = (payload[3].toInt() and 0xFF) == 1
                        updated = updated.copy(
                            caseBattery = BatteryInfo(lvl, chg),
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }
                }
            }
        }

        return updated
    }

    fun parseProtocolInfoPayload(payload: ByteArray): Int? {
        if (payload.isEmpty() || payload[0] != CMD_CONNECT_RET_PROTOCOL_INFO) return null
        return if (payload.size >= 8) 2 else if (payload.size >= 3) 1 else null
    }

    fun parseDeviceInfoPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 4 || payload[0] != CMD_CONNECT_RET_DEVICE_INFO) return currentState
        val infoType = payload[1]
        val declaredLen = payload[2].toInt() and 0xFF
        val endIdx = (3 + declaredLen).coerceAtMost(payload.size)
        if (endIdx <= 3) return currentState
        val ascii = String(payload, 3, endIdx - 3, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
        if (ascii.isBlank()) return currentState

        return when (infoType) {
            DEVICE_INFO_MODEL_NAME -> {
                val withHw = currentState.copy(hardwareModelName = ascii, lastSyncTime = System.currentTimeMillis())
                val resolvedType = if (currentState.forcedModelFamily == null) {
                    val detected = SonyModelFamily.detect(ascii, currentState.deviceType)
                    if (detected != SonyModelFamily.GENERIC_TWS && detected != SonyModelFamily.GENERIC_OVER_EAR) {
                        detected.defaultDeviceType
                    } else currentState.deviceType
                } else currentState.deviceType
                withHw.copy(deviceType = resolvedType)
            }
            DEVICE_INFO_FW_VERSION -> {
                currentState.copy(firmwareVersion = ascii, lastSyncTime = System.currentTimeMillis())
            }
            else -> currentState
        }
    }

    fun parseSupportFunctionPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 3 || payload[0] != CMD_CONNECT_RET_SUPPORT_FUNCTION || currentState.protocolVersion != 2) {
            return currentState
        }
        val count = payload[2].toInt() and 0xFF
        val funcs = mutableSetOf<Int>()
        var idx = 3
        var parsed = 0
        // Cada MessageMdrV2SupportFunction ocupa 2 bytes: [functionType, priority]
        while (parsed < count && idx + 1 < payload.size) {
            funcs.add(payload[idx].toInt() and 0xFF)
            idx += 2
            parsed++
        }
        if (funcs.isEmpty()) return currentState

        val hasNcAsm = funcs.any { it in 0x61..0x6D }
        val resolvedFamily = currentState.modelFamily
        val ancOverride: Boolean? = when {
            currentState.forcedModelFamily != null -> null
            hasNcAsm -> true
            resolvedFamily == SonyModelFamily.GENERIC_TWS || resolvedFamily == SonyModelFamily.GENERIC_OVER_EAR -> false
            else -> null
        }
        return currentState.copy(
            supportFunctions = funcs,
            supportsAncOverride = ancOverride ?: currentState.supportsAncOverride,
            lastSyncTime = System.currentTimeMillis()
        )
    }

    fun parseCodecPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 3) return currentState
        val isV2Codec = currentState.protocolVersion == 2 &&
            (payload[0] == CMD_COMMON_RET_STATUS_V2 || payload[0] == CMD_COMMON_NTFY_STATUS_V2) &&
            payload[1] == COMMON_TYPE_AUDIO_CODEC
        val isV1Codec = (payload[0] == CMD_AUDIO_CODEC_RET_V1 || payload[0] == CMD_AUDIO_CODEC_NTFY_V1) &&
            payload[1] == 0x00.toByte()
        if (!isV2Codec && !isV1Codec) return currentState

        val codec = when (payload[2].toInt() and 0xFF) {
            0x01 -> "SBC"
            0x02 -> "AAC"
            0x10 -> "LDAC"
            0x20 -> "aptX"
            0x21 -> "aptX HD"
            0x30 -> "LC3"
            else -> ""
        }
        if (codec.isBlank()) return currentState
        return currentState.copy(codecName = codec, lastSyncTime = System.currentTimeMillis())
    }

    /**
     * Parsea la respuesta o notificación push (0x67 / 0x69) de estado de NC / ASM para V1 y V2.
     */
    fun parseNcAsmPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 4 || (payload[0] != CMD_RET_NC_ASM && payload[0] != CMD_NTFY_NC_ASM)) return currentState
        val subType = payload[1]

        // Trama V1 (8 bytes): [0x67/0x69, 0x02, effect, ncSettingType, dualSingle, asmSettingType, voiceFocus, level]
        if (subType == CATEGORY_NC_ASM_V1) {
            if (payload.size < 8) return currentState
            val effect = payload[2].toInt() and 0xFF
            val dualSingle = payload[4].toInt() and 0xFF
            val vf = (payload[6].toInt() and 0xFF) == 1
            val lvlRaw = payload[7].toInt() and 0xFF
            val lvl = if (lvlRaw in 1..20) lvlRaw else currentState.ambientLevel
            val mode = when {
                effect == 0x00 -> NoiseControlMode.OFF
                dualSingle == 0x00 -> NoiseControlMode.AMBIENT
                else -> NoiseControlMode.ANC
            }
            return currentState.copy(
                noiseMode = mode,
                ambientLevel = lvl,
                voiceFocus = vf,
                lastSyncTime = System.currentTimeMillis()
            )
        }

        // Ignorar subtipos que no son NC/ASM (ej. button mode)
        if (subType != CATEGORY_NC_ASM && subType != CATEGORY_NC_ASM_XM6 && subType != CATEGORY_NC_ASM_WF_XM4) {
            return currentState
        }

        val enabledByte = payload[3].toInt() and 0xFF
        val ambientByte = if (payload.size > 4) payload[4].toInt() and 0xFF else 0

        // Extracción robusta de nivel, enfoque de voz y viento (7 bytes estándar / 8 bytes WF-1000XM4 0x15)
        val (parsedVoiceFocus, parsedLevel, parsedWind) = when {
            payload.size >= 8 && subType == CATEGORY_NC_ASM_WF_XM4 -> {
                val windOn = (payload[5].toInt() and 0xFF) != 0x00
                val vf = (payload[6].toInt() and 0xFF) == 1
                val lvlRaw = payload[7].toInt() and 0xFF
                val lvl = if (lvlRaw in 1..20) lvlRaw else currentState.ambientLevel
                Triple(vf, lvl, windOn)
            }
            payload.size >= 8 -> {
                val vf = (payload[6].toInt() and 0xFF) == 1
                val lvlRaw = payload[7].toInt() and 0xFF
                val lvl = if (lvlRaw in 1..20) lvlRaw else currentState.ambientLevel
                Triple(vf, lvl, currentState.windNoiseReduction)
            }
            payload.size >= 7 && (payload[6].toInt() and 0xFF) in 1..20 -> {
                val vf = (payload[5].toInt() and 0xFF) == 1
                val lvl = payload[6].toInt() and 0xFF
                Triple(vf, lvl, currentState.windNoiseReduction)
            }
            payload.size >= 6 -> {
                val vf = (payload[5].toInt() and 0xFF) == 1
                Triple(vf, currentState.ambientLevel, currentState.windNoiseReduction)
            }
            else -> Triple(currentState.voiceFocus, currentState.ambientLevel, currentState.windNoiseReduction)
        }

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
            windNoiseReduction = parsedWind,
            lastSyncTime = System.currentTimeMillis()
        )
    }

    /**
     * Genera payload de 8 bytes exclusivamente para modelos con auto-reducción de viento (0x15 / WF-1000XM4).
     * Mantiene intacto buildSetNcAsmPayload (7 bytes, 0x17) para el resto de la gama Sony.
     */
    fun buildSetNcAsmWithWindPayload(
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        windReduction: Boolean
    ): ByteArray {
        val enabledByte: Byte = if (mode == NoiseControlMode.OFF) 0x00 else 0x01
        val ambientByte: Byte = if (mode == NoiseControlMode.AMBIENT) 0x01 else 0x00
        val windByte: Byte = if (mode == NoiseControlMode.ANC && windReduction) 0x01 else 0x00
        val voiceByte: Byte = if (mode == NoiseControlMode.AMBIENT && voiceFocus) 0x01 else 0x00
        val levelByte: Byte = if (mode == NoiseControlMode.AMBIENT) ambientLevel.coerceIn(1, 20).toByte() else 0x00
        return byteArrayOf(
            CMD_SET_NC_ASM,
            CATEGORY_NC_ASM_WF_XM4,
            0x01,
            enabledByte,
            ambientByte,
            windByte,
            voiceByte,
            levelByte
        )
    }

    // =========================================================================
    // FASE 2: ECUALIZADOR (V1/V2 5-band + Clear Bass Y WH-1000XM6 10-band raw)
    // =========================================================================

    fun eqInquiredTypeFor(state: HeadsetState): Byte = when {
        state.protocolVersion < 2 -> EQ_TYPE_PRESET_V1
        state.capabilities.tenBandEq -> EQ_TYPE_PRESET_XM6_10BAND
        else -> EQ_TYPE_PRESET_V2
    }

    fun buildEqualizerGetPayload(state: HeadsetState): ByteArray =
        byteArrayOf(CMD_EQEBB_GET_PARAM, eqInquiredTypeFor(state))

    fun buildEqualizerSetPresetPayload(state: HeadsetState, presetId: Int): ByteArray =
        byteArrayOf(CMD_EQEBB_SET_PARAM, eqInquiredTypeFor(state), (presetId and 0xFF).toByte(), 0x00)

    fun buildEqualizerSetCustomPayload(state: HeadsetState, clearBass: Int, bands: List<Int>): ByteArray {
        val inquired = eqInquiredTypeFor(state)
        if (state.capabilities.tenBandEq) {
            val out = ByteArray(4 + bands.size)
            out[0] = CMD_EQEBB_SET_PARAM
            out[1] = EQ_TYPE_PRESET_XM6_10BAND
            out[2] = EqPreset.CUSTOM.id.toByte()
            out[3] = bands.size.toByte()
            for (i in bands.indices) {
                out[4 + i] = bands[i].coerceIn(0, 12).toByte()
            }
            return out
        }
        val presetByte = if (state.protocolVersion < 2) EqPreset.UNSPECIFIED.id.toByte() else EqPreset.CUSTOM.id.toByte()
        val out = ByteArray(5 + bands.size)
        out[0] = CMD_EQEBB_SET_PARAM
        out[1] = inquired
        out[2] = presetByte
        out[3] = (1 + bands.size).toByte()
        out[4] = (clearBass.coerceIn(-10, 10) + 10).toByte()
        for (i in bands.indices) {
            out[5 + i] = (bands[i].coerceIn(-10, 10) + 10).toByte()
        }
        return out
    }

    fun parseEqualizerPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 4 || (payload[0] != CMD_EQEBB_RET_PARAM && payload[0] != CMD_EQEBB_NTFY_PARAM)) {
            return currentState
        }
        val inquired = payload[1].toInt() and 0xFF
        if (inquired !in setOf(0x00, 0x01, 0x02, 0x03, 0x04)) return currentState

        val presetId = payload[2].toInt() and 0xFF
        val count = payload[3].toInt() and 0xFF
        val isTenBand = inquired == EQ_TYPE_PRESET_XM6_10BAND.toInt() ||
            (currentState.capabilities.tenBandEq && count == 10)

        if (count == 0) {
            return currentState.copy(
                eqState = currentState.eqState.copy(
                    isSupported = true,
                    isLoaded = true,
                    presetId = presetId,
                    isTenBand = isTenBand
                ),
                lastSyncTime = System.currentTimeMillis()
            )
        }
        if (payload.size < 4 + count) return currentState

        return if (isTenBand) {
            val tenBands = (0 until count).map { (payload[4 + it].toInt() and 0xFF).coerceIn(0, 12) }
            currentState.copy(
                eqState = EqState(
                    isSupported = true,
                    isLoaded = true,
                    presetId = presetId,
                    clearBass = 0,
                    bands = tenBands,
                    isTenBand = true
                ),
                lastSyncTime = System.currentTimeMillis()
            )
        } else {
            val cb = ((payload[4].toInt() and 0xFF) - 10).coerceIn(-10, 10)
            val fiveBands = (1 until count).map { ((payload[4 + it].toInt() and 0xFF) - 10).coerceIn(-10, 10) }
            currentState.copy(
                eqState = EqState(
                    isSupported = true,
                    isLoaded = true,
                    presetId = presetId,
                    clearBass = cb,
                    bands = fiveBands,
                    isTenBand = false
                ),
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }

    // =========================================================================
    // FASE 2: AUDIO (DSEE Upscaling & Bluetooth Connection Priority)
    // =========================================================================

    fun buildDseeGetPayload(): ByteArray = byteArrayOf(CMD_AUDIO_GET_PARAM, AUDIO_TYPE_UPSCALING)

    fun buildDseeSetPayload(enabled: Boolean): ByteArray =
        byteArrayOf(CMD_AUDIO_SET_PARAM, AUDIO_TYPE_UPSCALING, if (enabled) 0x01 else 0x00)

    fun buildConnectionPriorityGetPayload(inquiredType: Int): ByteArray =
        byteArrayOf(CMD_AUDIO_GET_PARAM, inquiredType.toByte())

    fun buildConnectionPrioritySetPayload(inquiredType: Int, priority: ConnectionPriority): ByteArray =
        if (inquiredType == AUDIO_TYPE_CONNECTION_MODE_CLASSIC_LE.toInt()) {
            byteArrayOf(CMD_AUDIO_SET_PARAM, AUDIO_TYPE_CONNECTION_MODE_CLASSIC_LE, priority.byteValue.toByte(), 0x00)
        } else {
            byteArrayOf(CMD_AUDIO_SET_PARAM, inquiredType.toByte(), priority.byteValue.toByte())
        }

    fun parseAudioPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 3 || (payload[0] != CMD_AUDIO_RET_PARAM && payload[0] != CMD_AUDIO_NTFY_PARAM)) {
            return currentState
        }
        val inquired = payload[1].toInt() and 0xFF
        val valByte = payload[2].toInt() and 0xFF
        return when (inquired) {
            AUDIO_TYPE_UPSCALING.toInt() -> currentState.copy(
                dseeEnabled = (valByte == 0x01),
                lastSyncTime = System.currentTimeMillis()
            )
            AUDIO_TYPE_CONNECTION_MODE.toInt(),
            AUDIO_TYPE_CONNECTION_MODE_LDAC.toInt(),
            AUDIO_TYPE_CONNECTION_MODE_CLASSIC_LE.toInt() -> {
                val priority = ConnectionPriority.fromByte(valByte) ?: return currentState
                currentState.copy(
                    connectionPriority = priority,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
            else -> currentState
        }
    }

    // =========================================================================
    // FASE 2: POWER (Auto Power Off & Remote Power Off V2)
    // =========================================================================

    fun buildAutoPowerOffGetPayload(withWearDetection: Boolean): ByteArray =
        byteArrayOf(CMD_POWER_GET_PARAM, if (withWearDetection) POWER_TYPE_AUTO_POWER_OFF_WEAR else POWER_TYPE_AUTO_POWER_OFF)

    fun buildAutoPowerOffSetPayload(withWearDetection: Boolean, option: AutoPowerOffOption): ByteArray {
        val inquired = if (withWearDetection) POWER_TYPE_AUTO_POWER_OFF_WEAR else POWER_TYPE_AUTO_POWER_OFF
        return byteArrayOf(CMD_POWER_SET_PARAM, inquired, option.byteValue.toByte(), option.byteValue.toByte())
    }

    /** Strictly V2 (never emits V1 0x22). */
    fun buildRemotePowerOffV2Payload(): ByteArray =
        byteArrayOf(CMD_POWER_SET_STATUS, POWER_TYPE_POWER_OFF, POWER_OFF_USER)

    fun parsePowerParamPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 3 || (payload[0] != CMD_POWER_RET_PARAM && payload[0] != CMD_POWER_NTFY_PARAM)) {
            return currentState
        }
        val inquired = payload[1].toInt() and 0xFF
        if (inquired != POWER_TYPE_AUTO_POWER_OFF.toInt() && inquired != POWER_TYPE_AUTO_POWER_OFF_WEAR.toInt()) {
            return currentState
        }
        val opt = AutoPowerOffOption.fromByte(payload[2].toInt() and 0xFF) ?: return currentState
        return currentState.copy(
            autoPowerOff = opt,
            lastSyncTime = System.currentTimeMillis()
        )
    }

    // =========================================================================
    // FASE 2: SYSTEM (Speak-to-Chat, Wear Sensor, Assignable Touch Settings)
    // =========================================================================

    fun buildSpeakToChatGetPayload(inquiredType: Int): ByteArray =
        byteArrayOf(CMD_SYSTEM_GET_PARAM, inquiredType.toByte())

    fun buildSpeakToChatSetPayload(inquiredType: Int, enabled: Boolean, protocolVersion: Int): ByteArray =
        if (protocolVersion < 2 || inquiredType == SYSTEM_TYPE_SMART_TALKING_V1.toInt()) {
            byteArrayOf(CMD_SYSTEM_SET_PARAM, SYSTEM_TYPE_SMART_TALKING_V1, if (enabled) 0x01 else 0x00)
        } else {
            byteArrayOf(CMD_SYSTEM_SET_PARAM, inquiredType.toByte(), if (enabled) 0x00 else 0x01, 0x01)
        }

    fun buildSpeakToChatExtGetPayload(inquiredType: Int): ByteArray =
        byteArrayOf(CMD_SYSTEM_GET_EXT_PARAM, inquiredType.toByte())

    fun buildSpeakToChatExtSetPayload(
        inquiredType: Int,
        extMode: Int,
        sensitivity: SpeakToChatSensitivity,
        returnTime: SpeakToChatReturnTime,
        focusOnVoice: Boolean
    ): ByteArray = if (extMode == 1 || inquiredType == SYSTEM_TYPE_SMART_TALKING_1.toInt()) {
        byteArrayOf(
            CMD_SYSTEM_SET_EXT_PARAM,
            SYSTEM_TYPE_SMART_TALKING_1,
            sensitivity.byteValue.toByte(),
            if (focusOnVoice) 0x00 else 0x01,
            returnTime.byteValue.toByte()
        )
    } else {
        byteArrayOf(
            CMD_SYSTEM_SET_EXT_PARAM,
            SYSTEM_TYPE_SMART_TALKING_2,
            sensitivity.byteValue.toByte(),
            returnTime.byteValue.toByte()
        )
    }

    fun buildPauseWhenRemovedGetPayload(): ByteArray =
        byteArrayOf(CMD_SYSTEM_GET_PARAM, SYSTEM_TYPE_WEAR_PLAYBACK)

    fun buildPauseWhenRemovedSetPayload(enabled: Boolean): ByteArray =
        byteArrayOf(CMD_SYSTEM_SET_PARAM, SYSTEM_TYPE_WEAR_PLAYBACK, if (enabled) 0x00 else 0x01)

    fun buildTouchAssignmentGetPayload(inquiredType: Int): ByteArray =
        byteArrayOf(CMD_SYSTEM_GET_PARAM, inquiredType.toByte())

    fun buildTouchAssignmentSetPayload(inquiredType: Int, presets: List<TouchPresetOption>): ByteArray {
        val out = ByteArray(3 + presets.size)
        out[0] = CMD_SYSTEM_SET_PARAM
        out[1] = inquiredType.toByte()
        out[2] = presets.size.toByte()
        for (i in presets.indices) {
            out[3 + i] = presets[i].byteValue.toByte()
        }
        return out
    }

    fun parseSystemPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 3) return currentState
        val cmd = payload[0]
        val inquired = payload[1].toInt() and 0xFF
        val valByte = payload[2].toInt() and 0xFF

        if (cmd == CMD_SYSTEM_RET_PARAM || cmd == CMD_SYSTEM_NTFY_PARAM) {
            return when (inquired) {
                SYSTEM_TYPE_WEAR_PLAYBACK.toInt() -> currentState.copy(
                    pauseWhenRemoved = (valByte == 0x00),
                    lastSyncTime = System.currentTimeMillis()
                )
                SYSTEM_TYPE_SMART_TALKING_1.toInt(),
                SYSTEM_TYPE_SMART_TALKING_2.toInt() -> currentState.copy(
                    speakToChatEnabled = (valByte == 0x00),
                    lastSyncTime = System.currentTimeMillis()
                )
                SYSTEM_TYPE_SMART_TALKING_V1.toInt() -> {
                    val enabled = if (currentState.protocolVersion < 2) (valByte == 0x01) else (valByte == 0x00)
                    currentState.copy(
                        speakToChatEnabled = enabled,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }
                SYSTEM_TYPE_ASSIGNABLE.toInt(),
                SYSTEM_TYPE_ASSIGNABLE_LIMIT.toInt() -> {
                    val count = valByte
                    if (payload.size < 3 + count || count == 0) return currentState
                    val presets = (0 until count).map { TouchPresetOption.fromByte(payload[3 + it].toInt() and 0xFF) }
                    currentState.copy(
                        touchLeftPreset = presets.getOrNull(0) ?: currentState.touchLeftPreset,
                        touchRightPreset = presets.getOrNull(1) ?: currentState.touchRightPreset,
                        touchThirdPreset = presets.getOrNull(2),
                        lastSyncTime = System.currentTimeMillis()
                    )
                }
                else -> currentState
            }
        }

        if (cmd == CMD_SYSTEM_RET_EXT_PARAM || cmd == CMD_SYSTEM_NTFY_EXT_PARAM) {
            return when (inquired) {
                SYSTEM_TYPE_SMART_TALKING_1.toInt() -> {
                    if (payload.size < 5) return currentState
                    currentState.copy(
                        speakToChatSensitivity = SpeakToChatSensitivity.fromByte(payload[2].toInt() and 0xFF),
                        speakToChatFocusOnVoice = (payload[3].toInt() and 0xFF) == 0x00,
                        speakToChatReturnTime = SpeakToChatReturnTime.fromByte(payload[4].toInt() and 0xFF),
                        lastSyncTime = System.currentTimeMillis()
                    )
                }
                SYSTEM_TYPE_SMART_TALKING_2.toInt() -> {
                    if (payload.size < 4) return currentState
                    currentState.copy(
                        speakToChatSensitivity = SpeakToChatSensitivity.fromByte(payload[2].toInt() and 0xFF),
                        speakToChatReturnTime = SpeakToChatReturnTime.fromByte(payload[3].toInt() and 0xFF),
                        lastSyncTime = System.currentTimeMillis()
                    )
                }
                else -> currentState
            }
        }

        return currentState
    }

    // =========================================================================
    // FASE 2: GENERAL SETTINGS (0xD0..0xD9: Multipoint fallback & Touch lock)
    // =========================================================================

    fun buildGsCapabilityGetPayload(gsType: Byte, lang: Byte = 0x00): ByteArray =
        byteArrayOf(CMD_GS_GET_CAPABILITY, gsType, lang)

    fun buildGsParamGetPayload(gsType: Byte): ByteArray =
        byteArrayOf(CMD_GS_GET_PARAM, gsType)

    fun buildGsBooleanSetPayload(gsType: Byte, enabled: Boolean): ByteArray =
        byteArrayOf(CMD_GS_SET_PARAM, gsType, 0x00, if (enabled) 0x00 else 0x01)

    fun parseGsPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 3) return currentState
        val cmd = payload[0]
        val gsType = payload[1].toInt() and 0xFF
        val settingType = payload[2].toInt() and 0xFF

        if (cmd == CMD_GS_RET_CAPABILITY) {
            return if (settingType == 0x00) {
                currentState.copy(
                    gsBooleanConfirmed = currentState.gsBooleanConfirmed + gsType,
                    lastSyncTime = System.currentTimeMillis()
                )
            } else {
                currentState
            }
        }

        if ((cmd == CMD_GS_RET_PARAM || cmd == CMD_GS_NTFY_PARAM) && payload.size >= 4 && settingType == 0x00) {
            val enabled = (payload[3].toInt() and 0xFF) == 0x00
            return when (gsType) {
                GS_TYPE_MULTIPOINT.toInt() and 0xFF -> currentState.copy(
                    gsBooleanConfirmed = currentState.gsBooleanConfirmed + gsType,
                    multipointEnabled = enabled,
                    lastSyncTime = System.currentTimeMillis()
                )
                GS_TYPE_TOUCH_PANEL.toInt() and 0xFF -> currentState.copy(
                    gsBooleanConfirmed = currentState.gsBooleanConfirmed + gsType,
                    touchPanelEnabled = enabled,
                    lastSyncTime = System.currentTimeMillis()
                )
                else -> currentState
            }
        }
        return currentState
    }

    // =========================================================================
    // FASE 2: TABLE 2 (0x0E) - SUPPORT FUNCTIONS, PERI (MULTIPOINT) & VOICE GUIDANCE
    // =========================================================================

    fun parseTable2SupportFunctionPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 3 || payload[0] != CMD_CONNECT_RET_SUPPORT_FUNCTION || currentState.protocolVersion < 2) {
            return currentState
        }
        val count = payload[2].toInt() and 0xFF
        val funcs = mutableSetOf<Int>()
        var idx = 3
        var parsed = 0
        while (parsed < count && idx + 1 < payload.size) {
            funcs.add(payload[idx].toInt() and 0xFF)
            idx += 2
            parsed++
        }
        if (funcs.isEmpty()) return currentState
        return currentState.copy(
            table2SupportFunctions = funcs,
            lastSyncTime = System.currentTimeMillis()
        )
    }

    private fun macAscii17Bytes(mac: String): ByteArray {
        val clean = mac.trim().uppercase()
        val padded = clean.padEnd(17, '0').take(17)
        return padded.toByteArray(Charsets.US_ASCII)
    }

    fun buildPeriStatusGetPayload(inquiredType: Int): ByteArray =
        byteArrayOf(CMD_PERI_GET_STATUS, inquiredType.toByte())

    fun buildPeriStatusSetPayload(inquiredType: Int, enabled: Boolean, btMode: Int = 0x00): ByteArray =
        byteArrayOf(CMD_PERI_SET_STATUS, inquiredType.toByte(), btMode.toByte(), if (enabled) 0x00 else 0x01)

    fun buildPeriParamGetPayload(inquiredType: Int): ByteArray =
        byteArrayOf(CMD_PERI_GET_PARAM, inquiredType.toByte())

    fun buildPeriConnectDisconnectPayload(inquiredType: Int, connect: Boolean, macAddress: String): ByteArray {
        val macBytes = macAscii17Bytes(macAddress)
        return byteArrayOf(CMD_PERI_SET_EXT_PARAM, inquiredType.toByte(), if (connect) 0x01 else 0x00) + macBytes
    }

    fun buildPeriSourceSwitchPayload(macAddress: String): ByteArray =
        byteArrayOf(CMD_PERI_SET_EXT_PARAM, PERI_TYPE_SOURCE_SWITCH) + macAscii17Bytes(macAddress)

    fun parsePeriPayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 3) return currentState
        val cmd = payload[0]
        val inquired = payload[1].toInt() and 0xFF

        if ((cmd == CMD_PERI_RET_STATUS || cmd == CMD_PERI_NTFY_STATUS) &&
            (inquired == PERI_TYPE_PAIRING_CLASSIC.toInt() || inquired == PERI_TYPE_PAIRING_COD.toInt()) &&
            payload.size >= 4
        ) {
            val enabled = (payload[3].toInt() and 0xFF) == 0x00
            return currentState.copy(
                multipointEnabled = enabled,
                lastSyncTime = System.currentTimeMillis()
            )
        }

        if ((cmd == CMD_PERI_RET_PARAM || cmd == CMD_PERI_NTFY_PARAM) &&
            (inquired == PERI_TYPE_PAIRING_CLASSIC.toInt() || inquired == PERI_TYPE_PAIRING_COD.toInt())
        ) {
            val hasCod = (inquired == PERI_TYPE_PAIRING_COD.toInt())
            val deviceCount = payload[2].toInt() and 0xFF
            var cursor = 3
            val rawDevices = mutableListOf<Triple<String, String, Int>>()

            for (i in 0 until deviceCount) {
                val minLen = 17 + 1 + (if (hasCod) 3 else 0) + 1
                if (cursor + minLen > payload.size) return currentState
                val mac = String(payload, cursor, 17, Charsets.US_ASCII)
                cursor += 17
                val connStatus = payload[cursor].toInt() and 0xFF
                cursor += 1
                if (hasCod) cursor += 3
                val nameLen = payload[cursor].toInt() and 0xFF
                cursor += 1
                if (cursor + nameLen > payload.size) return currentState
                val friendlyName = if (nameLen > 0) {
                    String(payload, cursor, nameLen, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                } else {
                    mac
                }
                cursor += nameLen
                rawDevices.add(Triple(mac, friendlyName.ifBlank { mac }, connStatus))
            }

            val playbackRight = if (cursor < payload.size) (payload[cursor].toInt() and 0xFF) else 0xFF
            val activeIdx = resolveActiveMultipointIndex(rawDevices, playbackRight)
            val resolved = rawDevices.mapIndexed { idx, (mac, name, status) ->
                MultipointDevice(
                    address = mac,
                    friendlyName = name,
                    isConnected = status != 0,
                    isActiveSource = idx == activeIdx
                )
            }
            return currentState.copy(
                pairedDevices = resolved,
                lastSyncTime = System.currentTimeMillis()
            )
        }

        return currentState
    }

    private fun resolveActiveMultipointIndex(
        devices: List<Triple<String, String, Int>>,
        playbackRightRaw: Int
    ): Int {
        val explicitlyPlaying = devices.indexOfFirst { it.third == 2 }
        if (explicitlyPlaying >= 0) return explicitlyPlaying
        if (playbackRightRaw == 0xFF || devices.isEmpty()) return -1
        if (playbackRightRaw in 1..devices.size) {
            val oneBasedConnected = devices[playbackRightRaw - 1].third != 0
            val zeroBasedValid = playbackRightRaw in devices.indices && devices[playbackRightRaw].third != 0
            if (oneBasedConnected && !zeroBasedValid) return playbackRightRaw - 1
        }
        if (playbackRightRaw in devices.indices && devices[playbackRightRaw].third != 0) {
            return playbackRightRaw
        }
        return -1
    }

    fun buildVoiceGuidanceParamGetPayload(inquiredType: Int): ByteArray =
        byteArrayOf(CMD_VG_GET_PARAM, inquiredType.toByte())

    fun buildVoiceGuidanceSetParamPayload(
        inquiredType: Int,
        enabled: Boolean,
        language: VoiceGuidanceLanguageOption = VoiceGuidanceLanguageOption.UNDEFINED
    ): ByteArray = if (inquiredType == 0x02) {
        byteArrayOf(
            CMD_VG_SET_PARAM,
            0x02,
            if (enabled) 0x00 else 0x01,
            language.byteValue.toByte()
        )
    } else {
        byteArrayOf(
            CMD_VG_SET_PARAM,
            inquiredType.toByte(),
            if (enabled) 0x00 else 0x01
        )
    }

    fun buildVoiceGuidanceVolumeGetPayload(inquiredType: Int): ByteArray =
        byteArrayOf(CMD_VG_GET_PARAM, inquiredType.toByte())

    fun buildVoiceGuidanceVolumeSetPayload(inquiredType: Int, volume: Int, playFeedbackSound: Boolean = true): ByteArray =
        byteArrayOf(
            CMD_VG_SET_PARAM,
            inquiredType.toByte(),
            volume.coerceIn(-2, 2).toByte(),
            if (playFeedbackSound) 0x00 else 0x01
        )

    fun parseVoiceGuidancePayload(payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.size < 3 || (payload[0] != CMD_VG_RET_PARAM && payload[0] != CMD_VG_NTFY_PARAM)) {
            return currentState
        }
        val inquired = payload[1].toInt() and 0xFF
        return when (inquired) {
            0x00, 0x01, 0x02, 0x03 -> {
                val enabled = (payload[2].toInt() and 0xFF) == 0x00
                val lang = if (payload.size >= 4) {
                    VoiceGuidanceLanguageOption.fromByte(payload[3].toInt() and 0xFF)
                } else {
                    currentState.voiceGuidanceLanguage
                }
                currentState.copy(
                    voiceGuidanceEnabled = enabled,
                    voiceGuidanceLanguage = lang,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
            VG_TYPE_VOLUME.toInt(),
            VG_TYPE_VOLUME_5STEP.toInt() -> {
                val vol = payload[2].toInt().coerceIn(-2, 2)
                currentState.copy(
                    voiceGuidanceVolume = vol,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
            else -> currentState
        }
    }

    /**
     * Despachador unificado de tramas entrantes (Table 1 0x0C y Table 2 0x0E).
     */
    fun parseIncomingPayload(dataType: Byte, payload: ByteArray, currentState: HeadsetState): HeadsetState {
        if (payload.isEmpty()) return currentState
        val cmd = payload[0]

        if (dataType == DATA_TYPE_MDR_NO2) {
            return when (cmd) {
                CMD_CONNECT_RET_SUPPORT_FUNCTION -> parseTable2SupportFunctionPayload(payload, currentState)
                CMD_PERI_RET_STATUS, CMD_PERI_NTFY_STATUS,
                CMD_PERI_RET_PARAM, CMD_PERI_NTFY_PARAM -> parsePeriPayload(payload, currentState)
                CMD_VG_RET_PARAM, CMD_VG_NTFY_PARAM -> parseVoiceGuidancePayload(payload, currentState)
                else -> currentState
            }
        }

        if (dataType != DATA_TYPE_MDR) return currentState

        var updated = parseBatteryPayload(payload, currentState)
        parseProtocolInfoPayload(payload)?.let { proto ->
            updated = updated.copy(protocolVersion = proto)
        }
        updated = parseDeviceInfoPayload(payload, updated)
        updated = parseSupportFunctionPayload(payload, updated)
        updated = parseCodecPayload(payload, updated)
        updated = parseNcAsmPayload(payload, updated)
        updated = parseEqualizerPayload(payload, updated)
        updated = parseAudioPayload(payload, updated)
        updated = parsePowerParamPayload(payload, updated)
        updated = parseSystemPayload(payload, updated)
        updated = parseGsPayload(payload, updated)
        return updated
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
