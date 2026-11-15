package com.nuclear.soundconnect.automation

import com.nuclear.soundconnect.audio.LinuxAudioCodecMonitor
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

data class LocalAutomationConfig(
    val masterEnabled: Boolean = false,
    val callRuleEnabled: Boolean = true,
    val mediaRuleEnabled: Boolean = false,
    val mediaTargetMode: NoiseControlMode = NoiseControlMode.ANC,
    val scheduleRuleEnabled: Boolean = false,
    val scheduleStartHour: Int = 22,
    val scheduleStartMinute: Int = 0,
    val scheduleEndHour: Int = 7,
    val scheduleEndMinute: Int = 0,
    val scheduleTargetMode: NoiseControlMode = NoiseControlMode.ANC
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("masterEnabled", masterEnabled)
        put("callRuleEnabled", callRuleEnabled)
        put("mediaRuleEnabled", mediaRuleEnabled)
        put("mediaTargetMode", mediaTargetMode.name)
        put("scheduleRuleEnabled", scheduleRuleEnabled)
        put("scheduleStartHour", scheduleStartHour.coerceIn(0, 23))
        put("scheduleStartMinute", scheduleStartMinute.coerceIn(0, 59))
        put("scheduleEndHour", scheduleEndHour.coerceIn(0, 23))
        put("scheduleEndMinute", scheduleEndMinute.coerceIn(0, 59))
        put("scheduleTargetMode", scheduleTargetMode.name)
    }

    companion object {
        fun fromJson(obj: JSONObject): LocalAutomationConfig = LocalAutomationConfig(
            masterEnabled = obj.optBoolean("masterEnabled", false),
            callRuleEnabled = obj.optBoolean("callRuleEnabled", true),
            mediaRuleEnabled = obj.optBoolean("mediaRuleEnabled", false),
            mediaTargetMode = runCatching {
                NoiseControlMode.valueOf(obj.optString("mediaTargetMode", NoiseControlMode.ANC.name))
            }.getOrDefault(NoiseControlMode.ANC),
            scheduleRuleEnabled = obj.optBoolean("scheduleRuleEnabled", false),
            scheduleStartHour = obj.optInt("scheduleStartHour", 22).coerceIn(0, 23),
            scheduleStartMinute = obj.optInt("scheduleStartMinute", 0).coerceIn(0, 59),
            scheduleEndHour = obj.optInt("scheduleEndHour", 7).coerceIn(0, 23),
            scheduleEndMinute = obj.optInt("scheduleEndMinute", 0).coerceIn(0, 59),
            scheduleTargetMode = runCatching {
                NoiseControlMode.valueOf(obj.optString("scheduleTargetMode", NoiseControlMode.ANC.name))
            }.getOrDefault(NoiseControlMode.ANC)
        )
    }
}

private data class SavedNoiseSnapshot(
    val mode: NoiseControlMode,
    val ambientLevel: Int,
    val voiceFocus: Boolean
)

/**
 * Motor de Automatización Local 100% offline en Linux Desktop (Fase 3.3):
 * Cero telemetría, cero GPS, cero Wi-Fi SSID, cero red.
 * Señales observadas localmente:
 * - Llamada HFP/micrófono activo vía PipeWire / PulseAudio -> conmuta a Ambiente al atender y restaura modo al colgar.
 * - Reproducción multimedia activa (sink BlueZ RUNNING / D-Bus MPRIS Playing) -> aplica modo configurado.
 * - Horario local nocturno HH:mm del reloj del sistema -> aplica modo configurado al entrar en la franja.
 */
