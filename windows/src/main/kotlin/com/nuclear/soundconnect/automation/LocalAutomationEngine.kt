package com.nuclear.soundconnect.automation

import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import org.json.JSONObject
import java.util.Calendar

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
        put("scheduleStartHour", scheduleStartHour)
        put("scheduleStartMinute", scheduleStartMinute)
        put("scheduleEndHour", scheduleEndHour)
        put("scheduleEndMinute", scheduleEndMinute)
        put("scheduleTargetMode", scheduleTargetMode.name)
    }

    companion object {
        fun fromJson(raw: String?): LocalAutomationConfig {
            if (raw.isNullOrBlank()) return LocalAutomationConfig()
            return try {
                fromJsonObject(JSONObject(raw))
            } catch (_: Exception) {
                LocalAutomationConfig()
            }
        }

        fun fromJsonObject(obj: JSONObject?): LocalAutomationConfig {
            if (obj == null) return LocalAutomationConfig()
            val mediaMode = runCatching {
                val rawMode = obj.optString("mediaTargetMode", NoiseControlMode.ANC.name)
                if (rawMode == "NOISE_CANCELLING") NoiseControlMode.ANC else NoiseControlMode.valueOf(rawMode)
            }.getOrDefault(NoiseControlMode.ANC)
            val schedMode = runCatching {
                val rawMode = obj.optString("scheduleTargetMode", NoiseControlMode.ANC.name)
                if (rawMode == "NOISE_CANCELLING") NoiseControlMode.ANC else NoiseControlMode.valueOf(rawMode)
            }.getOrDefault(NoiseControlMode.ANC)
            return LocalAutomationConfig(
                masterEnabled = obj.optBoolean("masterEnabled", false),
                callRuleEnabled = obj.optBoolean("callRuleEnabled", true),
                mediaRuleEnabled = obj.optBoolean("mediaRuleEnabled", false),
                mediaTargetMode = mediaMode,
                scheduleRuleEnabled = obj.optBoolean("scheduleRuleEnabled", false),
                scheduleStartHour = obj.optInt("scheduleStartHour", 22).coerceIn(0, 23),
                scheduleStartMinute = obj.optInt("scheduleStartMinute", 0).coerceIn(0, 59),
                scheduleEndHour = obj.optInt("scheduleEndHour", 7).coerceIn(0, 23),
                scheduleEndMinute = obj.optInt("scheduleEndMinute", 0).coerceIn(0, 59),
                scheduleTargetMode = schedMode
            )
        }
    }
}

sealed class AutomationEvent {
    data class CallStart(val active: Boolean = true) : AutomationEvent()
    data class CallEnd(val restorePrevious: Boolean = true) : AutomationEvent()
    data class MediaPlayback(val playing: Boolean) : AutomationEvent()
}

data class SavedNoiseSnapshot(
    val mode: NoiseControlMode,
    val ambientLevel: Int,
    val voiceFocus: Boolean
)

/**
 * Local zero-telemetry automation engine for Windows desktop:
 * - Hands-Free (HFP) call start -> temporary Transparency with Voice Focus, restores on end
 * - Local Windows A2DP media playback start -> preferred mode
 * - Quiet hours schedule window (local system clock, overnight wrap supported)
 */
