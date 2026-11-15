package com.nuclear.soundconnect.backup

import com.nuclear.soundconnect.automation.LocalAutomationConfig
import com.nuclear.soundconnect.bluetooth.AutoPowerOffOption
import com.nuclear.soundconnect.bluetooth.ConnectionPriority
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.HexLogEntry
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyModelFamily
import com.nuclear.soundconnect.bluetooth.SpeakToChatReturnTime
import com.nuclear.soundconnect.bluetooth.SpeakToChatSensitivity
import com.nuclear.soundconnect.bluetooth.TouchPresetOption
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NuclearBackupBundle(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersionName: String = "1.1.0",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val deviceName: String = "",
    val deviceAddress: String = "",
    val forcedModelFamily: SonyModelFamily? = null,
    val noiseMode: NoiseControlMode = NoiseControlMode.ANC,
    val ambientLevel: Int = 20,
    val voiceFocus: Boolean = false,
    val windNoiseReduction: Boolean = false,
    val eqPresetId: Int = 0x00,
    val eqClearBass: Int = 0,
    val eqIsTenBand: Boolean = false,
    val eqBands: List<Int> = emptyList(),
    val dseeEnabled: Boolean? = null,
    val connectionPriority: ConnectionPriority? = null,
    val autoPowerOff: AutoPowerOffOption? = null,
    val speakToChatEnabled: Boolean? = null,
    val speakToChatSensitivity: SpeakToChatSensitivity = SpeakToChatSensitivity.AUTO,
    val speakToChatReturnTime: SpeakToChatReturnTime = SpeakToChatReturnTime.MID,
    val speakToChatFocusOnVoice: Boolean = false,
    val pauseWhenRemoved: Boolean? = null,
    val touchLeftPreset: TouchPresetOption? = null,
    val touchRightPreset: TouchPresetOption? = null,
    val touchPanelEnabled: Boolean? = null,
    val automation: LocalAutomationConfig = LocalAutomationConfig(),
    val languageCode: String? = null,
    val accentColorArgb: Int? = null
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * Local JSON profile backup & restore + Lab Mode Hex trace exporter.
 * 100% compatible with NuclearWearable Android v1.1.0 schema.
 * Strictly offline — no cloud sync, no internet permission, no telemetry.
 */
object ProfileBackupManager {

    fun createBundleFromState(
        state: HeadsetState,
        automation: LocalAutomationConfig,
        languageCode: String?,
        accentColorArgb: Int?,
        appVersionName: String = "1.1.0"
    ): NuclearBackupBundle {
        return NuclearBackupBundle(
            appVersionName = appVersionName,
            exportedAtMillis = System.currentTimeMillis(),
            deviceName = state.deviceName,
            deviceAddress = state.deviceAddress,
            forcedModelFamily = state.forcedModelFamily,
            noiseMode = state.noiseMode,
            ambientLevel = state.ambientLevel,
            voiceFocus = state.voiceFocus,
            windNoiseReduction = state.windNoiseReduction,
            eqPresetId = state.eqState.presetId,
            eqClearBass = state.eqState.clearBass,
            eqIsTenBand = state.eqState.isTenBand,
            eqBands = state.eqState.bands,
            dseeEnabled = state.dseeEnabled,
            connectionPriority = state.connectionPriority,
            autoPowerOff = state.autoPowerOff,
            speakToChatEnabled = state.speakToChatEnabled,
            speakToChatSensitivity = state.speakToChatSensitivity,
            speakToChatReturnTime = state.speakToChatReturnTime,
            speakToChatFocusOnVoice = state.speakToChatFocusOnVoice,
            pauseWhenRemoved = state.pauseWhenRemoved,
            touchLeftPreset = state.touchLeftPreset,
            touchRightPreset = state.touchRightPreset,
            touchPanelEnabled = state.touchPanelEnabled,
            automation = automation,
            languageCode = languageCode,
            accentColorArgb = accentColorArgb
        )
    }

    fun exportJson(bundle: NuclearBackupBundle): String {
        val root = JSONObject().apply {
            put("schemaVersion", bundle.schemaVersion)
            put("appVersionName", bundle.appVersionName)
            put("exportedAtMillis", bundle.exportedAtMillis)
            put("deviceName", bundle.deviceName)
            put("deviceAddress", bundle.deviceAddress)
            if (bundle.forcedModelFamily != null) {
                put("forcedModelFamily", bundle.forcedModelFamily.name)
            }
            put("noiseMode", bundle.noiseMode.name)
            put("ambientLevel", bundle.ambientLevel)
            put("voiceFocus", bundle.voiceFocus)
            put("windNoiseReduction", bundle.windNoiseReduction)
            put("eqPresetId", bundle.eqPresetId)
            put("eqClearBass", bundle.eqClearBass)
            put("eqIsTenBand", bundle.eqIsTenBand)
            put("eqBands", JSONArray(bundle.eqBands))
            bundle.dseeEnabled?.let { put("dseeEnabled", it) }
            bundle.connectionPriority?.let { put("connectionPriority", it.name) }
            bundle.autoPowerOff?.let { put("autoPowerOff", it.name) }
            bundle.speakToChatEnabled?.let { put("speakToChatEnabled", it) }
            put("speakToChatSensitivity", bundle.speakToChatSensitivity.name)
            put("speakToChatReturnTime", bundle.speakToChatReturnTime.name)
            put("speakToChatFocusOnVoice", bundle.speakToChatFocusOnVoice)
            bundle.pauseWhenRemoved?.let { put("pauseWhenRemoved", it) }
            bundle.touchLeftPreset?.let { put("touchLeftPreset", it.name) }
            bundle.touchRightPreset?.let { put("touchRightPreset", it.name) }
            bundle.touchPanelEnabled?.let { put("touchPanelEnabled", it) }
            put("automation", bundle.automation.toJson())
            bundle.languageCode?.let { put("languageCode", it) }
            bundle.accentColorArgb?.let { put("accentColorArgb", it) }
        }
        return root.toString(2)
    }

    fun parseBackupJson(rawJson: String): Result<NuclearBackupBundle> = runCatching {
        val obj = JSONObject(rawJson)
        val schema = obj.optInt("schemaVersion", 0)
        require(schema in 1..NuclearBackupBundle.CURRENT_SCHEMA_VERSION) {
            "Unsupported backup schemaVersion=$schema"
        }

        val bandsArray = obj.optJSONArray("eqBands")
        val bands = if (bandsArray != null) {
            List(bandsArray.length()) { idx -> bandsArray.optInt(idx, 0) }
        } else {
            emptyList()
        }

        NuclearBackupBundle(
            schemaVersion = schema,
            appVersionName = obj.optString("appVersionName", "1.1.0"),
            exportedAtMillis = obj.optLong("exportedAtMillis", 0L),
            deviceName = obj.optString("deviceName", ""),
            deviceAddress = obj.optString("deviceAddress", ""),
            forcedModelFamily = obj.optNullableString("forcedModelFamily")?.let { name ->
                runCatching { SonyModelFamily.valueOf(name) }.getOrNull()
            },
            noiseMode = obj.optNullableString("noiseMode")?.let { name ->
                runCatching { NoiseControlMode.valueOf(name) }.getOrNull()
            } ?: NoiseControlMode.ANC,
            ambientLevel = obj.optInt("ambientLevel", 20).coerceIn(1, 20),
            voiceFocus = obj.optBoolean("voiceFocus", false),
            windNoiseReduction = obj.optBoolean("windNoiseReduction", false),
            eqPresetId = obj.optInt("eqPresetId", 0x00),
            eqClearBass = obj.optInt("eqClearBass", 0).coerceIn(-10, 10),
            eqIsTenBand = obj.optBoolean("eqIsTenBand", false),
            eqBands = bands,
            dseeEnabled = obj.optNullableBoolean("dseeEnabled"),
            connectionPriority = obj.optNullableString("connectionPriority")?.let { name ->
                runCatching { ConnectionPriority.valueOf(name) }.getOrNull()
            },
            autoPowerOff = obj.optNullableString("autoPowerOff")?.let { name ->
                runCatching { AutoPowerOffOption.valueOf(name) }.getOrNull()
            },
            speakToChatEnabled = obj.optNullableBoolean("speakToChatEnabled"),
            speakToChatSensitivity = obj.optNullableString("speakToChatSensitivity")?.let { name ->
                runCatching { SpeakToChatSensitivity.valueOf(name) }.getOrNull()
            } ?: SpeakToChatSensitivity.AUTO,
            speakToChatReturnTime = obj.optNullableString("speakToChatReturnTime")?.let { name ->
                runCatching { SpeakToChatReturnTime.valueOf(name) }.getOrNull()
            } ?: SpeakToChatReturnTime.MID,
            speakToChatFocusOnVoice = obj.optBoolean("speakToChatFocusOnVoice", false),
            pauseWhenRemoved = obj.optNullableBoolean("pauseWhenRemoved"),
            touchLeftPreset = obj.optNullableString("touchLeftPreset")?.let { name ->
                runCatching { TouchPresetOption.valueOf(name) }.getOrNull()
            },
            touchRightPreset = obj.optNullableString("touchRightPreset")?.let { name ->
                runCatching { TouchPresetOption.valueOf(name) }.getOrNull()
            },
            touchPanelEnabled = obj.optNullableBoolean("touchPanelEnabled"),
            automation = LocalAutomationConfig.fromJsonObject(obj.optJSONObject("automation")),
            languageCode = obj.optNullableString("languageCode"),
            accentColorArgb = if (obj.has("accentColorArgb") && !obj.isNull("accentColorArgb")) {
                obj.optInt("accentColorArgb")
            } else {
                null
            }
        )
    }

    fun isUnknownOrUnparsedFrame(entry: HexLogEntry): Boolean {
        if (entry.direction == HexLogEntry.Direction.ERROR) return true
        val sum = entry.summary
        return sum.startsWith("RX cmd=") ||
            sum.contains("unknown", ignoreCase = true) ||
            sum.contains("unparsed", ignoreCase = true) ||
            sum.contains("desconocid", ignoreCase = true) ||
            sum.contains("no reconocida", ignoreCase = true)
    }

    fun formatHexMonitorExport(
        entries: List<HexLogEntry>,
        unknownOnly: Boolean = false
    ): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val filtered = if (unknownOnly) entries.filter(::isUnknownOrUnparsedFrame) else entries
        return buildString {
            appendLine("# NuclearWearable Lab Mode Trace (${filtered.size} entries, filter=${if (unknownOnly) "UNKNOWN_OR_ERROR" else "ALL"})")
            filtered.forEach { entry ->
                val ts = formatter.format(Date(entry.timestamp))
                appendLine("[$ts] [${entry.direction.name}] ${entry.summary}")
                if (entry.hex.isNotBlank()) {
                    appendLine("    HEX: ${entry.hex}")
                }
            }
        }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optNullableBoolean(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null
}
