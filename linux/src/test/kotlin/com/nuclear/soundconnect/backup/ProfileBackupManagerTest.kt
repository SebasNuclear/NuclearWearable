package com.nuclear.soundconnect.backup

import com.nuclear.soundconnect.automation.LocalAutomationConfig
import com.nuclear.soundconnect.bluetooth.AutoPowerOffOption
import com.nuclear.soundconnect.bluetooth.ConnectionPriority
import com.nuclear.soundconnect.bluetooth.EqPreset
import com.nuclear.soundconnect.bluetooth.EqState
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.HexLogEntry
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyModelFamily
import com.nuclear.soundconnect.bluetooth.SpeakToChatReturnTime
import com.nuclear.soundconnect.bluetooth.SpeakToChatSensitivity
import com.nuclear.soundconnect.bluetooth.TouchPresetOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBackupManagerTest {

    @Test
    fun `NuclearBackupBundle JSON export and parse round-trip preserves full hardware and automation profile`() {
        val state = HeadsetState(
            deviceName = "WH-1000XM6",
            deviceAddress = "A4:1D:10:11:22:33",
            forcedModelFamily = SonyModelFamily.WH_1000XM6,
            noiseMode = NoiseControlMode.AMBIENT,
            ambientLevel = 14,
            voiceFocus = true,
            eqState = EqState(
                isSupported = true,
                isLoaded = true,
                presetId = EqPreset.CUSTOM.id,
                clearBass = 0,
                bands = listOf(8, 7, 6, 5, 6, 7, 8, 9, 7, 6),
                isTenBand = true
            ),
            dseeEnabled = true,
            connectionPriority = ConnectionPriority.SOUND_QUALITY,
            autoPowerOff = AutoPowerOffOption.MIN_30,
            speakToChatEnabled = true,
            speakToChatSensitivity = SpeakToChatSensitivity.HIGH,
            speakToChatReturnTime = SpeakToChatReturnTime.FAST,
            speakToChatFocusOnVoice = true,
            pauseWhenRemoved = true,
            touchLeftPreset = TouchPresetOption.AMBIENT_SOUND_CONTROL,
            touchRightPreset = TouchPresetOption.PLAYBACK_CONTROL,
            touchPanelEnabled = true,
            accentColor = 0xFF00D2FF.toInt()
        )
        val autoCfg = LocalAutomationConfig(
            masterEnabled = true,
            callRuleEnabled = true,
            mediaRuleEnabled = true,
            mediaTargetMode = NoiseControlMode.ANC,
            scheduleRuleEnabled = true,
            scheduleStartHour = 23,
            scheduleEndHour = 6,
            scheduleTargetMode = NoiseControlMode.ANC
        )

        val bundle = ProfileBackupManager.createBundleFromState(state, autoCfg, "es")
        val json = ProfileBackupManager.exportJson(bundle)
        val restored = ProfileBackupManager.parseBackupJson(json).getOrThrow()

        assertEquals("WH-1000XM6", restored.deviceName)
        assertEquals(SonyModelFamily.WH_1000XM6, restored.forcedModelFamily)
        assertEquals(NoiseControlMode.AMBIENT, restored.noiseMode)
        assertEquals(14, restored.ambientLevel)
        assertTrue(restored.voiceFocus)
        assertTrue(restored.eqIsTenBand)
        assertEquals(listOf(8, 7, 6, 5, 6, 7, 8, 9, 7, 6), restored.eqBands)
        assertEquals(true, restored.dseeEnabled)
        assertEquals(ConnectionPriority.SOUND_QUALITY, restored.connectionPriority)
        assertEquals(AutoPowerOffOption.MIN_30, restored.autoPowerOff)
        assertEquals(SpeakToChatSensitivity.HIGH, restored.speakToChatSensitivity)
        assertEquals(TouchPresetOption.PLAYBACK_CONTROL, restored.touchRightPreset)
        assertTrue(restored.automationConfig.masterEnabled)
        assertEquals(23, restored.automationConfig.scheduleStartHour)
        assertEquals(0xFF00D2FF.toInt(), restored.accentColorArgb)
    }

    @Test
    fun `rejects invalid JSON schema`() {
        assertTrue(ProfileBackupManager.parseBackupJson("{}").isFailure)
        assertTrue(ProfileBackupManager.parseBackupJson("""{"schemaVersion": 99}""").isFailure)
    }

    @Test
    fun `filters unknown or error Hex logs and formats export dump cleanly`() {
        val known = HexLogEntry(HexLogEntry.Direction.RX, "3E 0C ...", "Batería OK")
        val error = HexLogEntry(HexLogEntry.Direction.ERROR, "FF", "Checksum error")
        val unknown = HexLogEntry(HexLogEntry.Direction.RX, "3E 0E ...", "Subtipo desconocido (0xEE)")

        assertFalse(ProfileBackupManager.isUnknownOrUnparsedFrame(known))
        assertTrue(ProfileBackupManager.isUnknownOrUnparsedFrame(error))
        assertTrue(ProfileBackupManager.isUnknownOrUnparsedFrame(unknown))

        val filteredDump = ProfileBackupManager.formatHexMonitorExport(listOf(known, error, unknown), unknownOnly = true)
        assertFalse(filteredDump.contains("Batería OK"))
        assertTrue(filteredDump.contains("Checksum error"))
        assertTrue(filteredDump.contains("Subtipo desconocido"))
    }
}
