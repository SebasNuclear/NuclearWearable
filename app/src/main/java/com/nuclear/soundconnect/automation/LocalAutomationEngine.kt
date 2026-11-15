package com.nuclear.soundconnect.automation

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Motor local de automatización 100% libre de GPS, Wi-Fi SSID y red (Fase 3.3).
 * Dispara transiciones de ANC/Ambient usando únicamente AudioPlaybackCallback,
 * AudioManager.OnModeChangedListener y ventanas horarias del reloj local.
 */
class LocalAutomationEngine(
    context: Context,
    private val prefs: SharedPreferences,
    private val stateProvider: () -> HeadsetState,
    private val applyNoiseControl: (mode: NoiseControlMode, ambientLevel: Int, voiceFocus: Boolean, reason: String) -> Unit
) {
    companion object {
        private const val KEY_AUTOMATION_CONFIG = "local_automation_config_json"
        private const val USER_OVERRIDE_COOLDOWN_MS = 30_000L
        private const val RULE_TRIGGER_COOLDOWN_MS = 4_000L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val started = AtomicBoolean(false)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<LocalAutomationConfig> = _config.asStateFlow()

    @Volatile
    private var lastUserManualChangeAt: Long = 0L

    @Volatile
    private var lastTriggerAt: Long = 0L

    @Volatile
    private var callActive: Boolean = false

    @Volatile
    private var preCallSnapshot: SavedNoiseSnapshot? = null

    @Volatile
    private var mediaPlayingActive: Boolean = false

    @Volatile
    private var scheduleWindowWasActive: Boolean = false

    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        handleAudioModeChanged(mode)
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            val playingMedia = configs.orEmpty().any { cfg ->
                val usage = cfg.audioAttributes.usage
                usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME
            }
            handleMediaPlaybackChanged(playingMedia)
        }
    }

    private fun loadConfig(): LocalAutomationConfig {
        val raw = prefs.getString(KEY_AUTOMATION_CONFIG, null) ?: return LocalAutomationConfig()
        return runCatching { LocalAutomationConfig.fromJson(JSONObject(raw)) }
            .getOrDefault(LocalAutomationConfig())
    }

    fun updateConfig(newConfig: LocalAutomationConfig) {
        prefs.edit().putString(KEY_AUTOMATION_CONFIG, newConfig.toJson().toString()).apply()
        _config.value = newConfig
        evaluateScheduleTick(forceCheck = true)
    }

    fun notifyUserManualNoiseChange() {
        lastUserManualChangeAt = System.currentTimeMillis()
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        runCatching {
            audioManager?.addOnModeChangedListener(mainExecutor, modeListener)
            audioManager?.registerAudioPlaybackCallback(playbackCallback, mainHandler)
        }
        evaluateScheduleTick(forceCheck = true)
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching {
            audioManager?.removeOnModeChangedListener(modeListener)
            audioManager?.unregisterAudioPlaybackCallback(playbackCallback)
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
        if (!forceCheck && System.currentTimeMillis() - lastUserManualChangeAt < USER_OVERRIDE_COOLDOWN_MS) return

        val inWindow = isCurrentTimeInWindow(
            cfg.scheduleStartHour,
            cfg.scheduleStartMinute,
            cfg.scheduleEndHour,
            cfg.scheduleEndMinute
        )
        if (inWindow && !scheduleWindowWasActive) {
            scheduleWindowWasActive = true
            if (state.noiseMode != cfg.scheduleTargetMode) {
                lastTriggerAt = System.currentTimeMillis()
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

    private fun handleAudioModeChanged(audioMode: Int) {
        val cfg = _config.value
        val state = stateProvider()
        if (!cfg.masterEnabled || !cfg.callRuleEnabled || !state.isConnected || !state.effectiveHasAnc) {
            if (audioMode == AudioManager.MODE_NORMAL) {
                callActive = false
                preCallSnapshot = null
            }
            return
        }

        val isCallNow = audioMode == AudioManager.MODE_IN_CALL ||
            audioMode == AudioManager.MODE_IN_COMMUNICATION

        if (isCallNow && !callActive) {
            callActive = true
            preCallSnapshot = SavedNoiseSnapshot(
                mode = state.noiseMode,
                ambientLevel = state.ambientLevel,
                voiceFocus = state.voiceFocus
            )
            applyNoiseControl(NoiseControlMode.AMBIENT, state.ambientLevel.coerceAtLeast(15), true, "CallStart")
        } else if (!isCallNow && callActive) {
            callActive = false
            val restore = preCallSnapshot
            preCallSnapshot = null
            if (restore != null) {
                applyNoiseControl(restore.mode, restore.ambientLevel, restore.voiceFocus, "CallEnd")
            }
        }
    }

    private fun handleMediaPlaybackChanged(isPlaying: Boolean) {
        val prev = mediaPlayingActive
        mediaPlayingActive = isPlaying
        if (!isPlaying || prev == isPlaying) return

        val cfg = _config.value
        val state = stateProvider()
        if (!cfg.masterEnabled || !cfg.mediaRuleEnabled || !state.isConnected || !state.effectiveHasAnc) return
        if (callActive) return

        val now = System.currentTimeMillis()
        if (now - lastUserManualChangeAt < USER_OVERRIDE_COOLDOWN_MS) return
        if (now - lastTriggerAt < RULE_TRIGGER_COOLDOWN_MS) return

        if (state.noiseMode != cfg.mediaTargetMode) {
            lastTriggerAt = now
            applyNoiseControl(cfg.mediaTargetMode, state.ambientLevel, state.voiceFocus, "MediaPlayback")
        }
    }

    internal fun isCurrentTimeInWindow(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        nowCal: Calendar = Calendar.getInstance()
    ): Boolean {
        val nowMins = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        val startMins = startHour * 60 + startMinute
        val endMins = endHour * 60 + endMinute
        return if (startMins == endMins) {
            false
        } else if (startMins < endMins) {
            nowMins in startMins until endMins
        } else {
            nowMins >= startMins || nowMins < endMins
        }
    }
}
