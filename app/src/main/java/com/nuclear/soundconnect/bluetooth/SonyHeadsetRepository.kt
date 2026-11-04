package com.nuclear.soundconnect.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.nuclear.soundconnect.widget.NuclearWidgetManager
import com.nuclear.soundconnect.widget.SonyBudsWidgetProvider
import com.nuclear.soundconnect.wear.NuclearPhoneWearableSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private const val PREF_PREFIX_MAC_MODEL = "mac_model_"
        private const val PREF_PREFIX_FORCED_MODEL = "forced_model_"
        private const val KEY_GLOBAL_FORCED_MODEL = "global_forced_model"

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
        val effectiveFamily = family ?: (if (!address.isNullOrBlank()) getModelForMac(address) else null)
        val newType = effectiveFamily?.defaultDeviceType ?: current.deviceType
        updateState(current.copy(
            forcedModelFamily = family,
            deviceType = newType
        ))
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

    private val _state = MutableStateFlow(loadCachedState())
    val state: StateFlow<HeadsetState> = _state.asStateFlow()

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

        val dynamicAccent = com.nuclear.soundconnect.widget.WidgetPrefsHelper.getAccentColor(context)
        return HeadsetState(
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
            forcedModelFamily = effectiveFamily
        )
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
            apply()
        }
    }

    private fun updateState(newState: HeadsetState) {
        val dynamicAccent = com.nuclear.soundconnect.widget.WidgetPrefsHelper.getAccentColor(context)
        val stateWithAccent = if (newState.accentColor == 0xFF00E676.toInt() && dynamicAccent != 0xFF00E676.toInt()) {
            newState.copy(accentColor = dynamicAccent)
        } else {
            newState
        }
        _state.value = stateWithAccent
        saveCachedState(stateWithAccent)
        if (stateWithAccent.isConnected && stateWithAccent.deviceAddress.isNotBlank()) {
            recordDeviceInHistory(stateWithAccent)
        }
        NuclearWidgetManager.updateAllWidgets(context)
        NuclearPhoneWearableSync.syncStateToWear(context, stateWithAccent)
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

    @Volatile
    private var monitoringStarted = false

    fun startInteractiveMonitoring() {
        if (monitoringStarted) return
        monitoringStarted = true
        scope.launch {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            while (isActive) {
                val isScreenOn = powerManager?.isInteractive ?: true
                if (isScreenOn && _state.value.isConnected) {
                    syncTelemetryAsync(silent = true)
                    kotlinx.coroutines.delay(7000L)
                } else {
                    kotlinx.coroutines.delay(2500L)
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
            val device = getTargetBluetoothDevice()
            if (device == null) {
                updateState(_state.value.copy(isConnected = false, isBusy = false))
                onComplete?.invoke(false)
                return@launch
            }

            if (!silent) {
                _state.value = _state.value.copy(isBusy = true)
                SonyBudsWidgetProvider.updateAllWidgets(context)
            }

            val userForced = getUserForcedModel(device.address)
            val cachedMac = getModelForMac(device.address)
            val devName = try { device.name ?: "Sony Headphones" } catch (_: SecurityException) { "Sony Headphones" }
            val effectiveFamily = userForced ?: cachedMac ?: SonyModelFamily.detect(devName)

            if (cachedMac == null && userForced == null && isSonyDeviceName(devName)) {
                saveModelForMac(device.address, effectiveFamily)
            }

            val detectedType = effectiveFamily.defaultDeviceType
            val sysBat = getSystemBatteryLevel(device)

            var baseState = _state.value.copy(
                deviceName = devName,
                deviceAddress = device.address,
                deviceType = detectedType,
                forcedModelFamily = userForced ?: cachedMac
            )

            // Actualización instantánea a 0ms para diademas con porcentaje de sistema Bluetooth
            if (detectedType == DeviceType.OVER_EAR && sysBat in 0..100) {
                baseState = baseState.copy(
                    singleBattery = BatteryInfo(sysBat, false),
                    isConnected = true
                )
                updateState(baseState)
            }

            val result = rfcommClient.syncTelemetry(device, baseState)
            if (result.isSuccess) {
                val syncedState = result.getOrThrow()
                // Si el usuario conmutó el modo hace menos de 3.5 segundos, preservamos su selección explícita
                // Ventana de protección: el sondeo no debe revertir el modo, nivel ni enfoque de voz recién elegidos
                val userRecent = System.currentTimeMillis() - lastUserModeChangeTimestamp < 10_000L
                val current = _state.value
                val merged = if (userRecent) {
                    syncedState.copy(
                        noiseMode = current.noiseMode,
                        ambientLevel = current.ambientLevel,
                        voiceFocus = current.voiceFocus
                    )
                } else syncedState
                updateState(merged.copy(isBusy = false))
                onComplete?.invoke(true)
            } else {
                // Fallback: mantener datos previos pero marcar ocupado = false
                updateState(_state.value.copy(isBusy = false))
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Cambia el modo de cancelación de ruido de forma asíncrona.
     */
    fun setNoiseModeAsync(mode: NoiseControlMode, onComplete: ((Boolean) -> Unit)? = null) {
        if (!_state.value.modelFamily.hasAnc) {
            onComplete?.invoke(false)
            return
        }
        lastUserModeChangeTimestamp = System.currentTimeMillis()

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
        @SuppressLint("MissingPermission")
        val name = device.name ?: "Sony Headphones"
        val userForced = getUserForcedModel(device.address)
        val cachedMac = getModelForMac(device.address)
        val effectiveFamily = userForced ?: cachedMac ?: SonyModelFamily.detect(name)
        val detectedType = effectiveFamily.defaultDeviceType
        val updated = _state.value.copy(
            deviceName = name,
            deviceAddress = device.address,
            deviceType = detectedType,
            forcedModelFamily = userForced ?: cachedMac
        )
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
            @SuppressLint("MissingPermission")
            val name = device.name ?: current.deviceName
            val userForced = getUserForcedModel(device.address)
            val cachedMac = getModelForMac(device.address)
            val effectiveFamily = userForced ?: cachedMac ?: SonyModelFamily.detect(name)
            if (cachedMac == null && userForced == null && isSonyDeviceName(name)) {
                saveModelForMac(device.address, effectiveFamily)
            }
            val type = effectiveFamily.defaultDeviceType
            val sysBat = getSystemBatteryLevel(device)
            val initialSingle = if (type == DeviceType.OVER_EAR && sysBat in 0..100) {
                BatteryInfo(sysBat, false)
            } else current.singleBattery

            val newState = current.copy(
                isConnected = true,
                deviceName = name,
                deviceAddress = device.address,
                deviceType = type,
                singleBattery = initialSingle,
                forcedModelFamily = userForced ?: cachedMac
            )
            updateState(newState)
            recordDeviceInHistory(newState)
            syncTelemetryAsync()
        } else {
            if (device.address.equals(current.deviceAddress, ignoreCase = true)) {
                saveDisconnectedSnapshot(current)
                recordDeviceInHistory(current)
                updateState(current.copy(isConnected = false, isBusy = false))
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
