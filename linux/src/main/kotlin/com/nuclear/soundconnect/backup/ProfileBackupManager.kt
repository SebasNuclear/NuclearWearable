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
    val schemaVersion: Int = 1,
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
    val eqBands: List<Int> = emptyList(),
    val eqIsTenBand: Boolean = false,
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
    val automationConfig: LocalAutomationConfig = LocalAutomationConfig(),
    val languageCode: String = "es",
    val accentColorArgb: Int = 0xFF00E676.toInt()
)

/**
 * Respaldo y restauración local offline en formato JSON via Storage Access Framework (Fase 3.5)
 * + formateo de tramas Hex del Modo Laboratorio para copiar o exportar.
 */
object ProfileBackupManager {

    fun createBundleFromState(
        state: HeadsetState,
        automationConfig: LocalAutomationConfig,
        languageCode: String
    ): NuclearBackupBundle = NuclearBackupBundle(
        deviceName = state.deviceName,
        deviceAddress = state.deviceAddress,
        forcedModelFamily = state.forcedModelFamily,
        noiseMode = state.noiseMode,
        ambientLevel = state.ambientLevel,
        voiceFocus = state.voiceFocus,
        windNoiseReduction = state.windNoiseReduction,
        eqPresetId = state.eqState.presetId,
        eqClearBass = state.eqState.clearBass,
        eqBands = state.eqState.bands,
        eqIsTenBand = state.eqState.isTenBand,
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
        automationConfig = automationConfig,
        languageCode = languageCode,
        accentColorArgb = state.accentColor
    )

    fun exportJson(bundle: NuclearBackupBundle): String {
        val root = JSONObject().apply {
            put("schemaVersion", bundle.schemaVersion)
            put("appVersionName", bundle.appVersionName)
            put("exportedAtMillis", bundle.exportedAtMillis)
            put("deviceName", bundle.deviceName)
            put("deviceAddress", bundle.deviceAddress)
            bundle.forcedModelFamily?.let { put("forcedModelFamily", it.name) }

            put("noiseMode", bundle.noiseMode.name)
            put("ambientLevel", bundle.ambientLevel.coerceIn(1, 20))
            put("voiceFocus", bundle.voiceFocus)
            put("windNoiseReduction", bundle.windNoiseReduction)

            put("eqPresetId", bundle.eqPresetId)
            put("eqClearBass", bundle.eqClearBass.coerceIn(-10, 10))
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

            put("automation", bundle.automationConfig.toJson())
            put("languageCode", bundle.languageCode)
            put("accentColorArgb", bundle.accentColorArgb)
        }
        return root.toString(2)
    }

    const val CURRENT_SCHEMA_VERSION = 1