class LocalAutomationEngine(
    private val applyNoiseAction: suspend (
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        reason: String
    ) -> Unit
) {
    @Volatile
    private var lastManualOverrideMs: Long = 0L

    @Volatile
    private var preCallSnapshot: SavedNoiseSnapshot? = null

    @Volatile
    private var lastScheduleWindowActive: Boolean? = null

    @Volatile
    private var lastRuleTriggeredAtMs: Long = 0L

    fun notifyUserManualOverride() {
        lastManualOverrideMs = System.currentTimeMillis()
        preCallSnapshot = null
    }

    private fun isRespectingUserOverride(now: Long = System.currentTimeMillis()): Boolean =
        now - lastManualOverrideMs < USER_OVERRIDE_COOLDOWN_MS

    suspend fun handleEvent(
        config: LocalAutomationConfig,
        event: AutomationEvent,
        isConnected: Boolean,
        currentMode: NoiseControlMode,
        currentAmbient: Int,
        currentVoiceFocus: Boolean
    ) {
        if (!config.masterEnabled || !isConnected) return
        val now = System.currentTimeMillis()

        when (event) {
            is AutomationEvent.CallStart -> {
                if (!config.callRuleEnabled || !event.active) return
                if (preCallSnapshot == null) {
                    preCallSnapshot = SavedNoiseSnapshot(currentMode, currentAmbient, currentVoiceFocus)
                }
                if (currentMode != NoiseControlMode.AMBIENT || !currentVoiceFocus) {
                    lastRuleTriggeredAtMs = now
                    applyNoiseAction(
                        NoiseControlMode.AMBIENT,
                        currentAmbient.coerceAtLeast(15),
                        true,
                        "Auto: Llamada/HFP entrante"
                    )
                }
            }
            is AutomationEvent.CallEnd -> {
                if (!config.callRuleEnabled || !event.restorePrevious) return
                val snapshot = preCallSnapshot ?: return
                preCallSnapshot = null
                if (!isRespectingUserOverride(now)) {
                    lastRuleTriggeredAtMs = now
                    applyNoiseAction(
                        snapshot.mode,
                        snapshot.ambientLevel,
                        snapshot.voiceFocus,
                        "Auto: Fin de llamada/HFP"
                    )
                }
            }
            is AutomationEvent.MediaPlayback -> {
                if (!config.mediaRuleEnabled || !event.playing) return
                if (preCallSnapshot != null) return // Do not override active call mode
                if (isRespectingUserOverride(now) || now - lastRuleTriggeredAtMs < RULE_TRIGGER_COOLDOWN_MS) return
                if (currentMode != config.mediaTargetMode) {
                    lastRuleTriggeredAtMs = now
                    applyNoiseAction(
                        config.mediaTargetMode,
                        currentAmbient,
                        currentVoiceFocus,
                        "Auto: Reproducción multimedia"
                    )
                }
            }
        }
    }

    suspend fun evaluateScheduleTick(
        config: LocalAutomationConfig,
        isConnected: Boolean,
        currentMode: NoiseControlMode,
        currentAmbient: Int,
        currentVoiceFocus: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (!config.masterEnabled || !config.scheduleRuleEnabled || !isConnected) {
            lastScheduleWindowActive = null
            return
        }
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val inWindow = isCurrentTimeInWindow(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            config.scheduleStartHour,
            config.scheduleStartMinute,
            config.scheduleEndHour,
            config.scheduleEndMinute
        )
        val prev = lastScheduleWindowActive
        lastScheduleWindowActive = inWindow

        // Apply only on transition into schedule window so manual toggles during window stick
        if (inWindow && prev == false && !isRespectingUserOverride(nowMillis) && preCallSnapshot == null) {
            if (currentMode != config.scheduleTargetMode) {
                lastRuleTriggeredAtMs = nowMillis
                applyNoiseAction(
                    config.scheduleTargetMode,
                    currentAmbient,
                    currentVoiceFocus,
                    "Auto: Franja horaria"
                )
            }
        }
    }

    companion object {
        private const val USER_OVERRIDE_COOLDOWN_MS = 30_000L
        private const val RULE_TRIGGER_COOLDOWN_MS = 4_000L

        fun isCurrentTimeInWindow(
            hour: Int,
            minute: Int,
            startHour: Int,
            startMinute: Int,
            endHour: Int,
            endMinute: Int
        ): Boolean {
            val nowMin = hour * 60 + minute
            val startMin = startHour * 60 + startMinute
            val endMin = endHour * 60 + endMinute
            return if (startMin == endMin) {
                false
            } else if (startMin < endMin) {
                nowMin in startMin until endMin
            } else {
                // Overnight window (e.g., 22:00 -> 07:00)
                nowMin >= startMin || nowMin < endMin
            }
        }
    }
}
