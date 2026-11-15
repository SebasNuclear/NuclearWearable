package com.nuclear.soundconnect.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyProtocolParserTest {

    @Test
    fun buildAndExtractFrames_roundTripsEscapeBytesAndEscapedChecksum() {
        // Test payloads that produce checksums equal to 0x3C, 0x3D, and 0x3E,
        // and contain 0x3C (60%), 0x3D (61%), and 0x3E (62%) directly inside payload.
        val payloadWithBatteryEscapeLevels = byteArrayOf(
            SonyProtocolParser.CMD_NTFY_BATTERY,
            SonyProtocolParser.BATTERY_TYPE_DUAL_V2,
            0x3C.toByte(), // 60%
            0x00,
            0x3D.toByte(), // 61%
            0x01
        )
        val packet = SonyProtocolParser.buildPacket(
            SonyProtocolParser.DATA_TYPE_MDR,
            0x00,
            payloadWithBatteryEscapeLevels
        )

        // Outer frame delimiters must be unique (all interior 0x3C/0x3D/0x3E must be escaped)
        assertEquals(SonyProtocolParser.START_MARKER, packet.first())
        assertEquals(SonyProtocolParser.END_MARKER, packet.last())
        for (i in 1 until packet.lastIndex) {
            assertFalse(
                "Interior byte at $i must not be START_MARKER",
                packet[i] == SonyProtocolParser.START_MARKER
            )
            assertFalse(
                "Interior byte at $i must not be END_MARKER",
                packet[i] == SonyProtocolParser.END_MARKER
            )
        }

        val (frames, remainder) = SonyProtocolParser.extractFrames(packet)
        assertEquals(1, frames.size)
        assertEquals(0, remainder.size)
        assertEquals(SonyProtocolParser.DATA_TYPE_MDR, frames[0].dataType)
        assertEquals(0x00.toByte(), frames[0].sequence)
        assertArrayEquals(payloadWithBatteryEscapeLevels, frames[0].payload)

        // Force exact checksum values 0x3C, 0x3D, 0x3E:
        // Header sum = DATA_TYPE_MDR (0x0C) + seq (0x01) + 4-byte length (payload len = 2) = 0x0F.
        for (targetChecksum in listOf(0x3C, 0x3D, 0x3E)) {
            val firstByte = (targetChecksum - 0x0F - 0x00) and 0xFF
            val payload = byteArrayOf(firstByte.toByte(), 0x00)
            val framed = SonyProtocolParser.buildPacket(SonyProtocolParser.DATA_TYPE_MDR, 0x01, payload)
            for (i in 1 until framed.lastIndex) {
                assertFalse(framed[i] == SonyProtocolParser.START_MARKER)
                assertFalse(framed[i] == SonyProtocolParser.END_MARKER)
            }
            val (parsed, rem) = SonyProtocolParser.extractFrames(framed)
            assertEquals("Checksum 0x${targetChecksum.toString(16)} failed roundtrip", 1, parsed.size)
            assertEquals(0, rem.size)
            assertArrayEquals(payload, parsed[0].payload)
        }
    }

    @Test
    fun extractFrames_handlesSplitChunksGarbageAndCorruptFrame() {
        val p1 = byteArrayOf(SonyProtocolParser.CMD_RET_BATTERY, 0x00, 0x3E, 0x00)
        val p2 = byteArrayOf(SonyProtocolParser.CMD_NTFY_NC_ASM, 0x17, 0x01, 0x01, 0x00, 0x00, 0x00)

        val f1 = SonyProtocolParser.buildPacket(SonyProtocolParser.DATA_TYPE_MDR, 0x00, p1)
        val f2 = SonyProtocolParser.buildPacket(SonyProtocolParser.DATA_TYPE_MDR, 0x01, p2)

        // Split f1 across two chunks with leading garbage and corrupt middle frame
        val splitPoint = f1.size / 2
        val chunk1 = byteArrayOf(0x00, 0x55, 0xAA.toByte()) + f1.copyOfRange(0, splitPoint)
        val firstExtract = SonyProtocolParser.extractFrames(chunk1)
        assertTrue(firstExtract.first.isEmpty())
        assertTrue(firstExtract.second.isNotEmpty())
        assertEquals(SonyProtocolParser.START_MARKER, firstExtract.second.first())

        val corruptFrame = byteArrayOf(
            SonyProtocolParser.START_MARKER,
            SonyProtocolParser.DATA_TYPE_MDR,
            0x00,
            0x00, 0x00, 0x00, 0x01,
            0x22,
            0x99.toByte(), // invalid checksum
            SonyProtocolParser.END_MARKER
        )

        val halfF2 = f2.copyOfRange(0, f2.size / 2)
        val chunk2 = firstExtract.second + f1.copyOfRange(splitPoint, f1.size) + corruptFrame + halfF2
        val secondExtract = SonyProtocolParser.extractFrames(chunk2)
        assertEquals(1, secondExtract.first.size)
        assertArrayEquals(p1, secondExtract.first[0].payload)
        assertEquals(SonyProtocolParser.START_MARKER, secondExtract.second.first())

        val thirdExtract = SonyProtocolParser.extractFrames(
            secondExtract.second + f2.copyOfRange(f2.size / 2, f2.size)
        )
        assertEquals(1, thirdExtract.first.size)
        assertEquals(0, thirdExtract.second.size)
        assertArrayEquals(p2, thirdExtract.first[0].payload)
    }

    @Test
    fun ackSequenceAndPacket_followAlternatingBitArq() {
        assertEquals(0x01.toByte(), SonyProtocolParser.nextAckSequenceForIncoming(0x00))
        assertEquals(0x00.toByte(), SonyProtocolParser.nextAckSequenceForIncoming(0x01))

        val ack1 = SonyProtocolParser.buildAckPacket(0x01)
        val (frames, rem) = SonyProtocolParser.extractFrames(ack1)
        assertEquals(1, frames.size)
        assertEquals(0, rem.size)
        assertEquals(SonyProtocolParser.DATA_TYPE_ACK, frames[0].dataType)
        assertEquals(0x01.toByte(), frames[0].sequence)
        assertEquals(0, frames[0].payload.size)
    }

    @Test
    fun buildSetNcAsmPayload_v2MaintainsStrictSevenByteInvariant() {
        val anc = SonyProtocolParser.buildSetNcAsmPayload(NoiseControlMode.ANC, 15, true)
        assertEquals(7, anc.size)
        assertArrayEquals(
            byteArrayOf(0x68, 0x17, 0x01, 0x01, 0x00, 0x00, 0x00),
            anc
        )

        val off = SonyProtocolParser.buildSetNcAsmPayload(NoiseControlMode.OFF, 15, true)
        assertEquals(7, off.size)
        assertArrayEquals(
            byteArrayOf(0x68, 0x17, 0x01, 0x00, 0x00, 0x00, 0x00),
            off
        )

        val ambientVoice = SonyProtocolParser.buildSetNcAsmPayload(NoiseControlMode.AMBIENT, 15, true)
        assertEquals(7, ambientVoice.size)
        assertArrayEquals(
            byteArrayOf(0x68, 0x17, 0x01, 0x01, 0x01, 0x01, 0x0F),
            ambientVoice
        )

        val ambientClampedLow = SonyProtocolParser.buildSetNcAsmPayload(NoiseControlMode.AMBIENT, -5, false)
        assertArrayEquals(
            byteArrayOf(0x68, 0x17, 0x01, 0x01, 0x01, 0x00, 0x01),
            ambientClampedLow
        )

        val ambientClampedHigh = SonyProtocolParser.buildSetNcAsmPayload(NoiseControlMode.AMBIENT, 99, false)
        assertArrayEquals(
            byteArrayOf(0x68, 0x17, 0x01, 0x01, 0x01, 0x00, 0x14),
            ambientClampedHigh
        )

        val v2Frame = SonyProtocolParser.extractFrames(
            SonyProtocolParser.buildSetNcAsmForProtocol(NoiseControlMode.AMBIENT, 12, false, 0x00, 2)
        ).first.single()
        assertEquals(7, v2Frame.payload.size)
    }

    @Test
    fun buildSetNcAsmV1Payload_buildsValidEightByteLegacyCommands() {
        assertArrayEquals(
            byteArrayOf(0x68, 0x02, 0x11, 0x01, 0x02, 0x01, 0x00, 0x00),
            SonyProtocolParser.buildSetNcAsmV1Payload(NoiseControlMode.ANC, 10, false)
        )
        assertArrayEquals(
            byteArrayOf(0x68, 0x02, 0x11, 0x01, 0x00, 0x01, 0x01, 0x0C),
            SonyProtocolParser.buildSetNcAsmV1Payload(NoiseControlMode.AMBIENT, 12, true)
        )
        assertArrayEquals(
            byteArrayOf(0x68, 0x02, 0x00, 0x01, 0x00, 0x01, 0x01, 0x00),
            SonyProtocolParser.buildSetNcAsmV1Payload(NoiseControlMode.OFF, 12, true)
        )
        assertArrayEquals(
            byteArrayOf(0x68, 0x02, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00),
            SonyProtocolParser.buildSetNcAsmV1Payload(NoiseControlMode.OFF, 12, false)
        )

        assertEquals(SonyProtocolParser.CATEGORY_NC_ASM_V1, SonyProtocolParser.ncAsmCategoryFor(1, SonyModelFamily.WH_1000XM4))
        assertEquals(SonyProtocolParser.CATEGORY_NC_ASM_XM6, SonyProtocolParser.ncAsmCategoryFor(2, SonyModelFamily.WH_1000XM6))
        assertEquals(SonyProtocolParser.CATEGORY_NC_ASM_WF_XM4, SonyProtocolParser.ncAsmCategoryFor(2, SonyModelFamily.WF_1000XM4))
        assertEquals(SonyProtocolParser.CATEGORY_NC_ASM, SonyProtocolParser.ncAsmCategoryFor(2, SonyModelFamily.WF_1000XM5))

        val v1Frame = SonyProtocolParser.extractFrames(
            SonyProtocolParser.buildSetNcAsmForProtocol(NoiseControlMode.ANC, 10, false, 0x01, 1)
        ).first.single()
        assertEquals(8, v1Frame.payload.size)
        assertEquals(SonyProtocolParser.CATEGORY_NC_ASM_V1, v1Frame.payload[1])
    }

    @Test
    fun parseNcAsmPayload_handlesV2PushAndV1LegacyAndSubtypes() {
        val base = HeadsetState(noiseMode = NoiseControlMode.OFF, ambientLevel = 14, voiceFocus = false)

        // V2 0x69 push AMBIENT level=18, voiceFocus=true
        val v2AmbientPush = SonyProtocolParser.parseNcAsmPayload(
            byteArrayOf(0x69, 0x17, 0x01, 0x01, 0x01, 0x01, 0x12),
            base
        )
        assertEquals(NoiseControlMode.AMBIENT, v2AmbientPush.noiseMode)
        assertEquals(18, v2AmbientPush.ambientLevel)
        assertTrue(v2AmbientPush.voiceFocus)

        // V2 0x67 ANC preserves user's saved ambientLevel (18) instead of zeroing it
        val v2AncRet = SonyProtocolParser.parseNcAsmPayload(
            byteArrayOf(0x67, 0x17, 0x01, 0x01, 0x00, 0x00, 0x00),
            v2AmbientPush
        )
        assertEquals(NoiseControlMode.ANC, v2AncRet.noiseMode)
        assertEquals(18, v2AncRet.ambientLevel)
        assertFalse(v2AncRet.voiceFocus)

        // WF-1000XM4 8-byte 0x15 push
        val xm4Push = SonyProtocolParser.parseNcAsmPayload(
            byteArrayOf(0x69, 0x15, 0x01, 0x01, 0x01, 0x01, 0x01, 0x09),
            base
        )
        assertEquals(NoiseControlMode.AMBIENT, xm4Push.noiseMode)
        assertEquals(9, xm4Push.ambientLevel)
        assertTrue(xm4Push.voiceFocus)

        // WH-1000XM6 0x19 OFF
        val xm6Off = SonyProtocolParser.parseNcAsmPayload(
            byteArrayOf(0x69, 0x19, 0x01, 0x00, 0x00, 0x00, 0x00),
            v2AmbientPush
        )
        assertEquals(NoiseControlMode.OFF, xm6Off.noiseMode)

        // V1 0x02 8-byte push: ANC -> AMBIENT -> OFF
        val v1Anc = SonyProtocolParser.parseNcAsmPayload(
            byteArrayOf(0x69, 0x02, 0x11, 0x01, 0x02, 0x01, 0x00, 0x00),
            base
        )
        assertEquals(NoiseControlMode.ANC, v1Anc.noiseMode)

        val v1Amb = SonyProtocolParser.parseNcAsmPayload(
            byteArrayOf(0x69, 0x02, 0x11, 0x01, 0x00, 0x01, 0x01, 0x10),
            v1Anc
        )
        assertEquals(NoiseControlMode.AMBIENT, v1Amb.noiseMode)
        assertEquals(16, v1Amb.ambientLevel)
        assertTrue(v1Amb.voiceFocus)

        val v1Off = SonyProtocolParser.parseNcAsmPayload(
            byteArrayOf(0x67, 0x02, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00),
            v1Amb
        )
        assertEquals(NoiseControlMode.OFF, v1Off.noiseMode)

        // Unrelated subtype must not overwrite noise state
        val ignored = SonyProtocolParser.parseNcAsmPayload(
            byteArrayOf(0x69, 0x10, 0x01, 0x00, 0x00, 0x00, 0x00),
            v2AmbientPush
        )
        assertEquals(v2AmbientPush, ignored)
    }

    @Test
    fun parseProtocolInfoAndBatteryAndOpcode13CollisionGuard() {
        assertNull(SonyProtocolParser.parseProtocolInfoPayload(byteArrayOf(0x01, 0x00)))
        assertNull(SonyProtocolParser.parseProtocolInfoPayload(byteArrayOf(0x05, 0x00, 0x01)))
        assertEquals(1, SonyProtocolParser.parseProtocolInfoPayload(byteArrayOf(0x01, 0x00, 0x01)))
        assertEquals(2, SonyProtocolParser.parseProtocolInfoPayload(ByteArray(8) { if (it == 0) 0x01 else 0x00 }))

        // V2 dual battery push 0x25 with 60%/61% (escaped bytes when framed) and offline right bud (255)
        val v2Base = HeadsetState(protocolVersion = 2)
        val dualUpdated = SonyProtocolParser.parseBatteryPayload(
            byteArrayOf(SonyProtocolParser.CMD_NTFY_BATTERY, 0x09, 0x3C, 0x01, 0x3D, 0x00),
            v2Base
        )
        assertEquals(60, dualUpdated.leftBattery.level)
        assertTrue(dualUpdated.leftBattery.isCharging)
        assertEquals(61, dualUpdated.rightBattery.level)
        assertFalse(dualUpdated.rightBattery.isCharging)
        assertEquals(DeviceType.TWS, dualUpdated.deviceType)

        val oneBudRemoved = SonyProtocolParser.parseBatteryPayload(
            byteArrayOf(SonyProtocolParser.CMD_RET_BATTERY, 0x09, 0x3E, 0x00, 0xFF.toByte(), 0x00),
            dualUpdated
        )
        assertEquals(62, oneBudRemoved.leftBattery.level)
        assertEquals(-1, oneBudRemoved.rightBattery.level)

        val caseUpdated = SonyProtocolParser.parseBatteryPayload(
            byteArrayOf(SonyProtocolParser.CMD_NTFY_BATTERY, 0x0A, 0x55, 0x01),
            oneBudRemoved
        )
        assertEquals(85, caseUpdated.caseBattery.level)
        assertTrue(caseUpdated.caseBattery.isCharging)

        // CRITICAL: Opcode 0x13 in V2 (e.g. CMD_COMMON_RET_STATUS_V2 audio codec = LDAC)
        // MUST NEVER be parsed as V1 battery notification!
        val v2CodecPacket0x13 = byteArrayOf(0x13, 0x02, 0x10)
        val afterGuard = SonyProtocolParser.parseBatteryPayload(v2CodecPacket0x13, caseUpdated)
        assertEquals(caseUpdated, afterGuard)

        // Same opcode 0x13 when protocolVersion == 1 parses as V1 battery
        val v1Base = HeadsetState(protocolVersion = 1, deviceName = "WH-1000XM4", deviceType = DeviceType.OVER_EAR)
        val v1BattUpdated = SonyProtocolParser.parseBatteryPayload(
            byteArrayOf(0x13, 0x00, 0x50, 0x00),
            v1Base
        )
        assertEquals(80, v1BattUpdated.singleBattery.level)
    }

    @Test
    fun deviceInfoSupportFunctionsCodecAndModelPriority() {
        val renamedBase = HeadsetState(deviceName = "Mis Audifonos", protocolVersion = 2)
        val modelBytes = "WH-1000XM4".toByteArray(Charsets.US_ASCII)
        val withHw = SonyProtocolParser.parseDeviceInfoPayload(
            byteArrayOf(0x05, 0x01, modelBytes.size.toByte()) + modelBytes,
            renamedBase
        )
        assertEquals("WH-1000XM4", withHw.hardwareModelName)
        assertEquals(DeviceType.OVER_EAR, withHw.deviceType)
        assertEquals(SonyModelFamily.WH_1000XM4, withHw.modelFamily)

        val fwBytes = "2.5.0".toByteArray(Charsets.US_ASCII)
        val withFw = SonyProtocolParser.parseDeviceInfoPayload(
            byteArrayOf(0x05, 0x02, fwBytes.size.toByte()) + fwBytes,
            withHw
        )
        assertEquals("2.5.0", withFw.firmwareVersion)

        // Support functions on unknown V2 device without NC/ASM -> effectiveHasAnc = false
        val genericNoAnc = SonyProtocolParser.parseSupportFunctionPayload(
            byteArrayOf(0x07, 0x00, 0x02, 0x11, 0x01, 0x31, 0x01),
            HeadsetState(deviceName = "Unknown Sony", protocolVersion = 2)
        )
        assertFalse(genericNoAnc.effectiveHasAnc)

        // Adding NC/ASM support function (0x61..0x6D) dynamically enables ANC
        val genericWithAnc = SonyProtocolParser.parseSupportFunctionPayload(
            byteArrayOf(0x07, 0x00, 0x01, 0x68, 0x01),
            genericNoAnc
        )
        assertTrue(genericWithAnc.effectiveHasAnc)

        // V1 must ignore 0x07 support function parser
        val v1Unchanged = SonyProtocolParser.parseSupportFunctionPayload(
            byteArrayOf(0x07, 0x00, 0x01, 0x11, 0x01),
            HeadsetState(deviceName = "WH-1000XM3", protocolVersion = 1)
        )
        assertTrue(v1Unchanged.supportFunctions.isEmpty())

        // Codecs V2 & V1
        val v2Ldac = SonyProtocolParser.parseCodecPayload(byteArrayOf(0x13, 0x02, 0x10), withFw)
        assertEquals("LDAC", v2Ldac.codecName)
        val v2Aac = SonyProtocolParser.parseCodecPayload(byteArrayOf(0x15, 0x02, 0x02), v2Ldac)
        assertEquals("AAC", v2Aac.codecName)

        val v1State = HeadsetState(deviceName = "WH-1000XM3", protocolVersion = 1)
        assertEquals("SBC", SonyProtocolParser.parseCodecPayload(byteArrayOf(0x19, 0x00, 0x01), v1State).codecName)
        assertEquals("LC3", SonyProtocolParser.parseCodecPayload(byteArrayOf(0x1B, 0x00, 0x30), v1State).codecName)
        assertEquals("", SonyProtocolParser.parseCodecPayload(byteArrayOf(0x13, 0x02, 0x10), v1State).codecName)

        // Capa 1 (hardwareModelName) beats stale cachedModelFamily & renamed deviceName
        val staleCacheRenamed = HeadsetState(
            deviceName = "Mis Audifonos",
            cachedModelFamily = SonyModelFamily.WF_1000XM4,
            deviceType = DeviceType.TWS,
            protocolVersion = 2
        )
        assertEquals(SonyModelFamily.WF_1000XM4, staleCacheRenamed.modelFamily)

        val whXm5Bytes = "WH-1000XM5".toByteArray(Charsets.US_ASCII)
        val correctedByFirmware = SonyProtocolParser.parseDeviceInfoPayload(
            byteArrayOf(0x05, 0x01, whXm5Bytes.size.toByte()) + whXm5Bytes,
            staleCacheRenamed
        )
        assertEquals(SonyModelFamily.WH_1000XM5, correctedByFirmware.modelFamily)
        assertEquals(DeviceType.OVER_EAR, correctedByFirmware.deviceType)

        // Capa 3 (forcedModelFamily) beats everything including hardwareModelName, cachedModelFamily, and supportsAncOverride
        val forcedNoAnc = genericWithAnc.copy(
            hardwareModelName = "WF-1000XM6",
            cachedModelFamily = SonyModelFamily.WF_1000XM6,
            forcedModelFamily = SonyModelFamily.WH_CH520
        )
        assertEquals(SonyModelFamily.WH_CH520, forcedNoAnc.modelFamily)
        assertFalse(forcedNoAnc.effectiveHasAnc)
    }

    @Test
    fun fase2Equalizer_5BandV1V2And10BandXm6_RoundTrip() {
        val v2State = HeadsetState(hardwareModelName = "WF-1000XM5", protocolVersion = 2)
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x56, 0x00),
            SonyProtocolParser.buildEqualizerGetPayload(v2State)
        )
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x58, 0x00, 0x11, 0x00),
            SonyProtocolParser.buildEqualizerSetPresetPayload(v2State, EqPreset.EXCITED.id)
        )

        val v2Custom = SonyProtocolParser.buildEqualizerSetCustomPayload(
            v2State,
            clearBass = -2,
            bands = listOf(0, 3, -4, 5, 1)
        )
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x58, 0x00, 0xA0.toByte(), 0x06, 0x08, 0x0A, 0x0D, 0x06, 0x0F, 0x0B),
            v2Custom
        )

        val parsedV2 = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0x57, 0x00, 0xA0.toByte(), 0x06, 0x08, 0x0A, 0x0D, 0x06, 0x0F, 0x0B),
            v2State
        )
        assertTrue(parsedV2.eqState.isLoaded)
        assertFalse(parsedV2.eqState.isTenBand)
        assertEquals(EqPreset.CUSTOM.id, parsedV2.eqState.presetId)
        assertEquals(-2, parsedV2.eqState.clearBass)
        assertEquals(listOf(0, 3, -4, 5, 1), parsedV2.eqState.bands)

        // V1 legacy uses inquired=0x01 and UNSPECIFIED(0xFF) for custom curves
        val v1State = HeadsetState(hardwareModelName = "WH-1000XM3", protocolVersion = 1)
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x56, 0x01),
            SonyProtocolParser.buildEqualizerGetPayload(v1State)
        )
        val v1Custom = SonyProtocolParser.buildEqualizerSetCustomPayload(
            v1State,
            clearBass = 4,
            bands = listOf(1, 2, 0, -1, 3)
        )
        assertEquals(0x01, v1Custom[1].toInt() and 0xFF)
        assertEquals(0xFF, v1Custom[2].toInt() and 0xFF)
        assertEquals(14, v1Custom[4].toInt() and 0xFF)

        // WH-1000XM6 10-band raw (0..12, inquired=0x04, no Clear Bass byte)
        val xm6State = HeadsetState(
            hardwareModelName = "WH-1000XM6",
            protocolVersion = 2,
            supportFunctions = setOf(0x57)
        )
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x56, 0x04),
            SonyProtocolParser.buildEqualizerGetPayload(xm6State)
        )
        val tenBands = listOf(6, 7, 8, 5, 4, 9, 10, 6, 3, 12)
        val xm6Custom = SonyProtocolParser.buildEqualizerSetCustomPayload(xm6State, clearBass = 5, bands = tenBands)
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x58, 0x04, 0xA0.toByte(), 10, 6, 7, 8, 5, 4, 9, 10, 6, 3, 12),
            xm6Custom
        )
        val parsedXm6 = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0x57, 0x04, 0xA0.toByte(), 10, 6, 7, 8, 5, 4, 9, 10, 6, 3, 12),
            xm6State
        )
        assertTrue(parsedXm6.eqState.isTenBand)
        assertEquals(0, parsedXm6.eqState.clearBass)
        assertEquals(tenBands, parsedXm6.eqState.bands)
    }

    @Test
    fun fase2Audio_DseeAndConnectionPriority_IncludingLe4Byte() {
        org.junit.Assert.assertArrayEquals(byteArrayOf(0xE6.toByte(), 0x01), SonyProtocolParser.buildDseeGetPayload())
        org.junit.Assert.assertArrayEquals(byteArrayOf(0xE8.toByte(), 0x01, 0x01), SonyProtocolParser.buildDseeSetPayload(true))
        org.junit.Assert.assertArrayEquals(byteArrayOf(0xE8.toByte(), 0x01, 0x00), SonyProtocolParser.buildDseeSetPayload(false))

        val base = HeadsetState(protocolVersion = 2)
        val withDsee = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xE7.toByte(), 0x01, 0x01),
            base
        )
        assertEquals(true, withDsee.dseeEnabled)

        val dseeOff = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xE9.toByte(), 0x01, 0x00),
            withDsee
        )
        assertEquals(false, dseeOff.dseeEnabled)

        // Classic 3-byte SET vs Classic+LE Audio 4-byte SET
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xE8.toByte(), 0x00, 0x01),
            SonyProtocolParser.buildConnectionPrioritySetPayload(0x00, ConnectionPriority.STABLE_CONNECTION)
        )
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xE8.toByte(), 0x05, 0x02, 0x00),
            SonyProtocolParser.buildConnectionPrioritySetPayload(0x05, ConnectionPriority.LOW_LATENCY)
        )

        val withPriority = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xE7.toByte(), 0x05, 0x02, 0x00),
            base
        )
        assertEquals(ConnectionPriority.LOW_LATENCY, withPriority.connectionPriority)
    }

    @Test
    fun fase2Power_AutoPowerOffAndRemotePowerOffV2Only() {
        org.junit.Assert.assertArrayEquals(byteArrayOf(0x26, 0x04), SonyProtocolParser.buildAutoPowerOffGetPayload(false))
        org.junit.Assert.assertArrayEquals(byteArrayOf(0x26, 0x05), SonyProtocolParser.buildAutoPowerOffGetPayload(true))
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x28, 0x05, 0x10, 0x10),
            SonyProtocolParser.buildAutoPowerOffSetPayload(true, AutoPowerOffOption.WHEN_REMOVED)
        )
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x28, 0x04, 0x11, 0x11),
            SonyProtocolParser.buildAutoPowerOffSetPayload(false, AutoPowerOffOption.DISABLE)
        )

        val parsed = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0x27, 0x05, 0x10, 0x10),
            HeadsetState(protocolVersion = 2)
        )
        assertEquals(AutoPowerOffOption.WHEN_REMOVED, parsed.autoPowerOff)

        // Strict V2 remote power off frame (never 0x22!)
        val powerOff = SonyProtocolParser.buildRemotePowerOffV2Payload()
        org.junit.Assert.assertArrayEquals(byteArrayOf(0x24, 0x03, 0x01), powerOff)
        assertFalse((powerOff[0].toInt() and 0xFF) == 0x22)
    }

    @Test
    fun fase2System_SpeakToChatWearTouchAndGeneralSettings() {
        // V1 Speak-to-Chat (0x05): non-inverted 0x01=ON
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xF8.toByte(), 0x05, 0x01),
            SonyProtocolParser.buildSpeakToChatSetPayload(0x05, enabled = true, protocolVersion = 1)
        )
        val v1Parsed = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xF7.toByte(), 0x05, 0x01),
            HeadsetState(protocolVersion = 1)
        )
        assertEquals(true, v1Parsed.speakToChatEnabled)

        // V2 Speak-to-Chat (0x02 / 0x0C): inverted 0x00=ON
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xF8.toByte(), 0x0C, 0x00, 0x01),
            SonyProtocolParser.buildSpeakToChatSetPayload(0x0C, enabled = true, protocolVersion = 2)
        )
        val v2Parsed = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xF7.toByte(), 0x02, 0x00, 0x01),
            HeadsetState(protocolVersion = 2)
        )
        assertEquals(true, v2Parsed.speakToChatEnabled)

        // Ext Mode 1 (3B param) vs Ext Mode 2 (2B param)
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xFC.toByte(), 0x02, 0x01, 0x00, 0x00),
            SonyProtocolParser.buildSpeakToChatExtSetPayload(
                inquiredType = 0x02,
                extMode = 1,
                sensitivity = SpeakToChatSensitivity.HIGH,
                returnTime = SpeakToChatReturnTime.FAST,
                focusOnVoice = true
            )
        )
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xFC.toByte(), 0x0C, 0x02, 0x03),
            SonyProtocolParser.buildSpeakToChatExtSetPayload(
                inquiredType = 0x0C,
                extMode = 2,
                sensitivity = SpeakToChatSensitivity.LOW,
                returnTime = SpeakToChatReturnTime.NONE,
                focusOnVoice = true
            )
        )
        val extParsed = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xFB.toByte(), 0x02, 0x02, 0x01, 0x02),
            HeadsetState(protocolVersion = 2)
        )
        assertEquals(SpeakToChatSensitivity.LOW, extParsed.speakToChatSensitivity)
        assertFalse(extParsed.speakToChatFocusOnVoice)
        assertEquals(SpeakToChatReturnTime.SLOW, extParsed.speakToChatReturnTime)

        // Pause when removed (inverted 0x00=ON, 0x01=OFF)
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xF8.toByte(), 0x01, 0x00),
            SonyProtocolParser.buildPauseWhenRemovedSetPayload(true)
        )
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xF8.toByte(), 0x01, 0x01),
            SonyProtocolParser.buildPauseWhenRemovedSetPayload(false)
        )
        val wearParsed = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xF7.toByte(), 0x01, 0x00),
            HeadsetState(protocolVersion = 2)
        )
        assertEquals(true, wearParsed.pauseWhenRemoved)

        // Touch assignment (including NO_FUNCTION = 0xFF)
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xF8.toByte(), 0x03, 0x02, 0xFF.toByte(), 0x20),
            SonyProtocolParser.buildTouchAssignmentSetPayload(
                0x03,
                listOf(TouchPresetOption.NO_FUNCTION, TouchPresetOption.PLAYBACK_CONTROL)
            )
        )
        val touchParsed = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xF7.toByte(), 0x0E, 0x02, 0x00, 0xFF.toByte()),
            HeadsetState(protocolVersion = 2)
        )
        assertEquals(TouchPresetOption.AMBIENT_SOUND_CONTROL, touchParsed.touchLeftPreset)
        assertEquals(TouchPresetOption.NO_FUNCTION, touchParsed.touchRightPreset)

        // GS Capability confirmation + boolean SET/RET
        val capConfirmed = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xD1.toByte(), 0xD3.toByte(), 0x00),
            HeadsetState(protocolVersion = 2, supportFunctions = setOf(0xD3))
        )
        assertTrue(0xD3 in capConfirmed.gsBooleanConfirmed)
        assertTrue(capConfirmed.capabilities.supportsTouchPanelToggle)

        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0xD8.toByte(), 0xD3.toByte(), 0x00, 0x01),
            SonyProtocolParser.buildGsBooleanSetPayload(SonyProtocolParser.GS_TYPE_TOUCH_PANEL, false)
        )
        val mpGs = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0xD7.toByte(), 0xD2.toByte(), 0x00, 0x00),
            capConfirmed
        )
        assertEquals(true, mpGs.multipointEnabled)
    }

    @Test
    fun fase2Table2_SupportPeriClassicAndCodAndVoiceGuidance() {
        val base = HeadsetState(hardwareModelName = "WH-1000XM5", protocolVersion = 2)
        val withT2 = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR_NO2,
            byteArrayOf(0x07, 0x00, 3, 0x32, 0x01, 0x42, 0x01, 0x45, 0x01),
            base
        )
        assertTrue(withT2.capabilities.multipointViaPeri)
        assertEquals(0x02, withT2.capabilities.multipointPeriInquiredType)
        assertTrue(withT2.capabilities.supportsVoiceGuidance)
        assertEquals(0x01, withT2.capabilities.voiceGuidanceInquiredType)
        assertTrue(withT2.capabilities.supportsVoiceGuidanceLanguage)
        assertTrue(withT2.capabilities.supportsVoiceGuidanceVolume)

        // PERI Status (0x33) & Param PAIRING_COD (0x37 0x02)
        val mpStatus = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR_NO2,
            byteArrayOf(0x33, 0x02, 0x00, 0x00),
            withT2
        )
        assertEquals(true, mpStatus.multipointEnabled)

        val mac1 = "AA:BB:CC:DD:EE:01".toByteArray(Charsets.US_ASCII)
        val name1 = "Pixel 9".toByteArray(Charsets.UTF_8)
        val mac2 = "11:22:33:44:55:66".toByteArray(Charsets.US_ASCII)
        val name2 = "ThinkPad".toByteArray(Charsets.UTF_8)
        val codPayload = byteArrayOf(0x37, 0x02, 0x02) +
            mac1 + byteArrayOf(0x01, 0x24, 0x04, 0x04, name1.size.toByte()) + name1 +
            mac2 + byteArrayOf(0x02, 0x0C, 0x01, 0x02, name2.size.toByte()) + name2 +
            byteArrayOf(0x02)

        val mpDevices = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR_NO2,
            codPayload,
            mpStatus
        )
        assertEquals(2, mpDevices.pairedDevices.size)
        assertEquals("Pixel 9", mpDevices.pairedDevices[0].friendlyName)
        assertTrue(mpDevices.pairedDevices[0].isConnected)
        assertFalse(mpDevices.pairedDevices[0].isActiveSource)
        assertEquals("ThinkPad", mpDevices.pairedDevices[1].friendlyName)
        assertTrue(mpDevices.pairedDevices[1].isConnected)
        assertTrue(mpDevices.pairedDevices[1].isActiveSource)

        // PAIRING_CLASSIC (0x00, no CoD) + trailing 1-based playbackRight
        val classicPayload = byteArrayOf(0x37, 0x00, 0x01) +
            mac1 + byteArrayOf(0x01, name1.size.toByte()) + name1 +
            byteArrayOf(0x01)
        val mpClassic = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR_NO2,
            classicPayload,
            mpStatus
        )
        assertEquals(1, mpClassic.pairedDevices.size)
        assertTrue(mpClassic.pairedDevices[0].isActiveSource)

        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x3C, 0x02, 0x01) + mac1,
            SonyProtocolParser.buildPeriConnectDisconnectPayload(0x02, connect = true, macAddress = "aa:bb:cc:dd:ee:01")
        )
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x3C, 0x01) + mac2,
            SonyProtocolParser.buildPeriSourceSwitchPayload("11:22:33:44:55:66")
        )

        // Voice Guidance MTK 0x01: RET returns 4 bytes [cmd, 0x01, val, lang], SET sends 3 bytes
        val vgParsed = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR_NO2,
            byteArrayOf(0x47, 0x01, 0x00, 0x04),
            withT2
        )
        assertEquals(true, vgParsed.voiceGuidanceEnabled)
        assertEquals(VoiceGuidanceLanguageOption.SPANISH, vgParsed.voiceGuidanceLanguage)

        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x48, 0x01, 0x01),
            SonyProtocolParser.buildVoiceGuidanceSetParamPayload(0x01, enabled = false, language = VoiceGuidanceLanguageOption.SPANISH)
        )
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x48, 0x02, 0x00, 0x04),
            SonyProtocolParser.buildVoiceGuidanceSetParamPayload(0x02, enabled = true, language = VoiceGuidanceLanguageOption.SPANISH)
        )

        // Voice Guidance Volume signed int8 (-2..+2)
        val volSet = SonyProtocolParser.buildVoiceGuidanceVolumeSetPayload(0x20, volume = -2, playFeedbackSound = true)
        org.junit.Assert.assertArrayEquals(byteArrayOf(0x48, 0x20, 0xFE.toByte(), 0x00), volSet)
        val volParsed = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR_NO2,
            byteArrayOf(0x47, 0x20, 0xFE.toByte()),
            vgParsed
        )
        assertEquals(-2, volParsed.voiceGuidanceVolume)
    }

    @Test
    fun fase2WindReduction_WfXm4EightByteAnd7ByteInvariants() {
        // Strict 7-byte V2 NC/ASM invariant
        val sevenByte = SonyProtocolParser.buildSetNcAsmPayload(NoiseControlMode.ANC, ambientLevel = 20, voiceFocus = false)
        assertEquals(7, sevenByte.size)
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x68, 0x17, 0x01, 0x01, 0x00, 0x00, 0x00),
            sevenByte
        )

        // WF-1000XM4 8-byte Wind reduction frame (0x15)
        val wfXm4 = HeadsetState(
            hardwareModelName = "WF-1000XM4",
            protocolVersion = 2,
            supportFunctions = setOf(0x68)
        )
        assertTrue(wfXm4.capabilities.supportsWindNoiseReduction)

        val whXm5 = HeadsetState(hardwareModelName = "WH-1000XM5", protocolVersion = 2, supportFunctions = setOf(0x64))
        assertFalse(whXm5.capabilities.supportsWindNoiseReduction)

        val eightByteWind = SonyProtocolParser.buildSetNcAsmWithWindPayload(
            mode = NoiseControlMode.ANC,
            ambientLevel = 20,
            voiceFocus = false,
            windReduction = true
        )
        assertEquals(8, eightByteWind.size)
        org.junit.Assert.assertArrayEquals(
            byteArrayOf(0x68, 0x15, 0x01, 0x01, 0x00, 0x01, 0x00, 0x00),
            eightByteWind
        )

        val parsedWind = SonyProtocolParser.parseIncomingPayload(
            SonyProtocolParser.DATA_TYPE_MDR,
            byteArrayOf(0x67, 0x15, 0x01, 0x01, 0x00, 0x01, 0x00, 0x00),
            wfXm4
        )
        assertEquals(NoiseControlMode.ANC, parsedWind.noiseMode)
        assertTrue(parsedWind.windNoiseReduction)
    }
}
