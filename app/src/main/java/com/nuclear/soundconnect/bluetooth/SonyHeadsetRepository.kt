package com.nuclear.soundconnect.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.nuclear.soundconnect.audio.AudioCodecMonitor
import com.nuclear.soundconnect.audio.EarSide
import com.nuclear.soundconnect.audio.FindBudsPlaybackState
import com.nuclear.soundconnect.audio.FindMyBudsTonePlayer
import com.nuclear.soundconnect.audio.LiveCodecHudInfo
import com.nuclear.soundconnect.automation.LocalAutomationConfig
import com.nuclear.soundconnect.automation.LocalAutomationEngine
import com.nuclear.soundconnect.backup.NuclearBackupBundle
import com.nuclear.soundconnect.backup.ProfileBackupManager
import com.nuclear.soundconnect.tile.NuclearQuickSettingsTileService
import com.nuclear.soundconnect.widget.NuclearWidgetManager
import com.nuclear.soundconnect.wear.NuclearPhoneWearableSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Repositorio central que coordina el estado de los audífonos Sony, la persistencia en disco
 * y la actualización del Widget en la pantalla de inicio.
 */
class SonyHeadsetRepository private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "sony_buds_controller_prefs"
        private const val KEY_DEV_NAME = "dev_name"
        private const val KEY_DEV_ADDR = "dev_addr"
        private const val KEY_DEV_TYPE = "dev_type"
        private const val KEY_IS_CONNECTED = "is_connected"
        private const val KEY_LEFT_BATTERY = "left_battery"
        private const val KEY_LEFT_CHARGING = "left_charging"
        private const val KEY_RIGHT_BATTERY = "right_battery"
        private const val KEY_RIGHT_CHARGING = "right_charging"
        private const val KEY_CASE_BATTERY = "case_battery"
        private const val KEY_CASE_CHARGING = "case_charging"
        private const val KEY_SINGLE_BATTERY = "single_battery"
        private const val KEY_SINGLE_CHARGING = "single_charging"
        private const val KEY_NOISE_MODE = "noise_mode"
        private const val KEY_AMBIENT_LEVEL = "ambient_level"
        private const val KEY_VOICE_FOCUS = "voice_focus"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_HW_MODEL = "hw_model"
        private const val KEY_FW_VERSION = "fw_version"
        private const val KEY_PROTOCOL_VERSION = "protocol_version"
        private const val KEY_CODEC_NAME = "codec_name"
        private const val KEY_SUPPORTS_ANC_OVERRIDE = "supports_anc_override"
        private const val PREF_PREFIX_MAC_MODEL = "mac_model_"
        private const val PREF_PREFIX_FORCED_MODEL = "forced_model_"
        private const val PREF_PREFIX_HW_MODEL = "mac_hw_model_"
        private const val PREF_PREFIX_FW_VER = "mac_fw_ver_"
        private const val PREF_PREFIX_PROTO_VER = "mac_proto_ver_"
        private const val KEY_GLOBAL_FORCED_MODEL = "global_forced_model"
        private const val KEY_EXTENDED_CACHE_JSON = "extended_state_cache_json"

        @Volatile
        private var INSTANCE: SonyHeadsetRepository? = null

        fun getInstance(context: Context): SonyHeadsetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SonyHeadsetRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun isSonyDeviceName(name: String?): Boolean = SonyRfcommClient.isSonyDeviceName(name)

        fun isSonyAudioDevice(device: BluetoothDevice): Boolean {
            if (SonyRfcommClient.isSonyAudioDevice(device)) return true
            if (INSTANCE?.isMacKnown(device.address) == true) return true
            return false
        }
    }

    fun isSonyAudioDevice(device: BluetoothDevice): Boolean = Companion.isSonyAudioDevice(device)
    fun isSonyDeviceName(name: String?): Boolean = Companion.isSonyDeviceName(name)

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val coroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("SonyHeadsetRepository", "Unhandled coroutine error", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)
    val rfcommClient = SonyRfcommClient(context)

    @Volatile
    private var lastUserModeChangeTimestamp: Long = 0L

    @Volatile
    private var activeSyncJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var activeModeChangeJob: kotlinx.coroutines.Job? = null

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<com.nuclear.soundconnect.ui.AppLanguage> = _language.asStateFlow()

    private fun loadLanguage(): com.nuclear.soundconnect.ui.AppLanguage {
        val code = prefs.getString(KEY_APP_LANGUAGE, com.nuclear.soundconnect.ui.AppLanguage.ES.code)
        return com.nuclear.soundconnect.ui.AppLanguage.fromCode(code)
    }

    fun setLanguage(lang: com.nuclear.soundconnect.ui.AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, lang.code).apply()
        _language.value = lang
    }

    fun saveModelForMac(address: String, family: SonyModelFamily) {
        if (address.isBlank()) return
        prefs.edit().putString(PREF_PREFIX_MAC_MODEL + address.uppercase(), family.name).apply()
    }

    fun getModelForMac(address: String?): SonyModelFamily? {
        if (address.isNullOrBlank()) return null
        val name = prefs.getString(PREF_PREFIX_MAC_MODEL + address.uppercase(), null) ?: return null
        return runCatching { SonyModelFamily.valueOf(name) }.getOrNull()
    }

    fun isMacKnown(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        return prefs.contains(PREF_PREFIX_MAC_MODEL + address.uppercase()) ||
               prefs.contains(PREF_PREFIX_FORCED_MODEL + address.uppercase())
    }

    fun setUserForcedModel(address: String?, family: SonyModelFamily?) {
        val editor = prefs.edit()
        if (family == null) {
            if (!address.isNullOrBlank()) {
                editor.remove(PREF_PREFIX_FORCED_MODEL + address.uppercase())
            }
            editor.remove(KEY_GLOBAL_FORCED_MODEL)
        } else {
            if (!address.isNullOrBlank()) {
                editor.putString(PREF_PREFIX_FORCED_MODEL + address.uppercase(), family.name)
            }
            editor.putString(KEY_GLOBAL_FORCED_MODEL, family.name)
        }
        editor.apply()

        val current = _state.value
        val tempState = current.copy(
            forcedModelFamily = family,
            cachedModelFamily = if (!address.isNullOrBlank()) getModelForMac(address) else current.cachedModelFamily
        )
        val newType = family?.defaultDeviceType ?: tempState.modelFamily.defaultDeviceType
        updateState(tempState.copy(deviceType = newType))
    }

    fun getUserForcedModel(address: String?): SonyModelFamily? {
        if (!address.isNullOrBlank()) {
            val name = prefs.getString(PREF_PREFIX_FORCED_MODEL + address.uppercase(), null)
            if (name != null) {
                return runCatching { SonyModelFamily.valueOf(name) }.getOrNull()
            }
        }
        val globalName = prefs.getString(KEY_GLOBAL_FORCED_MODEL, null) ?: return null
        return runCatching { SonyModelFamily.valueOf(globalName) }.getOrNull()
    }

    private fun saveHwModelForMac(address: String, hwModel: String) {
        if (address.isBlank() || hwModel.isBlank()) return
        prefs.edit().putString(PREF_PREFIX_HW_MODEL + address.uppercase(), hwModel).apply()
    }

    private fun getHwModelForMac(address: String?): String? {
        if (address.isNullOrBlank()) return null
        return prefs.getString(PREF_PREFIX_HW_MODEL + address.uppercase(), null)?.takeIf { it.isNotBlank() }
    }

    private fun saveFwVersionForMac(address: String, fw: String) {
        if (address.isBlank() || fw.isBlank()) return
        prefs.edit().putString(PREF_PREFIX_FW_VER + address.uppercase(), fw).apply()
    }

    private fun getFwVersionForMac(address: String?): String? {
        if (address.isNullOrBlank()) return null
        return prefs.getString(PREF_PREFIX_FW_VER + address.uppercase(), null)?.takeIf { it.isNotBlank() }
    }

    private fun saveProtoVersionForMac(address: String, proto: Int) {
        if (address.isBlank()) return
        prefs.edit().putInt(PREF_PREFIX_PROTO_VER + address.uppercase(), proto).apply()
    }

    private fun getProtoVersionForMac(address: String?): Int? {
        if (address.isNullOrBlank()) return null
        val key = PREF_PREFIX_PROTO_VER + address.uppercase()
        return if (prefs.contains(key)) prefs.getInt(key, 2) else null
    }

    private val _state = MutableStateFlow(loadCachedState())
    val state: StateFlow<HeadsetState> = _state.asStateFlow()

    private val audioCodecMonitor = AudioCodecMonitor(context)
    val liveCodecHud: StateFlow<LiveCodecHudInfo> = audioCodecMonitor.hudInfo

    private val findMyBudsPlayer = FindMyBudsTonePlayer()
    val findBudsState: StateFlow<FindBudsPlaybackState> = findMyBudsPlayer.playbackState

    val localAutomationEngine = LocalAutomationEngine(
        context = context,
        prefs = prefs,
        stateProvider = { _state.value },
        applyNoiseControl = { mode, level, voiceFocus, _ ->
            applyAutomationNoiseMode(mode, level, voiceFocus)
        }
    )
    val automationConfig: StateFlow<LocalAutomationConfig> = localAutomationEngine.config

    @Volatile
    private var lastExtendedSyncAt: Long = 0L

    @Volatile
    private var lastExtendedSyncMac: String = ""

    init {
        runCatching {
            audioCodecMonitor.start()
            localAutomationEngine.start()
        }
        scope.launch {
            rfcommClient.liveStateUpdates.collect { incoming ->
                mergeAndPublishIncomingState(incoming)
            }
        }
        scope.launch {
            rfcommClient.connectionEvents.collect { (mac, open) ->
                if (!open) {
                    val current = _state.value
                    if (current.deviceAddress.equals(mac, ignoreCase = true) && current.isConnected) {
                        val btDev = getTargetBluetoothDevice()
                        if (btDev == null || !btDev.address.equals(mac, ignoreCase = true) || !isDeviceBtConnected(btDev)) {
                            saveDisconnectedSnapshot(current)
                            updateState(current.copy(isConnected = false, isBusy = false), syncSessionContext = false)
                        }
                    }
                }
            }
        }
    }

    private fun mergeAndPublishIncomingState(incoming: HeadsetState) {
        val current = _state.value
        val sameMac = incoming.deviceAddress.equals(current.deviceAddress, ignoreCase = true)
        val userRecent = sameMac && (System.currentTimeMillis() - lastUserModeChangeTimestamp < 5_000L)
        val userForced = getUserForcedModel(incoming.deviceAddress)

        var resolvedCache = incoming.cachedModelFamily ?: getModelForMac(incoming.deviceAddress)
        if (incoming.hardwareModelName.isNotBlank()) {
            val hwFamily = SonyModelFamily.detect(incoming.hardwareModelName, incoming.deviceType)
            if (hwFamily != SonyModelFamily.GENERIC_TWS && hwFamily != SonyModelFamily.GENERIC_OVER_EAR) {
                resolvedCache = hwFamily
                saveModelForMac(incoming.deviceAddress, hwFamily)
            }
        }

        val preservedSameMac = if (sameMac) {
            incoming.copy(
                supportFunctions = if (incoming.supportFunctions.isNotEmpty()) incoming.supportFunctions else current.supportFunctions,
                table2SupportFunctions = if (incoming.table2SupportFunctions.isNotEmpty()) incoming.table2SupportFunctions else current.table2SupportFunctions,
                gsBooleanConfirmed = if (incoming.gsBooleanConfirmed.isNotEmpty()) incoming.gsBooleanConfirmed else current.gsBooleanConfirmed,
                eqState = if (incoming.eqState.isLoaded) incoming.eqState else current.eqState,
                dseeEnabled = incoming.dseeEnabled ?: current.dseeEnabled,
                connectionPriority = incoming.connectionPriority ?: current.connectionPriority,
                autoPowerOff = incoming.autoPowerOff ?: current.autoPowerOff,
                speakToChatEnabled = incoming.speakToChatEnabled ?: current.speakToChatEnabled,
                pauseWhenRemoved = incoming.pauseWhenRemoved ?: current.pauseWhenRemoved,
                voiceGuidanceEnabled = incoming.voiceGuidanceEnabled ?: current.voiceGuidanceEnabled,
                voiceGuidanceLanguage = if (incoming.voiceGuidanceLanguage != VoiceGuidanceLanguageOption.UNDEFINED) incoming.voiceGuidanceLanguage else current.voiceGuidanceLanguage,
                multipointEnabled = incoming.multipointEnabled ?: current.multipointEnabled,
                pairedDevices = if (incoming.pairedDevices.isNotEmpty()) incoming.pairedDevices else current.pairedDevices,
                touchLeftPreset = incoming.touchLeftPreset ?: current.touchLeftPreset,
                touchRightPreset = incoming.touchRightPreset ?: current.touchRightPreset,
                touchThirdPreset = incoming.touchThirdPreset ?: current.touchThirdPreset,
                touchPanelEnabled = incoming.touchPanelEnabled ?: current.touchPanelEnabled
            )
        } else {
            incoming
        }

        val resolvedType = userForced?.defaultDeviceType ?: preservedSameMac.deviceType
        val merged = if (userRecent) {
            preservedSameMac.copy(
                noiseMode = current.noiseMode,
                ambientLevel = current.ambientLevel,
                voiceFocus = current.voiceFocus,
                forcedModelFamily = userForced,
                cachedModelFamily = resolvedCache,
                deviceType = resolvedType,
                isConnected = true,
                isBusy = false
            )
        } else {
            preservedSameMac.copy(
                forcedModelFamily = userForced,
                cachedModelFamily = resolvedCache,
                deviceType = resolvedType,
                isConnected = true,
                isBusy = false
            )
        }
        updateState(merged, syncSessionContext = false)
    }

    private fun loadCachedState(): HeadsetState {
        var devName = prefs.getString(KEY_DEV_NAME, "WF-1000XM6") ?: "WF-1000XM6"
        var devAddr = prefs.getString(KEY_DEV_ADDR, "") ?: ""
        val forcedModel = getUserForcedModel(devAddr)
        val macModel = getModelForMac(devAddr)
        val effectiveFamily = forcedModel ?: macModel

        // Si el dispositivo guardado no es de la gama Sony por nombre, pero sí por MAC o override, conservarlo
        if (!isSonyDeviceName(devName) && effectiveFamily == null) {
            devName = "WF-1000XM6"
            devAddr = ""
        }
        val fallbackType = effectiveFamily?.defaultDeviceType ?: DeviceType.TWS
        val typeStr = prefs.getString(KEY_DEV_TYPE, fallbackType.name) ?: fallbackType.name
        val devType = runCatching { DeviceType.valueOf(typeStr) }.getOrDefault(fallbackType)
        val isConnected = prefs.getBoolean(KEY_IS_CONNECTED, false)

        val leftBat = prefs.getInt(KEY_LEFT_BATTERY, -1)
        val leftChg = prefs.getBoolean(KEY_LEFT_CHARGING, false)
        val rightBat = prefs.getInt(KEY_RIGHT_BATTERY, -1)
        val rightChg = prefs.getBoolean(KEY_RIGHT_CHARGING, false)
        val caseBat = prefs.getInt(KEY_CASE_BATTERY, -1)
        val caseChg = prefs.getBoolean(KEY_CASE_CHARGING, false)
        val singleBat = prefs.getInt(KEY_SINGLE_BATTERY, -1)
        val singleChg = prefs.getBoolean(KEY_SINGLE_CHARGING, false)

        val modeStr = prefs.getString(KEY_NOISE_MODE, NoiseControlMode.ANC.name) ?: NoiseControlMode.ANC.name
        val noiseMode = runCatching { NoiseControlMode.valueOf(modeStr) }.getOrDefault(NoiseControlMode.ANC)
        val ambientLevel = prefs.getInt(KEY_AMBIENT_LEVEL, 20)
        val voiceFocus = prefs.getBoolean(KEY_VOICE_FOCUS, false)
        val lastSync = prefs.getLong(KEY_LAST_SYNC, System.currentTimeMillis())

        val hwModel = getHwModelForMac(devAddr) ?: prefs.getString(KEY_HW_MODEL, "") ?: ""
        val fwVersion = getFwVersionForMac(devAddr) ?: prefs.getString(KEY_FW_VERSION, "") ?: ""
        val protoVersion = getProtoVersionForMac(devAddr) ?: prefs.getInt(KEY_PROTOCOL_VERSION, 2)
        val codecName = prefs.getString(KEY_CODEC_NAME, "") ?: ""
        val ancOverride = when (prefs.getInt(KEY_SUPPORTS_ANC_OVERRIDE, -1)) {
            1 -> true
            0 -> false
            else -> null
        }

        val dynamicAccent = com.nuclear.soundconnect.widget.WidgetPrefsHelper.getAccentColor(context)
        val baseLoaded = HeadsetState(
            deviceName = devName,
            deviceAddress = devAddr,
            isConnected = isConnected,
            deviceType = devType,
            leftBattery = BatteryInfo(leftBat, leftChg),
            rightBattery = BatteryInfo(rightBat, rightChg),
            caseBattery = BatteryInfo(caseBat, caseChg),
            singleBattery = BatteryInfo(singleBat, singleChg),
            noiseMode = noiseMode,
            ambientLevel = ambientLevel,
            voiceFocus = voiceFocus,
            accentColor = dynamicAccent,
            lastSyncTime = lastSync,
            forcedModelFamily = forcedModel,
            cachedModelFamily = macModel,
            hardwareModelName = hwModel,
            firmwareVersion = fwVersion,
            protocolVersion = protoVersion,
            codecName = codecName,
            supportsAncOverride = ancOverride
        )
        return applyExtendedCache(baseLoaded)
    }

    private fun applyExtendedCache(base: HeadsetState): HeadsetState {
        val raw = prefs.getString(KEY_EXTENDED_CACHE_JSON, null) ?: return base
        return runCatching {
            val obj = org.json.JSONObject(raw)
            val cachedMac = obj.optString("mac", "")
            if (cachedMac.isNotBlank() && base.deviceAddress.isNotBlank() &&
                !cachedMac.equals(base.deviceAddress, ignoreCase = true)
            ) {
                return@runCatching base
            }
            val t1Set = mutableSetOf<Int>()
            obj.optJSONArray("t1")?.let { arr ->
                for (i in 0 until arr.length()) t1Set.add(arr.optInt(i))
            }
            val t2Set = mutableSetOf<Int>()
            obj.optJSONArray("t2")?.let { arr ->
                for (i in 0 until arr.length()) t2Set.add(arr.optInt(i))
            }
            val gsSet = mutableSetOf<Int>()
            obj.optJSONArray("gs")?.let { arr ->
                for (i in 0 until arr.length()) gsSet.add(arr.optInt(i))
            }
            val eqBands = mutableListOf<Int>()
            obj.optJSONArray("eqBands")?.let { arr ->
                for (i in 0 until arr.length()) eqBands.add(arr.optInt(i))
            }
            val eqLoaded = obj.optBoolean("eqLoaded", false)
            base.copy(
                supportFunctions = t1Set,
                table2SupportFunctions = t2Set,
                gsBooleanConfirmed = gsSet,
                eqState = if (eqLoaded) {
                    EqState(
                        isSupported = true,
                        isLoaded = true,
                        presetId = obj.optInt("eqPresetId", 0),
                        clearBass = obj.optInt("eqClearBass", 0),
                        bands = eqBands,
                        isTenBand = obj.optBoolean("eqTenBand", false)
                    )
                } else {
                    base.eqState
                },
                dseeEnabled = if (obj.has("dsee")) obj.optBoolean("dsee") else null,
                connectionPriority = obj.optString("connPrio", "")
                    .takeIf { it.isNotBlank() }?.let { runCatching { ConnectionPriority.valueOf(it) }.getOrNull() },
                autoPowerOff = obj.optString("autoOff", "")
                    .takeIf { it.isNotBlank() }?.let { runCatching { AutoPowerOffOption.valueOf(it) }.getOrNull() },
                speakToChatEnabled = if (obj.has("stcEn")) obj.optBoolean("stcEn") else null,
                speakToChatSensitivity = runCatching {
                    SpeakToChatSensitivity.valueOf(obj.optString("stcSens", SpeakToChatSensitivity.AUTO.name))
                }.getOrDefault(SpeakToChatSensitivity.AUTO),
                speakToChatReturnTime = runCatching {
                    SpeakToChatReturnTime.valueOf(obj.optString("stcRet", SpeakToChatReturnTime.MID.name))
                }.getOrDefault(SpeakToChatReturnTime.MID),
                speakToChatFocusOnVoice = obj.optBoolean("stcFocus", false),
                pauseWhenRemoved = if (obj.has("wear")) obj.optBoolean("wear") else null,
                windNoiseReduction = obj.optBoolean("wind", false),
                voiceGuidanceEnabled = if (obj.has("vgEn")) obj.optBoolean("vgEn") else null,
                voiceGuidanceLanguage = runCatching {
                    VoiceGuidanceLanguageOption.valueOf(obj.optString("vgLang", VoiceGuidanceLanguageOption.UNDEFINED.name))
                }.getOrDefault(VoiceGuidanceLanguageOption.UNDEFINED),
                voiceGuidanceVolume = obj.optInt("vgVol", 0).coerceIn(-2, 2),
                multipointEnabled = if (obj.has("mpEn")) obj.optBoolean("mpEn") else null,
                touchLeftPreset = obj.optString("tL", "")
                    .takeIf { it.isNotBlank() }?.let { runCatching { TouchPresetOption.valueOf(it) }.getOrNull() },
                touchRightPreset = obj.optString("tR", "")
                    .takeIf { it.isNotBlank() }?.let { runCatching { TouchPresetOption.valueOf(it) }.getOrNull() },
                touchThirdPreset = obj.optString("t3", "")
                    .takeIf { it.isNotBlank() }?.let { runCatching { TouchPresetOption.valueOf(it) }.getOrNull() },
                touchPanelEnabled = if (obj.has("tPanel")) obj.optBoolean("tPanel") else null
            )
        }.getOrDefault(base)
    }

    private fun saveExtendedCacheJson(s: HeadsetState): String {
        return org.json.JSONObject().apply {
            put("mac", s.deviceAddress)
            put("t1", org.json.JSONArray(s.supportFunctions.toList()))
            put("t2", org.json.JSONArray(s.table2SupportFunctions.toList()))
            put("gs", org.json.JSONArray(s.gsBooleanConfirmed.toList()))
            put("eqLoaded", s.eqState.isLoaded)
            put("eqPresetId", s.eqState.presetId)
            put("eqClearBass", s.eqState.clearBass)
            put("eqTenBand", s.eqState.isTenBand)
            put("eqBands", org.json.JSONArray(s.eqState.bands))
            s.dseeEnabled?.let { put("dsee", it) }
            s.connectionPriority?.let { put("connPrio", it.name) }
            s.autoPowerOff?.let { put("autoOff", it.name) }
            s.speakToChatEnabled?.let { put("stcEn", it) }
            put("stcSens", s.speakToChatSensitivity.name)
            put("stcRet", s.speakToChatReturnTime.name)
            put("stcFocus", s.speakToChatFocusOnVoice)
            s.pauseWhenRemoved?.let { put("wear", it) }
            put("wind", s.windNoiseReduction)
            s.voiceGuidanceEnabled?.let { put("vgEn", it) }
            put("vgLang", s.voiceGuidanceLanguage.name)
            put("vgVol", s.voiceGuidanceVolume)
            s.multipointEnabled?.let { put("mpEn", it) }
            s.touchLeftPreset?.let { put("tL", it.name) }
            s.touchRightPreset?.let { put("tR", it.name) }
            s.touchThirdPreset?.let { put("t3", it.name) }
            s.touchPanelEnabled?.let { put("tPanel", it) }
        }.toString()
    }

    private fun saveCachedState(s: HeadsetState) {
        prefs.edit().apply {
            putString(KEY_DEV_NAME, s.deviceName)
            putString(KEY_DEV_ADDR, s.deviceAddress)
            putString(KEY_DEV_TYPE, s.deviceType.name)
            putBoolean(KEY_IS_CONNECTED, s.isConnected)
            putInt(KEY_LEFT_BATTERY, s.leftBattery.level)
            putBoolean(KEY_LEFT_CHARGING, s.leftBattery.isCharging)
            putInt(KEY_RIGHT_BATTERY, s.rightBattery.level)
            putBoolean(KEY_RIGHT_CHARGING, s.rightBattery.isCharging)
            putInt(KEY_CASE_BATTERY, s.caseBattery.level)
            putBoolean(KEY_CASE_CHARGING, s.caseBattery.isCharging)
            putInt(KEY_SINGLE_BATTERY, s.singleBattery.level)
            putBoolean(KEY_SINGLE_CHARGING, s.singleBattery.isCharging)
            putString(KEY_NOISE_MODE, s.noiseMode.name)
            putInt(KEY_AMBIENT_LEVEL, s.ambientLevel)
            putBoolean(KEY_VOICE_FOCUS, s.voiceFocus)
            putLong(KEY_LAST_SYNC, s.lastSyncTime)
            putString(KEY_HW_MODEL, s.hardwareModelName)
            putString(KEY_FW_VERSION, s.firmwareVersion)
            putInt(KEY_PROTOCOL_VERSION, s.protocolVersion)
            putString(KEY_CODEC_NAME, s.codecName)
            putInt(
                KEY_SUPPORTS_ANC_OVERRIDE,
                when (s.supportsAncOverride) {
                    true -> 1
                    false -> 0
                    null -> -1
                }
            )
            putString(KEY_EXTENDED_CACHE_JSON, saveExtendedCacheJson(s))
            apply()
        }
        if (s.deviceAddress.isNotBlank()) {
            if (s.hardwareModelName.isNotBlank()) saveHwModelForMac(s.deviceAddress, s.hardwareModelName)
            if (s.firmwareVersion.isNotBlank()) saveFwVersionForMac(s.deviceAddress, s.firmwareVersion)
            saveProtoVersionForMac(s.deviceAddress, s.protocolVersion)
        }
    }

    private fun updateState(newState: HeadsetState, syncSessionContext: Boolean = true) {
        val dynamicAccent = com.nuclear.soundconnect.widget.WidgetPrefsHelper.getAccentColor(context)
        val stateWithAccent = if (newState.accentColor == 0xFF00E676.toInt() && dynamicAccent != 0xFF00E676.toInt()) {
            newState.copy(accentColor = dynamicAccent)
        } else {
            newState
        }
        _state.value = stateWithAccent
        saveCachedState(stateWithAccent)
        audioCodecMonitor.refresh(stateWithAccent)
        if (stateWithAccent.isConnected && stateWithAccent.deviceAddress.isNotBlank()) {
            recordDeviceInHistory(stateWithAccent)
        }
        if (syncSessionContext) {
            rfcommClient.updateSessionContext(stateWithAccent)
        }
        NuclearWidgetManager.updateAllWidgets(context)
        NuclearPhoneWearableSync.syncStateToWear(context, stateWithAccent)
        NuclearQuickSettingsTileService.requestRefresh(context)
    }

    @SuppressLint("MissingPermission")
    fun getTargetBluetoothDevice(): BluetoothDevice? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        val preferred = prefs.getString(KEY_DEV_ADDR, null)
        return rfcommClient.findTargetDevice(adapter, preferred)
    }

    fun getSystemBatteryLevel(device: BluetoothDevice): Int {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            (method.invoke(device) as? Int) ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isDeviceBtConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            (method.invoke(device) as? Boolean) ?: false
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun buildBaseStateForDevice(device: BluetoothDevice): HeadsetState {
        val current = _state.value
        val sameDevice = current.deviceAddress.equals(device.address, ignoreCase = true)
        val userForced = getUserForcedModel(device.address)
        val cachedMac = getModelForMac(device.address)
        val devName = try { device.name ?: "Sony Headphones" } catch (_: SecurityException) { "Sony Headphones" }

        val hwModel = if (sameDevice && current.hardwareModelName.isNotBlank()) {
            current.hardwareModelName
        } else {
            getHwModelForMac(device.address) ?: ""
        }
        val fwVersion = if (sameDevice && current.firmwareVersion.isNotBlank()) {
            current.firmwareVersion
        } else {
            getFwVersionForMac(device.address) ?: ""
        }
        val protoVersion = getProtoVersionForMac(device.address) ?: if (sameDevice) current.protocolVersion else 2

        val hwDetected = if (hwModel.isNotBlank()) {
            SonyModelFamily.detect(hwModel, cachedMac?.defaultDeviceType ?: DeviceType.TWS)
        } else null
        val effectiveFamily = userForced
            ?: hwDetected?.takeIf { it != SonyModelFamily.GENERIC_TWS && it != SonyModelFamily.GENERIC_OVER_EAR }
            ?: cachedMac
            ?: SonyModelFamily.detect(devName)

        if (cachedMac == null && userForced == null && (isSonyDeviceName(devName) || hwModel.isNotBlank())) {
            saveModelForMac(device.address, effectiveFamily)
        }

        val base = if (sameDevice) {
            current
        } else {
            current.copy(
                leftBattery = BatteryInfo(-1, false),
                rightBattery = BatteryInfo(-1, false),
                caseBattery = BatteryInfo(-1, false),
                singleBattery = BatteryInfo(-1, false),
                codecName = "",
                supportFunctions = emptySet(),
                table2SupportFunctions = emptySet(),
                gsBooleanConfirmed = emptySet(),
                supportsAncOverride = null,
                eqState = EqState(),
                dseeEnabled = null,
                connectionPriority = null,
                autoPowerOff = null,
                speakToChatEnabled = null,
                speakToChatSensitivity = SpeakToChatSensitivity.AUTO,
                speakToChatReturnTime = SpeakToChatReturnTime.MID,
                speakToChatFocusOnVoice = false,
                pauseWhenRemoved = null,
                windNoiseReduction = false,
                voiceGuidanceEnabled = null,
                voiceGuidanceLanguage = VoiceGuidanceLanguageOption.UNDEFINED,
                voiceGuidanceVolume = 0,
                multipointEnabled = null,
                pairedDevices = emptyList(),
                touchLeftPreset = null,
                touchRightPreset = null,
                touchThirdPreset = null,
                touchPanelEnabled = null
            )
        }

        return base.copy(
            deviceName = devName,
            deviceAddress = device.address,
            deviceType = effectiveFamily.defaultDeviceType,
            forcedModelFamily = userForced,
            cachedModelFamily = cachedMac,
            hardwareModelName = hwModel,
            firmwareVersion = fwVersion,
            protocolVersion = protoVersion
        )
    }

    @Volatile
    private var monitoringStarted = false

    fun startInteractiveMonitoring() {
        if (monitoringStarted) return
        monitoringStarted = true
        scope.launch {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            while (isActive) {
                if (!hasBluetoothConnectPermission()) {
                    delay(5000L)
                    continue
                }
                val isScreenOn = powerManager?.isInteractive ?: true
                if (!isScreenOn) {
                    delay(15_000L)
                    continue
                }

                val targetDevice = getTargetBluetoothDevice()
                val btConnected = targetDevice != null && isDeviceBtConnected(targetDevice)
                if (targetDevice != null && btConnected) {
                    if (!rfcommClient.isSessionActive(targetDevice.address)) {
                        val base = buildBaseStateForDevice(targetDevice)
                        val openRes = rfcommClient.ensurePersistentSession(targetDevice, base)
                        if (openRes.isSuccess) {
                            mergeAndPublishIncomingState(openRes.getOrThrow())
                            maybeAutoSyncExtended(targetDevice.address)
                            localAutomationEngine.evaluateScheduleTick()
                            delay(45_000L)
                        } else {
                            delay(8_000L)
                        }
                    } else {
                        // La sesión persistente recibe push en tiempo real (0x25 / 0x69 / 0x15);
                        // solo refrescamos ligeramente cada 45s como respaldo para V1 sin push automático
                        val timeSinceUserChange = System.currentTimeMillis() - lastUserModeChangeTimestamp
                        if (timeSinceUserChange >= 5_000L) {
                            syncTelemetryAsync(silent = true)
                        }
                        localAutomationEngine.evaluateScheduleTick()
                        delay(45_000L)
                    }
                } else {
                    if (_state.value.isConnected) {
                        saveDisconnectedSnapshot(_state.value)
                        updateState(_state.value.copy(isConnected = false, isBusy = false), syncSessionContext = false)
                    }
                    delay(5_000L)
                }
            }
        }
    }

    /**
     * Sincroniza la batería y modo de ruido en segundo plano.
     */
    fun syncTelemetryAsync(silent: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        val timeSinceUserChange = System.currentTimeMillis() - lastUserModeChangeTimestamp
        if (silent && timeSinceUserChange < 5_000L) {
            onComplete?.invoke(true)
            return
        }

        activeSyncJob?.cancel()
        activeSyncJob = scope.launch {
            if (!hasBluetoothConnectPermission()) {
                onComplete?.invoke(false)
                return@launch
            }
            val device = getTargetBluetoothDevice()
            if (device == null) {
                updateState(_state.value.copy(isConnected = false, isBusy = false), syncSessionContext = false)
                onComplete?.invoke(false)
                return@launch
            }

            if (!silent) {
                _state.value = _state.value.copy(isBusy = true)
                NuclearWidgetManager.updateAllWidgets(context)
            }

            var baseState = buildBaseStateForDevice(device)
            val sysBat = getSystemBatteryLevel(device)

            // Actualización instantánea a 0ms para diademas con porcentaje de sistema Bluetooth
            if (baseState.deviceType == DeviceType.OVER_EAR && sysBat in 0..100) {
                baseState = baseState.copy(
                    singleBattery = BatteryInfo(sysBat, false),
                    isConnected = true
                )
                updateState(baseState)
            }

            val result = rfcommClient.syncTelemetry(device, baseState)
            if (result.isSuccess) {
                mergeAndPublishIncomingState(result.getOrThrow())
                maybeAutoSyncExtended(device.address)
                localAutomationEngine.evaluateScheduleTick()
                onComplete?.invoke(true)
            } else {
                updateState(_state.value.copy(isBusy = false))
                onComplete?.invoke(false)
            }
        }
    }

    private fun maybeAutoSyncExtended(address: String) {
        val now = System.currentTimeMillis()
        if (!lastExtendedSyncMac.equals(address, ignoreCase = true) || now - lastExtendedSyncAt > 120_000L) {
            lastExtendedSyncMac = address
            lastExtendedSyncAt = now
            syncExtendedControlsAsync()
        }
    }

    /**
     * Cambia el modo de cancelación de ruido de forma asíncrona.
     */
    fun setNoiseModeAsync(mode: NoiseControlMode, onComplete: ((Boolean) -> Unit)? = null) {
        if (!_state.value.effectiveHasAnc) {
            onComplete?.invoke(false)
            return
        }
        lastUserModeChangeTimestamp = System.currentTimeMillis()
        localAutomationEngine.notifyUserManualNoiseChange()

        // Prioridad máxima del usuario: Cancelar sondeo en segundo plano para no bloquear el socket RFCOMM
        activeSyncJob?.cancel()

        // Optimistic UI update para respuesta inmediata en la app y el Widget
        val previousState = _state.value
        val optimisticState = previousState.copy(noiseMode = mode, isBusy = true)
        updateState(optimisticState)

        activeModeChangeJob?.cancel()
        activeModeChangeJob = scope.launch {
            val device = getTargetBluetoothDevice()
            if (device == null) {
                updateState(previousState.copy(isBusy = false))
                onComplete?.invoke(false)
                return@launch
            }

            val result = rfcommClient.setNoiseMode(
                device = device,
                mode = mode,
                ambientLevel = optimisticState.ambientLevel,
                voiceFocus = optimisticState.voiceFocus,
                currentState = optimisticState
            )

            if (result.isSuccess) {
                // Confirmamos el modo explícito sin dejar que ningún paquete previo de query lo revierta
                updateState(result.getOrThrow().copy(
                    noiseMode = mode,
                    ambientLevel = optimisticState.ambientLevel,
                    voiceFocus = optimisticState.voiceFocus,
                    isBusy = false
                ))
                onComplete?.invoke(true)
            } else {
                // En caso de fallo en el socket, conservamos el modo visualmente ya que puede ser desconexión transitoria
                updateState(optimisticState.copy(isBusy = false))
                onComplete?.invoke(false)
            }
        }
    }

    fun setAmbientLevel(level: Int) {
        val clamped = level.coerceIn(1, 20)
        val updated = _state.value.copy(ambientLevel = clamped)
        updateState(updated)

        if (updated.noiseMode == NoiseControlMode.AMBIENT) {
            setNoiseModeAsync(NoiseControlMode.AMBIENT)
        }
    }

    fun setVoiceFocus(enabled: Boolean) {
        val updated = _state.value.copy(voiceFocus = enabled)
        updateState(updated)

        if (updated.noiseMode == NoiseControlMode.AMBIENT) {
            setNoiseModeAsync(NoiseControlMode.AMBIENT)
        }
    }

    fun setAmbientConfig(level: Int, voiceFocus: Boolean) {
        val clamped = level.coerceIn(1, 20)
        val updated = _state.value.copy(ambientLevel = clamped, voiceFocus = voiceFocus)
        updateState(updated)

        if (updated.noiseMode == NoiseControlMode.AMBIENT) {
            setNoiseModeAsync(NoiseControlMode.AMBIENT)
        }
    }

    fun syncAccentColor(color: Int) {
        val updated = _state.value.copy(accentColor = color)
        _state.value = updated
        saveCachedState(updated)
        NuclearPhoneWearableSync.syncStateToWear(context, updated)
    }

    fun setPreferredDevice(device: BluetoothDevice) {
        if (!isSonyAudioDevice(device)) return
        val switchingMac = !_state.value.deviceAddress.equals(device.address, ignoreCase = true)
        if (switchingMac) {
            scope.launch { rfcommClient.closePersistentSession("Cambio de dispositivo preferido") }
        }
        val updated = buildBaseStateForDevice(device)
        updateState(updated)
        syncTelemetryAsync()
    }

    /**
     * Fallback: Recibe actualizaciones públicas de batería del sistema operativo
     * (android.bluetooth.device.action.BATTERY_LEVEL_CHANGED).
     */
    fun handleSystemBatteryUpdate(device: BluetoothDevice, level: Int, intent: Intent) {
        if (!isSonyAudioDevice(device)) return
        val current = _state.value
        // Si el socket RFCOMM no está activo o falló, usamos el broadcast del sistema
        if (level in 0..100) {
            val updated = if (current.deviceType == DeviceType.OVER_EAR) {
                current.copy(
                    isConnected = true,
                    singleBattery = BatteryInfo(level, false),
                    lastSyncTime = System.currentTimeMillis()
                )
            } else {
                // Para TWS Sony: NO usar `level` genérico de Android como fallback para ambos auriculares
                // Si Android no provee extras específicos de canal, se preserva el estado individual de Sony MDR
                val leftExtra = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL_LEFT", -1)
                val rightExtra = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL_RIGHT", -1)
                val caseExtra = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL_CASE", -1)

                val newLeft = if (leftExtra in 0..100) BatteryInfo(leftExtra, false) else current.leftBattery
                val newRight = if (rightExtra in 0..100) BatteryInfo(rightExtra, false) else current.rightBattery
                val newCase = if (caseExtra in 0..100) BatteryInfo(caseExtra, false) else current.caseBattery

                current.copy(
                    isConnected = true,
                    leftBattery = newLeft,
                    rightBattery = newRight,
                    caseBattery = newCase,
                    singleBattery = BatteryInfo(level, false),
                    lastSyncTime = System.currentTimeMillis()
                )
            }
            updateState(updated)
            // Al cambiar la batería de sistema (ej. al acoplar o sacar un segundo audífono del estuche),
            // consultar los canales individuales L/R/Estuche por RFCOMM inmediatamente
            syncTelemetryAsync(silent = true)
        }
    }

    fun handleAclConnectionChange(device: BluetoothDevice, connected: Boolean) {
        // RESTRICCIÓN ESTRICTA A SONY: Ignorar tablets, PCs, autos, TVs u otros dispositivos
        if (!isSonyAudioDevice(device)) {
            return
        }

        val current = _state.value
        if (connected) {
            val newState = buildBaseStateForDevice(device).let { base ->
                val sysBat = getSystemBatteryLevel(device)
                val initialSingle = if (base.deviceType == DeviceType.OVER_EAR && sysBat in 0..100) {
                    BatteryInfo(sysBat, false)
                } else base.singleBattery
                base.copy(isConnected = true, singleBattery = initialSingle)
            }
            updateState(newState)
            recordDeviceInHistory(newState)
            scope.launch {
                val res = rfcommClient.ensurePersistentSession(device, newState)
                if (res.isSuccess) {
                    mergeAndPublishIncomingState(res.getOrThrow())
                } else {
                    syncTelemetryAsync()
                }
            }
        } else {
            if (device.address.equals(current.deviceAddress, ignoreCase = true)) {
                scope.launch { rfcommClient.closePersistentSession("BT ACL Desconectado") }
                saveDisconnectedSnapshot(current)
                recordDeviceInHistory(current)
                updateState(current.copy(isConnected = false, isBusy = false), syncSessionContext = false)
            }
        }
    }

    fun getDeviceHistory(): List<DeviceBatteryRecord> {
        val raw = prefs.getString("all_device_history", null) ?: return emptyList()
        val list = mutableListOf<DeviceBatteryRecord>()
        try {
            val jsonArray = org.json.JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val typeStr = obj.optString("type", DeviceType.TWS.name)
                val type = runCatching { DeviceType.valueOf(typeStr) }.getOrDefault(DeviceType.TWS)
                list.add(
                    DeviceBatteryRecord(
                        address = obj.optString("address", ""),
                        name = obj.optString("name", "Audífonos"),
                        deviceType = type,
                        leftLevel = obj.optInt("left", -1),
                        rightLevel = obj.optInt("right", -1),
                        caseLevel = obj.optInt("case", -1),
                        singleLevel = obj.optInt("single", -1),
                        lastSeenTimestamp = obj.optLong("time", 0L)
                    )
                )
            }
        } catch (_: Exception) {}
        return list.sortedByDescending { it.lastSeenTimestamp }
    }

    fun recordDeviceInHistory(s: HeadsetState) {
        if (s.deviceAddress.isBlank()) return
        val currentHistory = getDeviceHistory().toMutableList()
        val existingIndex = currentHistory.indexOfFirst { it.address.equals(s.deviceAddress, ignoreCase = true) }
        val newRecord = DeviceBatteryRecord(
            address = s.deviceAddress,
            name = s.deviceName,
            deviceType = s.deviceType,
            leftLevel = s.leftBattery.level,
            rightLevel = s.rightBattery.level,
            caseLevel = s.caseBattery.level,
            singleLevel = s.singleBattery.level,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        if (existingIndex >= 0) {
            currentHistory[existingIndex] = newRecord
        } else {
            currentHistory.add(0, newRecord)
        }
        val jsonArray = org.json.JSONArray()
        for (item in currentHistory) {
            val obj = org.json.JSONObject().apply {
                put("address", item.address)
                put("name", item.name)
                put("type", item.deviceType.name)
                put("left", item.leftLevel)
                put("right", item.rightLevel)
                put("case", item.caseLevel)
                put("single", item.singleLevel)
                put("time", item.lastSeenTimestamp)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("all_device_history", jsonArray.toString()).apply()
    }

    private fun saveDisconnectedSnapshot(s: HeadsetState) {
        prefs.edit().apply {
            putLong("last_disc_time", System.currentTimeMillis())
            putString("last_disc_name", s.deviceName)
            putString("last_disc_type", s.deviceType.name)
            putInt("last_disc_left", s.leftBattery.level)
            putInt("last_disc_right", s.rightBattery.level)
            putInt("last_disc_case", s.caseBattery.level)
            putInt("last_disc_single", s.singleBattery.level)
            apply()
        }
    }

    fun getLastKnownCharge(): DisconnectedHistory? {
        val ts = prefs.getLong("last_disc_time", 0L)
        if (ts <= 0L) return null
        val name = prefs.getString("last_disc_name", "WF-1000XM6") ?: "WF-1000XM6"
        val typeStr = prefs.getString("last_disc_type", DeviceType.TWS.name) ?: DeviceType.TWS.name
        val type = runCatching { DeviceType.valueOf(typeStr) }.getOrDefault(DeviceType.TWS)
        return DisconnectedHistory(
            deviceName = name,
            deviceType = type,
            leftLevel = prefs.getInt("last_disc_left", 0),
            rightLevel = prefs.getInt("last_disc_right", 0),
            caseLevel = prefs.getInt("last_disc_case", 0),
            singleLevel = prefs.getInt("last_disc_single", 0),
            timestamp = ts
        )
    }

    // =========================================================================
    // FASE 2 & FASE 3: CONTROLES EXTENDIDOS, AUTOMATIZACIÓN Y RESPALDO LOCAL
    // =========================================================================

    private fun runDeviceCommand(
        optimisticUpdate: ((HeadsetState) -> HeadsetState)? = null,
        revertOnFailure: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null,
        block: suspend (BluetoothDevice, HeadsetState) -> Result<HeadsetState>
    ) {
        if (!hasBluetoothConnectPermission()) {
            onComplete?.invoke(false)
            return
        }
        val previous = _state.value
        val optimistic = optimisticUpdate?.invoke(previous) ?: previous
        if (optimisticUpdate != null) {
            updateState(optimistic.copy(isBusy = true))
        }
        activeSyncJob?.cancel()
        scope.launch {
            val device = getTargetBluetoothDevice()
            if (device == null) {
                if (optimisticUpdate != null && revertOnFailure) {
                    updateState(previous.copy(isBusy = false))
                } else {
                    updateState(_state.value.copy(isBusy = false))
                }
                onComplete?.invoke(false)
                return@launch
            }
            val result = block(device, optimistic)
            if (result.isSuccess) {
                mergeAndPublishIncomingState(result.getOrThrow())
                onComplete?.invoke(true)
            } else {
                if (optimisticUpdate != null && revertOnFailure) {
                    updateState(previous.copy(isBusy = false))
                } else {
                    updateState(_state.value.copy(isBusy = false))
                }
                onComplete?.invoke(false)
            }
        }
    }

    private fun applyAutomationNoiseMode(mode: NoiseControlMode, ambientLevel: Int, voiceFocus: Boolean) {
        if (!_state.value.effectiveHasAnc || !hasBluetoothConnectPermission()) return
        scope.launch {
            val device = getTargetBluetoothDevice() ?: return@launch
            val snap = _state.value.copy(noiseMode = mode, ambientLevel = ambientLevel, voiceFocus = voiceFocus)
            updateState(snap)
            val res = rfcommClient.setNoiseMode(device, mode, ambientLevel, voiceFocus, snap)
            if (res.isSuccess) {
                mergeAndPublishIncomingState(res.getOrThrow().copy(noiseMode = mode, ambientLevel = ambientLevel, voiceFocus = voiceFocus))
            }
        }
    }

    fun syncExtendedControlsAsync(onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(onComplete = onComplete) { device, current ->
            rfcommClient.syncExtendedControls(device, current)
        }
    }

    fun setEqualizerPresetAsync(presetId: Int, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(eqState = it.eqState.copy(isSupported = true, isLoaded = true, presetId = presetId)) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setEqualizerPreset(device, current, presetId)
        }
    }

    fun setEqualizerCustomAsync(clearBass: Int, bands: List<Int>, onComplete: ((Boolean) -> Unit)? = null) {
        val clampedCb = clearBass.coerceIn(-10, 10)
        runDeviceCommand(
            optimisticUpdate = {
                val clampedBands = if (it.capabilities.tenBandEq) {
                    bands.map { b -> b.coerceIn(0, 12) }
                } else {
                    bands.map { b -> b.coerceIn(-10, 10) }
                }
                it.copy(
                    eqState = EqState(
                        isSupported = true,
                        isLoaded = true,
                        presetId = EqPreset.CUSTOM.id,
                        clearBass = if (it.capabilities.tenBandEq) 0 else clampedCb,
                        bands = clampedBands,
                        isTenBand = it.capabilities.tenBandEq
                    )
                )
            },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setEqualizerCustom(device, current, clampedCb, bands)
        }
    }

    fun setDseeEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(dseeEnabled = enabled) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setDseeEnabled(device, current, enabled)
        }
    }

    fun setConnectionPriorityAsync(priority: ConnectionPriority, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(connectionPriority = priority) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setConnectionPriority(device, current, priority)
        }
    }

    fun setAutoPowerOffAsync(option: AutoPowerOffOption, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(autoPowerOff = option) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setAutoPowerOff(device, current, option)
        }
    }

    fun setSpeakToChatEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(speakToChatEnabled = enabled) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setSpeakToChatEnabled(device, current, enabled)
        }
    }

    fun setSpeakToChatConfigAsync(
        sensitivity: SpeakToChatSensitivity,
        returnTime: SpeakToChatReturnTime,
        focusOnVoice: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        runDeviceCommand(
            optimisticUpdate = {
                it.copy(
                    speakToChatSensitivity = sensitivity,
                    speakToChatReturnTime = returnTime,
                    speakToChatFocusOnVoice = focusOnVoice
                )
            },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setSpeakToChatConfig(device, current, sensitivity, returnTime, focusOnVoice)
        }
    }

    fun setPauseWhenRemovedAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(pauseWhenRemoved = enabled) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setPauseWhenRemoved(device, current, enabled)
        }
    }

    fun setWindNoiseReductionAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(windNoiseReduction = enabled) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setWindNoiseReduction(device, current, enabled)
        }
    }

    fun setTouchAssignmentAsync(
        left: TouchPresetOption,
        right: TouchPresetOption,
        third: TouchPresetOption? = _state.value.touchThirdPreset,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val list = buildList {
            add(left)
            add(right)
            if (third != null) add(third)
        }
        runDeviceCommand(
            optimisticUpdate = {
                it.copy(touchLeftPreset = left, touchRightPreset = right, touchThirdPreset = third)
            },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setTouchAssignment(device, current, list)
        }
    }

    fun setTouchPanelEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(touchPanelEnabled = enabled) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setTouchPanelEnabled(device, current, enabled)
        }
    }

    fun setMultipointEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(multipointEnabled = enabled) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setMultipointEnabled(device, current, enabled)
        }
    }

    fun setMultipointDeviceConnectionAsync(targetMac: String, connect: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(onComplete = onComplete) { device, current ->
            rfcommClient.setMultipointDeviceConnection(device, current, targetMac, connect)
        }
    }

    fun switchMultipointActiveSourceAsync(targetMac: String, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(onComplete = onComplete) { device, current ->
            rfcommClient.switchMultipointActiveSource(device, current, targetMac)
        }
    }

    fun setVoiceGuidanceAsync(
        enabled: Boolean,
        language: VoiceGuidanceLanguageOption,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(voiceGuidanceEnabled = enabled, voiceGuidanceLanguage = language) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setVoiceGuidance(device, current, enabled, language)
        }
    }

    fun setVoiceGuidanceVolumeAsync(volume: Int, onComplete: ((Boolean) -> Unit)? = null) {
        val clamped = volume.coerceIn(-2, 2)
        runDeviceCommand(
            optimisticUpdate = { it.copy(voiceGuidanceVolume = clamped) },
            onComplete = onComplete
        ) { device, current ->
            rfcommClient.setVoiceGuidanceVolume(device, current, clamped)
        }
    }

    fun remotePowerOffAsync(onComplete: ((Boolean) -> Unit)? = null) {
        val snap = _state.value
        if (snap.protocolVersion < 2 || !snap.capabilities.supportsRemotePowerOff || !hasBluetoothConnectPermission()) {
            onComplete?.invoke(false)
            return
        }
        activeSyncJob?.cancel()
        scope.launch {
            val device = getTargetBluetoothDevice()
            if (device == null) {
                onComplete?.invoke(false)
                return@launch
            }
            val res = rfcommClient.remotePowerOff(device, snap)
            if (res.isSuccess) {
                saveDisconnectedSnapshot(snap)
                updateState(snap.copy(isConnected = false, isBusy = false), syncSessionContext = false)
                onComplete?.invoke(true)
            } else {
                onComplete?.invoke(false)
            }
        }
    }

    fun refreshCodecHud() {
        audioCodecMonitor.refresh(_state.value)
    }

    fun startFindMyBuds(side: EarSide) {
        findMyBudsPlayer.start(side)
    }

    fun stopFindMyBuds() {
        findMyBudsPlayer.stop()
    }

    fun updateAutomationConfig(config: LocalAutomationConfig) {
        localAutomationEngine.updateConfig(config)
    }

    fun exportBackupJson(): String {
        val bundle = ProfileBackupManager.createBundleFromState(
            state = _state.value,
            automationConfig = localAutomationEngine.config.value,
            languageCode = _language.value.code
        )
        return ProfileBackupManager.exportJson(bundle)
    }

    fun restoreBackupJson(rawJson: String, onApplied: ((Boolean) -> Unit)? = null): Result<NuclearBackupBundle> {
        val parsed = ProfileBackupManager.parseBackupJson(rawJson)
        if (parsed.isFailure) {
            onApplied?.invoke(false)
            return parsed
        }
        val bundle = parsed.getOrThrow()

        setLanguage(com.nuclear.soundconnect.ui.AppLanguage.fromCode(bundle.languageCode))
        localAutomationEngine.updateConfig(bundle.automationConfig)

        val accentIdx = findAccentIndex(bundle.accentColorArgb)
        context.getSharedPreferences("nuclear_app_theme_prefs", Context.MODE_PRIVATE)
            .edit().putInt("accent_idx", accentIdx).apply()
        syncAccentColor(bundle.accentColorArgb)
        NuclearWidgetManager.updateAllWidgets(context)

        val cur = _state.value
        if (bundle.forcedModelFamily != null) {
            setUserForcedModel(cur.deviceAddress.takeIf { it.isNotBlank() }, bundle.forcedModelFamily)
        }

        val updated = _state.value.copy(
            noiseMode = bundle.noiseMode,
            ambientLevel = bundle.ambientLevel,
            voiceFocus = bundle.voiceFocus,
            windNoiseReduction = bundle.windNoiseReduction,
            eqState = if (bundle.eqBands.isNotEmpty() || bundle.eqPresetId != 0) {
                EqState(
                    isSupported = true,
                    isLoaded = true,
                    presetId = bundle.eqPresetId,
                    clearBass = bundle.eqClearBass,
                    bands = bundle.eqBands,
                    isTenBand = bundle.eqIsTenBand
                )
            } else {
                _state.value.eqState
            },
            dseeEnabled = bundle.dseeEnabled ?: _state.value.dseeEnabled,
            connectionPriority = bundle.connectionPriority ?: _state.value.connectionPriority,
            autoPowerOff = bundle.autoPowerOff ?: _state.value.autoPowerOff,
            speakToChatEnabled = bundle.speakToChatEnabled ?: _state.value.speakToChatEnabled,
            speakToChatSensitivity = bundle.speakToChatSensitivity,
            speakToChatReturnTime = bundle.speakToChatReturnTime,
            speakToChatFocusOnVoice = bundle.speakToChatFocusOnVoice,
            pauseWhenRemoved = bundle.pauseWhenRemoved ?: _state.value.pauseWhenRemoved,
            touchLeftPreset = bundle.touchLeftPreset ?: _state.value.touchLeftPreset,
            touchRightPreset = bundle.touchRightPreset ?: _state.value.touchRightPreset,
            touchPanelEnabled = bundle.touchPanelEnabled ?: _state.value.touchPanelEnabled
        )
        updateState(updated)

        if (hasBluetoothConnectPermission()) {
            val device = getTargetBluetoothDevice()
            if (device != null && updated.isConnected) {
                scope.launch {
                    val caps = updated.capabilities
                    if (updated.effectiveHasAnc) {
                        rfcommClient.setNoiseMode(device, updated.noiseMode, updated.ambientLevel, updated.voiceFocus, updated)
                    }
                    if (caps.supportsEq) {
                        if (updated.eqState.presetId == EqPreset.CUSTOM.id && updated.eqState.bands.isNotEmpty()) {
                            rfcommClient.setEqualizerCustom(device, updated, updated.eqState.clearBass, updated.eqState.bands)
                        } else {
                            rfcommClient.setEqualizerPreset(device, updated, updated.eqState.presetId)
                        }
                    }
                    if (caps.supportsDsee && bundle.dseeEnabled != null) {
                        rfcommClient.setDseeEnabled(device, updated, bundle.dseeEnabled)
                    }
                    onApplied?.invoke(true)
                }
                return parsed
            }
        }
        onApplied?.invoke(true)
        return parsed
    }

    private fun findAccentIndex(colorArgb: Int): Int {
        val palette = intArrayOf(
            0xFF00E676.toInt(),
            0xFF10B981.toInt(),
            0xFF00B0FF.toInt(),
            0xFF007AFF.toInt(),
            0xFF00D2FF.toInt(),
            0xFFFFB703.toInt(),
            0xFFFFB300.toInt(),
            0xFFFF5722.toInt(),
            0xFFFF1744.toInt(),
            0xFFFF007F.toInt(),
            0xFFA855F7.toInt(),
            0xFF8B5CF6.toInt()
        )
        return palette.indexOf(colorArgb).takeIf { it >= 0 } ?: 0
    }
}

data class DisconnectedHistory(
    val deviceName: String,
    val deviceType: DeviceType,
    val leftLevel: Int,
    val rightLevel: Int,
    val caseLevel: Int,
    val singleLevel: Int,
    val timestamp: Long
)

data class DeviceBatteryRecord(
    val address: String,
    val name: String,
    val deviceType: DeviceType,
    val leftLevel: Int,
    val rightLevel: Int,
    val caseLevel: Int,
    val singleLevel: Int,
    val lastSeenTimestamp: Long
)