class LocalAutomationEngine(
    private val scope: CoroutineScope,
    private val codecMonitor: LinuxAudioCodecMonitor,
    private val stateProvider: () -> HeadsetState,
    private val applyNoiseControl: (NoiseControlMode, Int, Boolean, String) -> Unit,
    private val onPersistConfig: (LocalAutomationConfig) -> Unit,
    initialConfig: LocalAutomationConfig = LocalAutomationConfig(),
    private val clockMillisProvider: () -> Long = { System.currentTimeMillis() },
    private val currentMinuteOfDayProvider: () -> Int = { defaultCurrentMinuteOfDay() }
) {

    companion object {
        private const val USER_OVERRIDE_COOLDOWN_MS = 30_000L
        private const val RULE_TRIGGER_COOLDOWN_MS = 4_000L
        private const val POLL_INTERVAL_MS = 3_000L

        private fun defaultCurrentMinuteOfDay(): Int {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }

        fun isTimeInWindow(
            minuteNow: Int,
            startHour: Int,
            startMin: Int,
            endHour: Int,
            endMin: Int
        ): Boolean {
            val start = startHour.coerceIn(0, 23) * 60 + startMin.coerceIn(0, 59)
            val end = endHour.coerceIn(0, 23) * 60 + endMin.coerceIn(0, 59)
            return if (start <= end) {
                minuteNow in start until end
            } else {
                minuteNow >= start || minuteNow < end
            }
        }
    }

    private val _config = MutableStateFlow(initialConfig)
    val config: StateFlow<LocalAutomationConfig> = _config.asStateFlow()

    private val _lastAppliedRule = MutableStateFlow("")
    val lastAppliedRule: StateFlow<String> = _lastAppliedRule.asStateFlow()

    private val started = AtomicBoolean(false)
    private var monitorJob: Job? = null

    private var preCallSnapshot: SavedNoiseSnapshot? = null
    private var callActive = false
    private var mediaActive = false
    private var scheduleWindowWasActive = false
    private var lastTriggerAt = 0L
    private var lastUserManualChangeAt = 0L

    fun updateConfig(newConfig: LocalAutomationConfig) {
        _config.value = newConfig
        onPersistConfig(newConfig)
        scheduleWindowWasActive = false
        mediaActive = false
        evaluateScheduleTick(forceCheck = true)
    }

    fun markUserManualModeChange() {
        lastUserManualChangeAt = clockMillisProvider()
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        monitorJob = scope.launch {
            while (isActive) {
                runCatching { evaluateAudioSignalsOnce() }
                evaluateScheduleTick()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Evalúa una iteración de señales locales de llamada HFP y reproducción multimedia (también invocable desde tests).
     */
    fun processAudioSignals(isCallNow: Boolean, isMediaPlayingNow: Boolean) {
        val prevCall = callActive
        val state = stateProvider()

        handleCallTransition(isCallNow, state)

        if (!isCallNow && (!prevCall || preCallSnapshot == null)) {
            handleMediaTransition(isMediaPlayingNow, state)
        }
    }

    private fun evaluateAudioSignalsOnce() {
        val state = stateProvider()
        val cfg = _config.value
        val needsAudioPoll = state.isConnected && cfg.masterEnabled && (cfg.callRuleEnabled || cfg.mediaRuleEnabled)
        if (!needsAudioPoll) {
            if (!state.isConnected) {
                callActive = false
                mediaActive = false
                preCallSnapshot = null
            }
            return
        }
        val audioState = codecMonitor.inspectSystemAudio(
            targetMac = state.deviceAddress,
            sonyReportedCodec = state.codecName,
            checkMpris = cfg.mediaRuleEnabled
        )
        processAudioSignals(
            isCallNow = audioState.isCallActive,
            isMediaPlayingNow = audioState.isMediaPlaying
        )
    }

    private fun handleCallTransition(isCallNow: Boolean, state: HeadsetState) {
        val cfg = _config.value
        if (!cfg.masterEnabled || !cfg.callRuleEnabled || !state.isConnected || !state.effectiveHasAnc) {
            if (!isCallNow) {
                callActive = false
                preCallSnapshot = null
            }
            return
        }

        if (isCallNow && !callActive) {
            callActive = true
            preCallSnapshot = SavedNoiseSnapshot(
                mode = state.noiseMode,
                ambientLevel = state.ambientLevel,
                voiceFocus = state.voiceFocus
            )
            if (state.noiseMode != NoiseControlMode.AMBIENT) {
                lastTriggerAt = clockMillisProvider()
                _lastAppliedRule.value = "Call -> Ambient"
                applyNoiseControl(NoiseControlMode.AMBIENT, 20, true, "Call/HFP Active")
            }
        } else if (!isCallNow && callActive) {
            callActive = false
            val snap = preCallSnapshot
            preCallSnapshot = null
            if (snap != null && snap.mode != state.noiseMode) {
                lastTriggerAt = clockMillisProvider()
                _lastAppliedRule.value = "Call End -> ${snap.mode.name}"
                applyNoiseControl(snap.mode, snap.ambientLevel, snap.voiceFocus, "Call End Restore")
            }
        }
    }

    private fun handleMediaTransition(isPlayingNow: Boolean, state: HeadsetState) {
        val cfg = _config.value
        if (!cfg.masterEnabled || !cfg.mediaRuleEnabled || !state.isConnected || !state.effectiveHasAnc) {
            mediaActive = false
            return
        }
        val now = clockMillisProvider()
        if (isPlayingNow && !mediaActive) {
            mediaActive = true
            if (now - lastUserManualChangeAt < USER_OVERRIDE_COOLDOWN_MS) return
            if (now - lastTriggerAt < RULE_TRIGGER_COOLDOWN_MS) return
            if (state.noiseMode != cfg.mediaTargetMode) {
                lastTriggerAt = now
                _lastAppliedRule.value = "Media -> ${cfg.mediaTargetMode.name}"
                applyNoiseControl(
                    cfg.mediaTargetMode,
                    state.ambientLevel,
                    state.voiceFocus,
                    "Media Playback"
                )
            }
        } else if (!isPlayingNow) {
            mediaActive = false
        }
    }

    fun evaluateScheduleTick(forceCheck: Boolean = false) {
        val cfg = _config.value
        val state = stateProvider()
        if (!cfg.masterEnabled || !cfg.scheduleRuleEnabled || !state.isConnected || !state.effectiveHasAnc) {
            scheduleWindowWasActive = false
            return
        }
        if (callActive) return
        val now = clockMillisProvider()
        if (!forceCheck && now - lastUserManualChangeAt < USER_OVERRIDE_COOLDOWN_MS) return

        val inWindow = isTimeInWindow(
            minuteNow = currentMinuteOfDayProvider(),
            startHour = cfg.scheduleStartHour,
            startMin = cfg.scheduleStartMinute,
            endHour = cfg.scheduleEndHour,
            endMin = cfg.scheduleEndMinute
        )
        if (inWindow && !scheduleWindowWasActive) {
            scheduleWindowWasActive = true
            if (state.noiseMode != cfg.scheduleTargetMode) {
                lastTriggerAt = now
                _lastAppliedRule.value = "Schedule -> ${cfg.scheduleTargetMode.name}"
                applyNoiseControl(
                    cfg.scheduleTargetMode,
                    state.ambientLevel,
                    state.voiceFocus,
                    "Schedule"
                )
            }
        } else if (!inWindow) {
            scheduleWindowWasActive = false
        }
    }
}
