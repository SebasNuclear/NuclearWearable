package com.nuclear.soundconnect

import com.nuclear.soundconnect.audio.LiveCodecHudInfo
import com.nuclear.soundconnect.audio.WindowsAudioMonitor
import com.nuclear.soundconnect.automation.LocalAutomationConfig
import com.nuclear.soundconnect.automation.LocalAutomationEngine
import com.nuclear.soundconnect.backup.NuclearBackupBundle
import com.nuclear.soundconnect.backup.ProfileBackupManager
import com.nuclear.soundconnect.bluetooth.AutoPowerOffOption
import com.nuclear.soundconnect.bluetooth.ConnectionPriority
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3ModulesTest {

    @Test
    fun `backup bundle exports and restores with full Android schema parity`() {
        val state = HeadsetState(
            deviceName = "WH-1000XM6",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            noiseMode = NoiseControlMode.AMBIENT,
            ambientLevel = 14,
            voiceFocus = true,
            windNoiseReduction = false,
            forcedModelFamily = SonyModelFamily.WH_1000XM6,
            eqState = EqState(
                isSupported = true,
                isLoaded = true,
                presetId = 0xA0,
                clearBass = 3,
                bands = listOf(6, 7, 5, 6, 8, 9, 7, 6, 7, 8),
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
            touchPanelEnabled = true
        )
        val auto = LocalAutomationConfig(
            masterEnabled = true,
            callRuleEnabled = true,
            mediaRuleEnabled = true,
            mediaTargetMode = NoiseControlMode.ANC,
            scheduleRuleEnabled = true,
            scheduleStartHour = 23,
            scheduleStartMinute = 30,
            scheduleEndHour = 6,
            scheduleEndMinute = 45,
            scheduleTargetMode = NoiseControlMode.ANC
        )
        val bundle = ProfileBackupManager.createBundleFromState(
            state = state,
            automation = auto,
            languageCode = "es",
            accentColorArgb = 0xFF00E676.toInt()
        )
        val json = ProfileBackupManager.exportJson(bundle)
        val parsed = ProfileBackupManager.parseBackupJson(json)
        assertTrue(parsed.isSuccess)
        val restored = parsed.getOrThrow()

        assertEquals(NuclearBackupBundle.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
        assertEquals("WH-1000XM6", restored.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", restored.deviceAddress)
        assertEquals(SonyModelFamily.WH_1000XM6, restored.forcedModelFamily)
        assertEquals(NoiseControlMode.AMBIENT, restored.noiseMode)
        assertEquals(14, restored.ambientLevel)
        assertTrue(restored.voiceFocus)
        assertEquals(0xA0, restored.eqPresetId)
        assertEquals(3, restored.eqClearBass)
        assertTrue(restored.eqIsTenBand)
        assertEquals(10, restored.eqBands.size)
        assertEquals(true, restored.dseeEnabled)
        assertEquals(ConnectionPriority.SOUND_QUALITY, restored.connectionPriority)
        assertEquals(AutoPowerOffOption.MIN_30, restored.autoPowerOff)
        assertEquals(true, restored.speakToChatEnabled)
        assertEquals(SpeakToChatSensitivity.HIGH, restored.speakToChatSensitivity)
        assertEquals(SpeakToChatReturnTime.FAST, restored.speakToChatReturnTime)
        assertTrue(restored.speakToChatFocusOnVoice)
        assertEquals(true, restored.pauseWhenRemoved)
        assertEquals(TouchPresetOption.AMBIENT_SOUND_CONTROL, restored.touchLeftPreset)
        assertEquals(TouchPresetOption.PLAYBACK_CONTROL, restored.touchRightPreset)
        assertEquals(true, restored.touchPanelEnabled)
        assertTrue(restored.automation.masterEnabled)
        assertEquals(23, restored.automation.scheduleStartHour)
        assertEquals(45, restored.automation.scheduleEndMinute)
        assertEquals("es", restored.languageCode)
        assertEquals(0xFF00E676.toInt(), restored.accentColorArgb)
    }

    @Test
    fun `backup rejects invalid schema version`() {
        val result = ProfileBackupManager.parseBackupJson("""{"schemaVersion": 99}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun `hex filter isolates unknown and error frames`() {
        val entries = listOf(
            HexLogEntry(HexLogEntry.Direction.TX, "3E 0C ...", "ACK"),
            HexLogEntry(HexLogEntry.Direction.RX, "3E 0C ...", "RX cmd=0x99 payload=01"),
            HexLogEntry(HexLogEntry.Direction.ERROR, "", "Socket disconnected")
        )
        assertFalse(ProfileBackupManager.isUnknownOrUnparsedFrame(entries[0]))
        assertTrue(ProfileBackupManager.isUnknownOrUnparsedFrame(entries[1]))
        assertTrue(ProfileBackupManager.isUnknownOrUnparsedFrame(entries[2]))

        val filteredExport = ProfileBackupManager.formatHexMonitorExport(entries, unknownOnly = true)
        assertTrue(filteredExport.contains("2 entries"))
        assertTrue(filteredExport.contains("RX cmd=0x99"))
        assertTrue(filteredExport.contains("Socket disconnected"))
        assertFalse(filteredExport.contains("ACK"))
    }

    @Test
    fun `automation schedule window handles standard and overnight ranges`() {
        // Same-day window 09:00 -> 17:30
        assertTrue(LocalAutomationEngine.isCurrentTimeInWindow(9, 0, 9, 0, 17, 30))
        assertTrue(LocalAutomationEngine.isCurrentTimeInWindow(14, 15, 9, 0, 17, 30))
        assertFalse(LocalAutomationEngine.isCurrentTimeInWindow(17, 30, 9, 0, 17, 30))
        assertFalse(LocalAutomationEngine.isCurrentTimeInWindow(8, 59, 9, 0, 17, 30))

        // Overnight window 22:00 -> 07:00
        assertTrue(LocalAutomationEngine.isCurrentTimeInWindow(23, 15, 22, 0, 7, 0))
        assertTrue(LocalAutomationEngine.isCurrentTimeInWindow(3, 30, 22, 0, 7, 0))
        assertFalse(LocalAutomationEngine.isCurrentTimeInWindow(7, 0, 22, 0, 7, 0))
        assertFalse(LocalAutomationEngine.isCurrentTimeInWindow(15, 0, 22, 0, 7, 0))

        // Equal start/end means disabled
        assertFalse(LocalAutomationEngine.isCurrentTimeInWindow(12, 0, 12, 0, 12, 0))
    }

    @Test
    fun `live codec hud formats badge and queries local CoreAudio safely`() {
        val ldac = LiveCodecHudInfo(codecName = "LDAC", sampleRateHz = 96000, bitsPerSample = 24)
        assertEquals("LDAC · 96 kHz / 24b", ldac.formatShortBadge())

        val aac = LiveCodecHudInfo(codecName = "AAC", sampleRateHz = 44100, bitsPerSample = 16)
        assertEquals("AAC · 44.1 kHz / 16b", aac.formatShortBadge())

        assertEquals("", LiveCodecHudInfo().formatShortBadge())

        val monitor = WindowsAudioMonitor()
        val snapshot = monitor.queryCoreAudioSnapshot("WH-1000XM5")
        assertNotNull(snapshot)
    }
}
