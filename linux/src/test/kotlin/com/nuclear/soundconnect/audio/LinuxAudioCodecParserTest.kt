package com.nuclear.soundconnect.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxAudioCodecParserTest {

    @Test
    fun `parses PipeWire pw-dump JSON for LDAC 96kHz 24-bit running stream`() {
        val pwDumpFixture = """
            [
              {
                "id": 44,
                "type": "PipeWire:Interface:Device",
                "info": {
                  "props": {
                    "device.api": "bluez5",
                    "api.bluez5.address": "A4:1D:10:AB:CD:EF",
                    "device.name": "bluez_card.A4_1D_10_AB_CD_EF"
                  },
                  "params": {
                    "Profile": [
                      { "index": 2, "name": "a2dp-sink" }
                    ]
                  }
                }
              },
              {
                "id": 65,
                "type": "PipeWire:Interface:Node",
                "info": {
                  "state": "running",
                  "props": {
                    "node.name": "bluez_output.A4_1D_10_AB_CD_EF.1",
                    "media.class": "Audio/Sink",
                    "device.api": "bluez5",
                    "api.bluez5.address": "A4:1D:10:AB:CD:EF",
                    "api.bluez5.codec": "ldac",
                    "api.bluez5.profile": "a2dp-sink",
                    "audio.rate": 96000,
                    "audio.channels": 2,
                    "audio.format": "S24_32LE"
                  }
                }
              }
            ]
        """.trimIndent()

        val state = LinuxAudioCodecMonitor.parsePwDump(pwDumpFixture, "A4:1D:10:AB:CD:EF")
        assertNotNull(state)
        state!!
        assertTrue(state.isMediaPlaying)
        assertFalse(state.isCallActive)
        assertEquals("LDAC", state.hud.codecName)
        assertEquals(96_000, state.hud.sampleRateHz)
        assertEquals(24, state.hud.bitsPerSample)
        assertEquals(2, state.hud.channelCount)
        assertTrue(state.hud.isA2dpPlaying)
        assertEquals("LDAC • 96 kHz • 24-bit", state.hud.formatShortBadge())
    }

    @Test
    fun `parses PulseAudio pactl outputs and detects HFP call mode`() {
        val cardsFixture = """
            Card #12
            	Name: bluez_card.A4_1D_10_AB_CD_EF
            	Driver: module-bluez5-device.c
            	Properties:
            		api.bluez5.address = "A4:1D:10:AB:CD:EF"
            		bluetooth.codec = "msbc"
            	Active Profile: headset-head-unit
        """.trimIndent()

        val sinksFixture = """
            Sink #88
            	State: RUNNING
            	Name: bluez_output.A4_1D_10_AB_CD_EF.headset_head_unit
            	Sample Specification: s16le 1ch 16000Hz
            	Properties:
            		api.bluez5.address = "A4:1D:10:AB:CD:EF"
            		api.bluez5.profile = "headset-head-unit"
        """.trimIndent()

        val sourceOutputsFixture = """
            Source Output #301
            	Properties:
            		application.name = "Zoom"
            		target.object = "bluez_input.A4_1D_10_AB_CD_EF.0"
        """.trimIndent()

        val state = LinuxAudioCodecMonitor.parsePactlOutputs(
            cardsOut = cardsFixture,
            sinksOut = sinksFixture,
            sourceOutputsOut = sourceOutputsFixture,
            targetMac = "A4:1D:10:AB:CD:EF"
        )
        assertNotNull(state)
        state!!
        assertTrue(state.isCallActive)
        assertTrue(state.hud.isHfpCallActive)
        assertEquals(16_000, state.hud.sampleRateHz)
        assertEquals(16, state.hud.bitsPerSample)
        assertEquals("mSBC (HFP)", state.hud.codecName)
    }
}
