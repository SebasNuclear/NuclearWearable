package com.nuclear.soundconnect.automation

import com.nuclear.soundconnect.audio.LinuxAudioCodecMonitor
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyModelFamily
import com.nuclear.soundconnect.util.CommandResult
import com.nuclear.soundconnect.util.CommandRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAutomationEngineTest {

    private object NoopRunner : CommandRunner {
        override fun run(command: List<String>, timeoutMs: Long) = CommandResult(-1, "")
        override fun runWithStdin(command: List<String>, stdinText: String, timeoutMs: Long) = false
        override fun commandExists(executable: String) = false
    }

    @Test
    fun `isTimeInWindow handles midnight wraparound and daytime intervals`() {
        // Overnight 22:00 -> 07:00
        assertTrue(LocalAutomationEngine.isTimeInWindow(23 * 60 + 30, 22, 0, 7, 0))
        assertTrue(LocalAutomationEngine.isTimeInWindow(3 * 60, 22, 0, 7, 0))
        assertFalse(LocalAutomationEngine.isTimeInWindow(7 * 60, 22, 0, 7, 0))
        assertFalse(LocalAutomationEngine.isTimeInWindow(14 * 60, 22, 0, 7, 0))

        // Same day 09:00 -> 17:00
        assertTrue(LocalAutomationEngine.isTimeInWindow(12 * 60, 9, 0, 17, 0))
        assertFalse(LocalAutomationEngine.isTimeInWindow(8 * 60 + 59, 9, 0, 17, 0))
    }

    @Test
    fun `call rule switches ANC to Ambient+VoiceFocus during call and restores ANC after hangup`() {
        var currentTime = 100_000L
        var state = HeadsetState(
            deviceName = "WF-1000XM6",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            isConnected = true,
            deviceType = DeviceType.TWS,
            forcedModelFamily = SonyModelFamily.WF_1000XM6,
            noiseMode = NoiseControlMode.ANC,
            ambientLevel = 12,
            voiceFocus = false
        )
        val appliedCalls = mutableListOf<Pair<NoiseControlMode, String>>()

        val engine = LocalAutomationEngine(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            codecMonitor = LinuxAudioCodecMonitor(NoopRunner),
            stateProvider = { state },
            applyNoiseControl = { mode, level, focus, reason ->
                appliedCalls += mode to reason
                state = state.copy(noiseMode = mode, ambientLevel = level, voiceFocus = focus)
            },
            onPersistConfig = {},
            initialConfig = LocalAutomationConfig(masterEnabled = true, callRuleEnabled = true, mediaRuleEnabled = true),
            clockMillisProvider = { currentTime },
            currentMinuteOfDayProvider = { 12 * 60 }
        )

        // Incoming call starts
        engine.processAudioSignals(isCallNow = true, isMediaPlayingNow = false)
        assertEquals(NoiseControlMode.AMBIENT, state.noiseMode)
        assertTrue(state.voiceFocus)

        // Call ends -> restores original ANC + level=12 + voiceFocus=false
        currentTime += 5_000L
        engine.processAudioSignals(isCallNow = false, isMediaPlayingNow = false)
        assertEquals(NoiseControlMode.ANC, state.noiseMode)
        assertEquals(12, state.ambientLevel)
        assertFalse(state.voiceFocus)
        assertEquals(2, appliedCalls.size)
    }

    @Test
    fun `media rule respects 30s manual user override cooldown`() {
        var currentTime = 200_000L
        var state = HeadsetState(
            deviceName = "WH-1000XM5",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            isConnected = true,
            forcedModelFamily = SonyModelFamily.WH_1000XM5,
            noiseMode = NoiseControlMode.OFF
        )
        var triggerCount = 0

        val engine = LocalAutomationEngine(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            codecMonitor = LinuxAudioCodecMonitor(NoopRunner),
            stateProvider = { state },
            applyNoiseControl = { mode, _, _, _ ->
                triggerCount++
                state = state.copy(noiseMode = mode)
            },
            onPersistConfig = {},
            initialConfig = LocalAutomationConfig(
                masterEnabled = true,
                callRuleEnabled = false,
                mediaRuleEnabled = true,
                mediaTargetMode = NoiseControlMode.ANC
            ),
            clockMillisProvider = { currentTime },
            currentMinuteOfDayProvider = { 12 * 60 }
        )

        engine.markUserManualModeChange()
        engine.processAudioSignals(isCallNow = false, isMediaPlayingNow = true)
        assertEquals(0, triggerCount) // Blocked by cooldown

        // Reset edge and advance past 30s cooldown
        engine.processAudioSignals(isCallNow = false, isMediaPlayingNow = false)
        currentTime += 35_000L
        engine.processAudioSignals(isCallNow = false, isMediaPlayingNow = true)
        assertEquals(1, triggerCount)
        assertEquals(NoiseControlMode.ANC, state.noiseMode)
    }
}
