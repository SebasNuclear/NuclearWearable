package com.nuclear.soundconnect.bluetooth

import com.nuclear.soundconnect.ui.AppLanguage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Paths

data class DeviceHistoryRecord(
    val mac: String,
    val name: String,
    val deviceType: DeviceType,
    val leftLevel: Int = -1,
    val rightLevel: Int = -1,
    val caseLevel: Int = -1,
    val singleLevel: Int = -1,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

/**
 * Repositorio y Gestor central para audífonos Sony en Windows.
 * Coordina el estado de conexión, persistencia de preferencias (tema, OLED, acento, idioma),
 * historial de dispositivos e interacción RFCOMM transaccional.
 */
class SonyWindowsHeadsetManager(
    val rfcommClient: SonyWindowsRfcommClient = SonyWindowsRfcommClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _state = MutableStateFlow(HeadsetState())
    val state: StateFlow<HeadsetState> = _state.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<WindowsBluetoothNative.PairedBluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<WindowsBluetoothNative.PairedBluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _themeMode = MutableStateFlow(2) // 0: Claro, 1: Oscuro, 2: Sistema
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _isOled = MutableStateFlow(true)
    val isOled: StateFlow<Boolean> = _isOled.asStateFlow()

    private val _accentIndex = MutableStateFlow(0)
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.ES)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _history = MutableStateFlow<List<DeviceHistoryRecord>>(emptyList())
    val history: StateFlow<List<DeviceHistoryRecord>> = _history.asStateFlow()

    private var pollingJob: Job? = null
    private var activeSyncJob: Job? = null
    private var activeModeChangeJob: Job? = null
    @Volatile
    private var lastUserModeChangeTimestamp: Long = 0L

    private val settingsFile: File = Paths.get(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "NuclearWearable",
        "settings.json"
    ).toFile()

    init {
        loadSettings()
        refreshPairedDevices()
        startTelemetryPolling()
    }

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        saveSettings()
    }

    fun setOled(oled: Boolean) {
        _isOled.value = oled
        saveSettings()
    }

    fun setAccentIndex(idx: Int) {
        _accentIndex.value = idx
        saveSettings()
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        saveSettings()
    }

    fun refreshPairedDevices() {
        scope.launch(Dispatchers.IO) {
            val list = WindowsBluetoothNative.getPairedDevices()
            _pairedDevices.value = list

            // Si no tenemos dispositivo seleccionado o el actual está vacío, buscar el mejor candidato Sony
            if (_state.value.deviceAddress.isBlank()) {
                val bestSony = list.firstOrNull { it.isSony && (it.name.contains("WF-1000XM6") || it.name.contains("WH-1000XM6")) }
                    ?: list.firstOrNull { it.isSony && it.isConnected }
                    ?: list.firstOrNull { it.isSony }

                if (bestSony != null) {
                    val family = _state.value.forcedModelFamily ?: SonyModelFamily.detect(bestSony.name)
                    _state.value = _state.value.copy(
                        deviceName = bestSony.name,
                        deviceAddress = bestSony.macAddress,
                        deviceType = family.defaultDeviceType,
                        forcedModelFamily = _state.value.forcedModelFamily
                    )
                    saveSettings()
                }
            }

            // Si hay un dispositivo conectado por audio en Windows, intentar sincronizar de inmediato
            val currentMac = _state.value.deviceAddress
            if (currentMac.isNotBlank()) {
                val dev = list.firstOrNull { it.macAddress.equals(currentMac, ignoreCase = true) }
                if (dev != null && dev.isConnected && !_state.value.isConnected) {
                    syncTelemetryAsync(silent = true)
                }
            }
        }
    }

    fun selectDevice(device: WindowsBluetoothNative.PairedBluetoothDevice) {
        val family = _state.value.forcedModelFamily ?: SonyModelFamily.detect(device.name)
        _state.value = _state.value.copy(
            deviceName = device.name,
            deviceAddress = device.macAddress,
            deviceType = family.defaultDeviceType,
            forcedModelFamily = null,
            isConnected = false
        )
        saveSettings()
        syncTelemetryAsync()
    }

    fun setUserForcedModel(address: String?, family: SonyModelFamily?) {
        val current = _state.value
        val effectiveFamily = family ?: (if (!address.isNullOrBlank()) SonyModelFamily.detect(current.deviceName) else null)
        val newType = effectiveFamily?.defaultDeviceType ?: current.deviceType
        _state.value = current.copy(
            forcedModelFamily = family,
            deviceType = newType
        )
        saveSettings()
    }

    private fun startTelemetryPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val mac = _state.value.deviceAddress
                    if (mac.isNotBlank()) {
                        val isConnectedKernel = WindowsBluetoothNative.isDeviceConnected(mac)

                        if (!isConnectedKernel) {
                            // Si en Windows ya no está conectado (audífonos apagados, en el estuche o fuera de rango)
                            if (_state.value.isConnected) {
                                rfcommClient.disconnect()
                                _state.value = _state.value.copy(
                                    isConnected = false,
                                    isBusy = false
                                )
                            }
                        } else {
                            // El dispositivo está conectado a nivel de kernel de Windows
                            if (!_state.value.isConnected) {
                                // Acaba de reconectarse: sincronizar telemetría de inmediato
                                syncTelemetryAsync(silent = true)
                            } else if (!rfcommClient.isImmunityActive() && !_state.value.isBusy && activeModeChangeJob?.isActive != true) {
                                // Sincronización periódica rápida (cada 3.5s) si no ha habido comandos recientes de usuario
                                val timeSinceLast = System.currentTimeMillis() - _state.value.lastSyncTime
                                val timeSinceUser = System.currentTimeMillis() - lastUserModeChangeTimestamp
                                if (timeSinceLast >= 3500L && timeSinceUser >= 5000L) {
                                    syncTelemetryAsync(silent = true)
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Mantener el monitor activo ante excepciones temporales
                }
                delay(1500)
            }
        }
    }

    /**
     * Sincronización asíncrona de telemetría (batería y modo ANC).
     */
    fun syncTelemetryAsync(silent: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        val currentMac = _state.value.deviceAddress
        if (currentMac.isBlank()) {
            onComplete?.invoke(false)
            return
        }

        val timeSinceUserChange = System.currentTimeMillis() - lastUserModeChangeTimestamp
        if (silent && timeSinceUserChange < 5000L) {
            onComplete?.invoke(true)
            return
        }

        // Si el usuario está ejecutando un cambio de modo acústico, darle prioridad total y no estorbar
        if (activeModeChangeJob?.isActive == true) {
            onComplete?.invoke(false)
            return
        }

        // Si ya hay una sincronización activa, evitar tareas duplicadas
        if (activeSyncJob?.isActive == true) {
            if (!silent) {
                _state.value = _state.value.copy(isBusy = true)
            }
            return
        }

        activeSyncJob = scope.launch(Dispatchers.IO) {
            if (!silent) {
                _state.value = _state.value.copy(isBusy = true)
            }

            val result = rfcommClient.syncTelemetry(currentMac, _state.value.deviceName, _state.value)
            if (result.isSuccess) {
                val syncedState = result.getOrThrow()
                val userRecent = System.currentTimeMillis() - lastUserModeChangeTimestamp < 10000L
                val current = _state.value
                val merged = if (userRecent) {
                    syncedState.copy(
                        noiseMode = current.noiseMode,
                        ambientLevel = current.ambientLevel,
                        voiceFocus = current.voiceFocus
                    )
                } else {
                    syncedState
                }
                _state.value = merged.copy(
                    isConnected = true,
                    isBusy = false,
                    lastSyncTime = System.currentTimeMillis()
                )
                recordDeviceInHistory(_state.value)
                saveSettings()
                onComplete?.invoke(true)
            } else {
                // Si la sesión RFCOMM falla, verificar con el kernel antes de declarar desconexión
                val stillConnectedKernel = WindowsBluetoothNative.isDeviceConnected(currentMac)
                _state.value = _state.value.copy(
                    isBusy = false,
                    isConnected = stillConnectedKernel
                )
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * Envía cambio acústico manual (ANC / AMBIENT / OFF) con preempción inmediata en UI
     * y prioridad absoluta de usuario (cancela sondeos y bloquea reversiones).
     */
    fun setNoiseModeAsync(mode: NoiseControlMode, onComplete: ((Boolean) -> Unit)? = null) {
        val currentMac = _state.value.deviceAddress
        if (currentMac.isBlank() || !_state.value.modelFamily.hasAnc) {
            onComplete?.invoke(false)
            return
        }

        lastUserModeChangeTimestamp = System.currentTimeMillis()

        // Prioridad máxima del usuario: Cancelar sondeo en segundo plano
        activeSyncJob?.cancel()

        // Actualización optimista inmediata en UI
        _state.value = _state.value.copy(noiseMode = mode, isBusy = false)

        activeModeChangeJob?.cancel()
        activeModeChangeJob = scope.launch(Dispatchers.IO) {
            val result = rfcommClient.setNoiseMode(
                macAddress = currentMac,
                deviceName = _state.value.deviceName,
                mode = mode,
                ambientLevel = _state.value.ambientLevel,
                voiceFocus = _state.value.voiceFocus,
                currentState = _state.value
            )
            if (result.isSuccess) {
                val current = _state.value
                _state.value = result.getOrThrow().copy(
                    noiseMode = mode,
                    ambientLevel = current.ambientLevel,
                    voiceFocus = current.voiceFocus,
                    isBusy = false
                )
                saveSettings()
                onComplete?.invoke(true)
            } else {
                _state.value = _state.value.copy(isBusy = false)
                onComplete?.invoke(false)
            }
        }
    }

    fun setAmbientLevel(level: Int) {
        val currentMac = _state.value.deviceAddress
        if (currentMac.isBlank()) return

        val clamped = level.coerceIn(1, 20)
        _state.value = _state.value.copy(ambientLevel = clamped)
        lastUserModeChangeTimestamp = System.currentTimeMillis()

        if (_state.value.noiseMode == NoiseControlMode.AMBIENT) {
            setNoiseModeAsync(NoiseControlMode.AMBIENT)
        }
    }

    fun setVoiceFocus(focus: Boolean) {
        val currentMac = _state.value.deviceAddress
        if (currentMac.isBlank()) return

        _state.value = _state.value.copy(voiceFocus = focus)
        lastUserModeChangeTimestamp = System.currentTimeMillis()

        if (_state.value.noiseMode == NoiseControlMode.AMBIENT) {
            setNoiseModeAsync(NoiseControlMode.AMBIENT)
        }
    }

    private fun recordDeviceInHistory(s: HeadsetState) {
        if (s.deviceAddress.isBlank()) return
        val current = _history.value.toMutableList()
        current.removeAll { it.mac.equals(s.deviceAddress, ignoreCase = true) }
        current.add(0, DeviceHistoryRecord(
            mac = s.deviceAddress,
            name = s.deviceName,
            deviceType = s.deviceType,
            leftLevel = s.leftBattery.level,
            rightLevel = s.rightBattery.level,
            caseLevel = s.caseBattery.level,
            singleLevel = s.singleBattery.level,
            lastSeenTimestamp = System.currentTimeMillis()
        ))
        if (current.size > 20) current.removeAt(current.size - 1)
        _history.value = current
    }

    private fun loadSettings() {
        try {
            if (!settingsFile.exists()) return
            val text = settingsFile.readText()

            val mac = Regex("\"macAddress\":\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1) ?: ""
            val name = Regex("\"deviceName\":\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1) ?: "WF-1000XM6"
            val familyStr = Regex("\"forcedFamily\":\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)
            val forcedFam = familyStr?.takeIf { it.isNotBlank() }?.let {
                try { SonyModelFamily.valueOf(it) } catch (_: Throwable) { null }
            }
            val themeModeVal = Regex("\"themeMode\":\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 2
            val isOledVal = Regex("\"isOled\":\\s*(true|false)").find(text)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: true
            val accentIdxVal = Regex("\"accentIndex\":\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val langStr = Regex("\"language\":\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1) ?: "es"

            _themeMode.value = themeModeVal
            _isOled.value = isOledVal
            _accentIndex.value = accentIdxVal
            _language.value = AppLanguage.fromCode(langStr)

            val effectiveFam = forcedFam ?: SonyModelFamily.detect(name)
            _state.value = _state.value.copy(
                deviceName = name,
                deviceAddress = mac,
                forcedModelFamily = forcedFam,
                deviceType = effectiveFam.defaultDeviceType
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun shutdown() {
        try {
            pollingJob?.cancel()
            activeSyncJob?.cancel()
            activeModeChangeJob?.cancel()
            rfcommClient.disconnect()
            saveSettings()
        } catch (_: Throwable) {}
    }

    private fun saveSettings() {
        try {
            settingsFile.parentFile?.mkdirs()
            val json = buildString {
                append("{\n")
                append("  \"macAddress\": \"${_state.value.deviceAddress}\",\n")
                append("  \"deviceName\": \"${_state.value.deviceName}\",\n")
                append("  \"forcedFamily\": \"${_state.value.forcedModelFamily?.name ?: ""}\",\n")
                append("  \"themeMode\": ${_themeMode.value},\n")
                append("  \"isOled\": ${_isOled.value},\n")
                append("  \"accentIndex\": ${_accentIndex.value},\n")
                append("  \"language\": \"${_language.value.code}\"\n")
                append("}\n")
            }
            settingsFile.writeText(json)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