    fun parseBackupJson(rawJson: String): Result<NuclearBackupBundle> = runCatching {
        val obj = JSONObject(rawJson)
        val schema = obj.optInt("schemaVersion", 0)
        require(schema in 1..CURRENT_SCHEMA_VERSION) { "Invalid or unsupported backup schemaVersion: $schema" }

        val bandsArray = obj.optJSONArray("eqBands")
        val bands = mutableListOf<Int>()
        val isTenBand = obj.optBoolean("eqIsTenBand", false)
        if (bandsArray != null) {
            for (i in 0 until bandsArray.length()) {
                val rawVal = bandsArray.optInt(i, if (isTenBand) 6 else 0)
                bands.add(if (isTenBand) rawVal.coerceIn(0, 12) else rawVal.coerceIn(-10, 10))
            }
        }

        val automationObj = obj.optJSONObject("automation")
        val automationCfg = if (automationObj != null) {
            LocalAutomationConfig.fromJson(automationObj)
        } else {
            LocalAutomationConfig()
        }

        NuclearBackupBundle(
            schemaVersion = schema,
            appVersionName = obj.optString("appVersionName", "1.1.0"),
            exportedAtMillis = obj.optLong("exportedAtMillis", System.currentTimeMillis()),
            deviceName = obj.optString("deviceName", ""),
            deviceAddress = obj.optString("deviceAddress", ""),
            forcedModelFamily = obj.optString("forcedModelFamily", "")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { SonyModelFamily.valueOf(it) }.getOrNull() },
            noiseMode = runCatching {
                NoiseControlMode.valueOf(obj.optString("noiseMode", NoiseControlMode.ANC.name))
            }.getOrDefault(NoiseControlMode.ANC),
            ambientLevel = obj.optInt("ambientLevel", 20).coerceIn(1, 20),
            voiceFocus = obj.optBoolean("voiceFocus", false),
            windNoiseReduction = obj.optBoolean("windNoiseReduction", false),
            eqPresetId = obj.optInt("eqPresetId", 0x00) and 0xFF,
            eqClearBass = obj.optInt("eqClearBass", 0).coerceIn(-10, 10),
            eqBands = bands,
            eqIsTenBand = isTenBand,
            dseeEnabled = if (obj.has("dseeEnabled")) obj.optBoolean("dseeEnabled") else null,
            connectionPriority = obj.optString("connectionPriority", "")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { ConnectionPriority.valueOf(it) }.getOrNull() },
            autoPowerOff = obj.optString("autoPowerOff", "")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { AutoPowerOffOption.valueOf(it) }.getOrNull() },
            speakToChatEnabled = if (obj.has("speakToChatEnabled")) obj.optBoolean("speakToChatEnabled") else null,
            speakToChatSensitivity = runCatching {
                SpeakToChatSensitivity.valueOf(obj.optString("speakToChatSensitivity", SpeakToChatSensitivity.AUTO.name))
            }.getOrDefault(SpeakToChatSensitivity.AUTO),
            speakToChatReturnTime = runCatching {
                SpeakToChatReturnTime.valueOf(obj.optString("speakToChatReturnTime", SpeakToChatReturnTime.MID.name))
            }.getOrDefault(SpeakToChatReturnTime.MID),
            speakToChatFocusOnVoice = obj.optBoolean("speakToChatFocusOnVoice", false),
            pauseWhenRemoved = if (obj.has("pauseWhenRemoved")) obj.optBoolean("pauseWhenRemoved") else null,
            touchLeftPreset = obj.optString("touchLeftPreset", "")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { TouchPresetOption.valueOf(it) }.getOrNull() },
            touchRightPreset = obj.optString("touchRightPreset", "")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { TouchPresetOption.valueOf(it) }.getOrNull() },
            touchPanelEnabled = if (obj.has("touchPanelEnabled")) obj.optBoolean("touchPanelEnabled") else null,
            automationConfig = automationCfg,
            languageCode = obj.optString("languageCode", "es"),
            accentColorArgb = obj.optInt("accentColorArgb", 0xFF00E676.toInt())
        )
    }

    fun isUnknownOrUnparsedFrame(entry: HexLogEntry): Boolean {
        val upperSummary = entry.summary.uppercase(Locale.US)
        return entry.direction == HexLogEntry.Direction.ERROR ||
            upperSummary.contains("UNKNOWN") ||
            upperSummary.contains("UNPARSED") ||
            upperSummary.contains("UNHANDLED") ||
            upperSummary.contains("ERROR") ||
            upperSummary.contains("DESCONOCID") ||
            upperSummary.contains("NO RECONOCID") ||
            upperSummary.contains("SIN PARSE") ||
            upperSummary.contains("FALLO")
    }

    fun formatHexMonitorExport(entries: List<HexLogEntry>, unknownOnly: Boolean = false): String {
        val filtered = if (unknownOnly) entries.filter(::isUnknownOrUnparsedFrame) else entries
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return buildString {
            appendLine("# NuclearWearable MDR Hex Frame Dump (${filtered.size} entries, unknownOnly=$unknownOnly)")
            for (e in filtered) {
                appendLine("[${fmt.format(Date(e.timestamp))}] [${e.direction}] ${e.summary} :: ${e.hex}")
            }
        }
    }
}
