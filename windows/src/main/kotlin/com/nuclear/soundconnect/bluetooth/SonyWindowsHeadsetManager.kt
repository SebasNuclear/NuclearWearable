package com.nuclear.soundconnect.bluetooth

import com.nuclear.soundconnect.audio.EarSide
import com.nuclear.soundconnect.audio.FindBudsPlaybackState
import com.nuclear.soundconnect.audio.FindMyBudsTonePlayer
import com.nuclear.soundconnect.audio.LiveCodecHudInfo
import com.nuclear.soundconnect.audio.WindowsAudioMonitor
import com.nuclear.soundconnect.automation.AutomationEvent
import com.nuclear.soundconnect.automation.LocalAutomationConfig
import com.nuclear.soundconnect.automation.LocalAutomationEngine
import com.nuclear.soundconnect.backup.ProfileBackupManager
import com.nuclear.soundconnect.ui.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

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

private data class CachedDeviceProfile(
    val cachedFamily: SonyModelFamily? = null,
    val forcedFamily: SonyModelFamily? = null,
    val hardwareModelName: String = "",
    val firmwareVersion: String = "",
    val protocolVersion: Int = 2,
    val supportFunctions: Set<Int> = emptySet(),
    val table2SupportFunctions: Set<Int> = emptySet(),
    val gsBooleanConfirmed: Set<Int> = emptySet(),
    val eqState: EqState = EqState(),
    val dseeEnabled: Boolean? = null,
    val connectionPriority: ConnectionPriority? = null,
    val autoPowerOff: AutoPowerOffOption? = null,
    val speakToChatEnabled: Boolean? = null,
    val speakToChatSensitivity: SpeakToChatSensitivity = SpeakToChatSensitivity.AUTO,
    val speakToChatReturnTime: SpeakToChatReturnTime = SpeakToChatReturnTime.MID,
    val speakToChatFocusOnVoice: Boolean = false,
    val pauseWhenRemoved: Boolean? = null,
    val windNoiseReduction: Boolean = false,
    val voiceGuidanceEnabled: Boolean? = null,
    val voiceGuidanceLanguage: VoiceGuidanceLanguageOption = VoiceGuidanceLanguageOption.UNDEFINED,
    val voiceGuidanceVolume: Int = 0,
    val multipointEnabled: Boolean? = null,
    val touchLeftPreset: TouchPresetOption? = null,
    val touchRightPreset: TouchPresetOption? = null,
    val touchThirdPreset: TouchPresetOption? = null,
    val touchPanelEnabled: Boolean? = null
)

/**
 * Repositorio y Gestor central v1.1.0 para audífonos Sony en Windows Desktop.
 * Coordina sesión RFCOMM dual-channel (Table 1/Table 2), caché por MAC, motores Fase 3
 * sin telemetría (HUD WASAPI, Find My Buds estéreo L/R, automatización local y respaldo JSON),
 * historial de baterías y preferencias visuales NuclearUI.
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

    private val _automationConfig = MutableStateFlow(LocalAutomationConfig())
    val automationConfig: StateFlow<LocalAutomationConfig> = _automationConfig.asStateFlow()

    private val _hexLogsHistory = MutableStateFlow<List<HexLogEntry>>(emptyList())
    val hexLogsHistory: StateFlow<List<HexLogEntry>> = _hexLogsHistory.asStateFlow()
    val hexLogs: SharedFlow<HexLogEntry> get() = rfcommClient.hexLogs

    private val deviceCaches = ConcurrentHashMap<String, CachedDeviceProfile>()
    private val lastExtendedSyncAtByMac = ConcurrentHashMap<String, Long>()

    private var pollingJob: Job? = null
    private var activeSyncJob: Job? = null
    private var activeModeChangeJob: Job? = null
    private var scheduleJob: Job? = null
    private var extendedSyncJob: Job? = null

    @Volatile private var lastUserModeChangeTimestamp: Long = 0L
    @Volatile private var prevAudioPlaying: Boolean = false
    @Volatile private var prevHfpCallActive: Boolean = false

    val findMyBudsPlayer = FindMyBudsTonePlayer()
    val findBudsState: StateFlow<FindBudsPlaybackState> get() = findMyBudsPlayer.playbackState

    val automationEngine: LocalAutomationEngine = LocalAutomationEngine { mode, ambientLevel, voiceFocus, _ ->
        applyNoiseModeFromAutomation(mode, ambientLevel, voiceFocus)
    }

    val audioMonitor: WindowsAudioMonitor = WindowsAudioMonitor { isPlaying, isHfpCall ->
        handleAudioMonitorTransition(isPlaying, isHfpCall)
    }
    val codecHud: StateFlow<LiveCodecHudInfo> get() = audioMonitor.liveInfo

    private val settingsFile: File = Paths.get(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "NuclearWearable",
        "settings.json"
    ).toFile()

    init {
        loadSettings()
        collectProtocolStreams()
        audioMonitor.startMonitoring()
        startScheduleLoop()
        refreshPairedDevices()
        startTelemetryPolling()
    }

    private fun collectProtocolStreams() {
        scope.launch {
            rfcommClient.hexLogs.collect { entry ->
                val updated = (_hexLogsHistory.value + entry).let { list ->
                    if (list.size > MAX_HEX_HISTORY) list.takeLast(MAX_HEX_HISTORY) else list
                }
                _hexLogsHistory.value = updated
            }
        }
        scope.launch {
            rfcommClient.liveStateUpdates.collect { incoming ->
                mergeAndPublishIncomingState(incoming, fromPush = true)
            }
        }
        scope.launch {
            rfcommClient.connectionEvents.collect { (mac, connected) ->
                if (!connected && _state.value.deviceAddress.equals(mac, ignoreCase = true)) {
                    _state.value = _state.value.copy(isConnected = false, isBusy = false)
                }
            }
        }
    }

    private fun startScheduleLoop() {
        scheduleJob?.cancel()
        scheduleJob = scope.launch {
            while (isActive) {
                val cur = _state.value
                automationEngine.evaluateScheduleTick(
                    config = _automationConfig.value,
                    isConnected = cur.isConnected && cur.effectiveHasAnc,
                    currentMode = cur.noiseMode,
                    currentAmbient = cur.ambientLevel,
                    currentVoiceFocus = cur.voiceFocus
                )
                delay(30_000L)
            }
        }
    }

    private fun handleAudioMonitorTransition(isPlaying: Boolean, isHfpCall: Boolean) {
        val cur = _state.value
        val connectedAndAnc = cur.isConnected && cur.effectiveHasAnc
        val cfg = _automationConfig.value

        val hfpChanged = isHfpCall != prevHfpCallActive
        val playStarted = isPlaying && !prevAudioPlaying
        prevHfpCallActive = isHfpCall
        prevAudioPlaying = isPlaying

        if (!connectedAndAnc) return
        scope.launch {
            if (hfpChanged) {
                val ev = if (isHfpCall) AutomationEvent.CallStart(true) else AutomationEvent.CallEnd(true)
                automationEngine.handleEvent(
                    config = cfg,
                    event = ev,
                    isConnected = true,
                    currentMode = _state.value.noiseMode,
                    currentAmbient = _state.value.ambientLevel,
                    currentVoiceFocus = _state.value.voiceFocus
                )
            } else if (playStarted) {
                automationEngine.handleEvent(
                    config = cfg,
                    event = AutomationEvent.MediaPlayback(true),
                    isConnected = true,
                    currentMode = _state.value.noiseMode,
                    currentAmbient = _state.value.ambientLevel,
                    currentVoiceFocus = _state.value.voiceFocus
                )
            }
        }
    }

    fun clearHexLogs() {
        _hexLogsHistory.value = emptyList()
    }

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode.coerceIn(0, 2)
        saveSettings()
    }

    fun setOled(oled: Boolean) {
        _isOled.value = oled
        saveSettings()
    }

    fun setAccentIndex(idx: Int) {
        _accentIndex.value = idx.coerceIn(0, ACCENT_PALETTE_ARGB.lastIndex)
        saveSettings()
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        saveSettings()
    }

    fun updateAutomationConfig(config: LocalAutomationConfig) {
        _automationConfig.value = config
        saveSettings()
    }

    fun refreshCodecHud() {
        audioMonitor.refreshNow()
    }

    fun startFindMyBuds(side: EarSide) {
        findMyBudsPlayer.startTone(side)
    }

    fun stopFindMyBuds() {
        findMyBudsPlayer.stop()
    }

    fun refreshPairedDevices() {
        scope.launch(Dispatchers.IO) {
            val list = WindowsBluetoothNative.getPairedDevices()
            _pairedDevices.value = list

            if (_state.value.deviceAddress.isBlank()) {
                val bestSony = list.firstOrNull { it.isSony && (it.name.contains("WF-1000XM6") || it.name.contains("WH-1000XM6")) }
                    ?: list.firstOrNull { it.isSony && it.isConnected }
                    ?: list.firstOrNull { it.isSony }

                if (bestSony != null) {
                    applyCachedStateForDevice(bestSony.macAddress, bestSony.name)
                    saveSettings()
                }
            }

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
        scope.launch(Dispatchers.IO) {
            rfcommClient.closePersistentSession("Cambio de dispositivo")
            applyCachedStateForDevice(device.macAddress, device.name)
            saveSettings()
            syncTelemetryAsync()
        }
    }

    fun setUserForcedModel(address: String?, family: SonyModelFamily?) {
        val current = _state.value
        val key = (address ?: current.deviceAddress).uppercase()
        if (key.isNotBlank()) {
            val existing = deviceCaches[key] ?: CachedDeviceProfile()
            deviceCaches[key] = existing.copy(forcedFamily = family)
        }
        val effectiveFamily = family ?: SonyModelFamily.detect(current.hardwareModelName.ifBlank { current.deviceName })
        _state.value = current.copy(
            forcedModelFamily = family,
            deviceType = effectiveFamily.defaultDeviceType
        )
        saveSettings()
    }

    private fun applyCachedStateForDevice(mac: String, name: String) {
        val key = mac.uppercase()
        val cached = deviceCaches[key]
        val baseFamily = cached?.forcedFamily
            ?: cached?.cachedFamily
            ?: SonyModelFamily.detect(cached?.hardwareModelName?.ifBlank { name } ?: name)
        _state.value = HeadsetState(
            deviceName = name,
            deviceAddress = mac,
            isConnected = false,
            deviceType = baseFamily.defaultDeviceType,
            accentColor = ACCENT_PALETTE_ARGB.getOrElse(_accentIndex.value) { ACCENT_PALETTE_ARGB[0] },
            forcedModelFamily = cached?.forcedFamily,
            cachedModelFamily = cached?.cachedFamily,
            hardwareModelName = cached?.hardwareModelName ?: "",
            firmwareVersion = cached?.firmwareVersion ?: "",
            protocolVersion = cached?.protocolVersion ?: 2,
            supportFunctions = cached?.supportFunctions ?: emptySet(),
            table2SupportFunctions = cached?.table2SupportFunctions ?: emptySet(),
            gsBooleanConfirmed = cached?.gsBooleanConfirmed ?: emptySet(),
            eqState = cached?.eqState ?: EqState(),
            dseeEnabled = cached?.dseeEnabled,
            connectionPriority = cached?.connectionPriority,
            autoPowerOff = cached?.autoPowerOff,
            speakToChatEnabled = cached?.speakToChatEnabled,
            speakToChatSensitivity = cached?.speakToChatSensitivity ?: SpeakToChatSensitivity.AUTO,
            speakToChatReturnTime = cached?.speakToChatReturnTime ?: SpeakToChatReturnTime.MID,
            speakToChatFocusOnVoice = cached?.speakToChatFocusOnVoice ?: false,
            pauseWhenRemoved = cached?.pauseWhenRemoved,
            windNoiseReduction = cached?.windNoiseReduction ?: false,
            voiceGuidanceEnabled = cached?.voiceGuidanceEnabled,
            voiceGuidanceLanguage = cached?.voiceGuidanceLanguage ?: VoiceGuidanceLanguageOption.UNDEFINED,
            voiceGuidanceVolume = cached?.voiceGuidanceVolume ?: 0,
            multipointEnabled = cached?.multipointEnabled,
            touchLeftPreset = cached?.touchLeftPreset,
            touchRightPreset = cached?.touchRightPreset,
            touchThirdPreset = cached?.touchThirdPreset,
            touchPanelEnabled = cached?.touchPanelEnabled
        )
        audioMonitor.updateFromProtocol("", name, _state.value.connectionPriority)
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
                            if (_state.value.isConnected) {
                                rfcommClient.disconnect("Bluetooth desconectado en Windows")
                                _state.value = _state.value.copy(isConnected = false, isBusy = false)
                                audioMonitor.clearDevice()
                            }
                        } else {
                            if (!_state.value.isConnected) {
                                syncTelemetryAsync(silent = true)
                            } else if (!rfcommClient.isImmunityActive() &&
                                !_state.value.isBusy &&
                                activeModeChangeJob?.isActive != true
                            ) {
                                val timeSinceLast = System.currentTimeMillis() - _state.value.lastSyncTime
                                val timeSinceUser = System.currentTimeMillis() - lastUserModeChangeTimestamp
                                if (timeSinceLast >= 3500L && timeSinceUser >= 5000L) {
                                    syncTelemetryAsync(silent = true)
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}
                delay(1500L)
            }
        }
    }

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

        if (activeModeChangeJob?.isActive == true) {
            onComplete?.invoke(false)
            return
        }

        if (activeSyncJob?.isActive == true) {
            if (!silent) _state.value = _state.value.copy(isBusy = true)
            return
        }

        activeSyncJob = scope.launch(Dispatchers.IO) {
            if (!silent) {
                _state.value = _state.value.copy(isBusy = true)
            }

            val result = rfcommClient.syncTelemetry(currentMac, _state.value.deviceName, _state.value)
            if (result.isSuccess) {
                val syncedState = result.getOrThrow().copy(
                    isConnected = true,
                    isBusy = false,
                    lastSyncTime = System.currentTimeMillis()
                )
                val merged = mergeAndPublishIncomingState(syncedState, fromPush = false)
                recordDeviceInHistory(merged)
                saveSettings()
                maybeAutoSyncExtended(currentMac, merged.deviceName, merged)
                onComplete?.invoke(true)
            } else {
                val stillConnectedKernel = WindowsBluetoothNative.isDeviceConnected(currentMac)
                _state.value = _state.value.copy(
                    isBusy = false,
                    isConnected = stillConnectedKernel
                )
                onComplete?.invoke(false)
            }
        }
    }

    private fun mergeAndPublishIncomingState(incoming: HeadsetState, fromPush: Boolean): HeadsetState {
        val cur = _state.value
        val sameMac = cur.deviceAddress.equals(incoming.deviceAddress, ignoreCase = true)
        val userRecent = System.currentTimeMillis() - lastUserModeChangeTimestamp < 5000L ||
            rfcommClient.isImmunityActive()

        val preservedMode = if (sameMac && userRecent) cur.noiseMode else incoming.noiseMode
        val preservedAmbient = if (sameMac && userRecent) cur.ambientLevel else incoming.ambientLevel
        val preservedVoice = if (sameMac && userRecent) cur.voiceFocus else incoming.voiceFocus

        val mergedEq = when {
            incoming.eqState.isLoaded -> incoming.eqState
            sameMac && cur.eqState.isLoaded -> cur.eqState
            else -> incoming.eqState
        }

        val merged = incoming.copy(
            noiseMode = preservedMode,
            ambientLevel = preservedAmbient,
            voiceFocus = preservedVoice,
            forcedModelFamily = cur.forcedModelFamily ?: incoming.forcedModelFamily,
            cachedModelFamily = incoming.cachedModelFamily ?: cur.cachedModelFamily,
            hardwareModelName = incoming.hardwareModelName.ifBlank { if (sameMac) cur.hardwareModelName else "" },
            firmwareVersion = incoming.firmwareVersion.ifBlank { if (sameMac) cur.firmwareVersion else "" },
            supportFunctions = if (incoming.supportFunctions.isNotEmpty()) incoming.supportFunctions else cur.supportFunctions,
            table2SupportFunctions = if (incoming.table2SupportFunctions.isNotEmpty()) incoming.table2SupportFunctions else cur.table2SupportFunctions,
            gsBooleanConfirmed = if (incoming.gsBooleanConfirmed.isNotEmpty()) incoming.gsBooleanConfirmed else cur.gsBooleanConfirmed,
            eqState = mergedEq,
            dseeEnabled = incoming.dseeEnabled ?: if (sameMac) cur.dseeEnabled else null,
            connectionPriority = incoming.connectionPriority ?: if (sameMac) cur.connectionPriority else null,
            autoPowerOff = incoming.autoPowerOff ?: if (sameMac) cur.autoPowerOff else null,
            speakToChatEnabled = incoming.speakToChatEnabled ?: if (sameMac) cur.speakToChatEnabled else null,
            speakToChatSensitivity = if (fromPush && incoming.speakToChatEnabled != null) incoming.speakToChatSensitivity else cur.speakToChatSensitivity,
            speakToChatReturnTime = if (fromPush && incoming.speakToChatEnabled != null) incoming.speakToChatReturnTime else cur.speakToChatReturnTime,
            speakToChatFocusOnVoice = if (fromPush && incoming.speakToChatEnabled != null) incoming.speakToChatFocusOnVoice else cur.speakToChatFocusOnVoice,
            pauseWhenRemoved = incoming.pauseWhenRemoved ?: if (sameMac) cur.pauseWhenRemoved else null,
            windNoiseReduction = if (incoming.supportFunctions.contains(0x15) || incoming.windNoiseReduction) incoming.windNoiseReduction else cur.windNoiseReduction,
            voiceGuidanceEnabled = incoming.voiceGuidanceEnabled ?: if (sameMac) cur.voiceGuidanceEnabled else null,
            voiceGuidanceLanguage = if (incoming.voiceGuidanceLanguage != VoiceGuidanceLanguageOption.UNDEFINED) incoming.voiceGuidanceLanguage else cur.voiceGuidanceLanguage,
            voiceGuidanceVolume = incoming.voiceGuidanceVolume,
            multipointEnabled = incoming.multipointEnabled ?: if (sameMac) cur.multipointEnabled else null,
            pairedDevices = if (incoming.pairedDevices.isNotEmpty()) incoming.pairedDevices else if (sameMac) cur.pairedDevices else emptyList(),
            touchLeftPreset = incoming.touchLeftPreset ?: if (sameMac) cur.touchLeftPreset else null,
            touchRightPreset = incoming.touchRightPreset ?: if (sameMac) cur.touchRightPreset else null,
            touchThirdPreset = incoming.touchThirdPreset ?: if (sameMac) cur.touchThirdPreset else null,
            touchPanelEnabled = incoming.touchPanelEnabled ?: if (sameMac) cur.touchPanelEnabled else null,
            accentColor = ACCENT_PALETTE_ARGB.getOrElse(_accentIndex.value) { cur.accentColor },
            isBusy = if (fromPush) cur.isBusy else incoming.isBusy,
            lastSyncTime = System.currentTimeMillis()
        )

        _state.value = merged
        cacheStateForMac(merged)
        audioMonitor.updateFromProtocol(
            codecName = merged.codecName,
            deviceName = merged.hardwareModelName.ifBlank { merged.deviceName },
            connectionPriority = merged.connectionPriority
        )
        return merged
    }

    private fun cacheStateForMac(s: HeadsetState) {
        val key = s.deviceAddress.uppercase()
        if (key.isBlank()) return
        val prev = deviceCaches[key] ?: CachedDeviceProfile()
        deviceCaches[key] = prev.copy(
            cachedFamily = s.modelFamily.takeIf { it != SonyModelFamily.GENERIC_TWS && it != SonyModelFamily.GENERIC_OVER_EAR }
                ?: prev.cachedFamily,
            forcedFamily = s.forcedModelFamily ?: prev.forcedFamily,
            hardwareModelName = s.hardwareModelName.ifBlank { prev.hardwareModelName },
            firmwareVersion = s.firmwareVersion.ifBlank { prev.firmwareVersion },
            protocolVersion = s.protocolVersion,
            supportFunctions = s.supportFunctions.ifEmpty { prev.supportFunctions },
            table2SupportFunctions = s.table2SupportFunctions.ifEmpty { prev.table2SupportFunctions },
            gsBooleanConfirmed = s.gsBooleanConfirmed.ifEmpty { prev.gsBooleanConfirmed },
            eqState = if (s.eqState.isLoaded) s.eqState else prev.eqState,
            dseeEnabled = s.dseeEnabled ?: prev.dseeEnabled,
            connectionPriority = s.connectionPriority ?: prev.connectionPriority,
            autoPowerOff = s.autoPowerOff ?: prev.autoPowerOff,
            speakToChatEnabled = s.speakToChatEnabled ?: prev.speakToChatEnabled,
            speakToChatSensitivity = s.speakToChatSensitivity,
            speakToChatReturnTime = s.speakToChatReturnTime,
            speakToChatFocusOnVoice = s.speakToChatFocusOnVoice,
            pauseWhenRemoved = s.pauseWhenRemoved ?: prev.pauseWhenRemoved,
            windNoiseReduction = s.windNoiseReduction,
            voiceGuidanceEnabled = s.voiceGuidanceEnabled ?: prev.voiceGuidanceEnabled,
            voiceGuidanceLanguage = if (s.voiceGuidanceLanguage != VoiceGuidanceLanguageOption.UNDEFINED) s.voiceGuidanceLanguage else prev.voiceGuidanceLanguage,
            voiceGuidanceVolume = s.voiceGuidanceVolume,
            multipointEnabled = s.multipointEnabled ?: prev.multipointEnabled,
            touchLeftPreset = s.touchLeftPreset ?: prev.touchLeftPreset,
            touchRightPreset = s.touchRightPreset ?: prev.touchRightPreset,
            touchThirdPreset = s.touchThirdPreset ?: prev.touchThirdPreset,
            touchPanelEnabled = s.touchPanelEnabled ?: prev.touchPanelEnabled
        )
    }

    private fun maybeAutoSyncExtended(mac: String, deviceName: String, state: HeadsetState) {
        val key = mac.uppercase()
        val now = System.currentTimeMillis()
        val lastAt = lastExtendedSyncAtByMac[key] ?: 0L
        if (now - lastAt < EXTENDED_AUTO_SYNC_COOLDOWN_MS) return
        if (extendedSyncJob?.isActive == true) return
        lastExtendedSyncAtByMac[key] = now
        syncExtendedControlsAsync()
    }

    fun setNoiseModeAsync(mode: NoiseControlMode, onComplete: ((Boolean) -> Unit)? = null) {
        automationEngine.notifyUserManualOverride()
        sendNoiseModeInternal(
            mode = mode,
            ambientLevel = _state.value.ambientLevel,
            voiceFocus = _state.value.voiceFocus,
            onComplete = onComplete
        )
    }

    private fun applyNoiseModeFromAutomation(mode: NoiseControlMode, ambientLevel: Int, voiceFocus: Boolean) {
        sendNoiseModeInternal(mode = mode, ambientLevel = ambientLevel, voiceFocus = voiceFocus, onComplete = null)
    }

    private fun sendNoiseModeInternal(
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        onComplete: ((Boolean) -> Unit)?
    ) {
        val currentMac = _state.value.deviceAddress
        if (currentMac.isBlank() || !_state.value.effectiveHasAnc) {
            onComplete?.invoke(false)
            return
        }

        lastUserModeChangeTimestamp = System.currentTimeMillis()
        activeSyncJob?.cancel()

        val clampedAmbient = ambientLevel.coerceIn(1, 20)
        _state.value = _state.value.copy(
            noiseMode = mode,
            ambientLevel = clampedAmbient,
            voiceFocus = voiceFocus,
            isBusy = false
        )

        activeModeChangeJob?.cancel()
        activeModeChangeJob = scope.launch(Dispatchers.IO) {
            val result = rfcommClient.setNoiseMode(
                macAddress = currentMac,
                deviceName = _state.value.deviceName,
                mode = mode,
                ambientLevel = clampedAmbient,
                voiceFocus = voiceFocus,
                currentState = _state.value
            )
            if (result.isSuccess) {
                _state.value = result.getOrThrow().copy(
                    noiseMode = mode,
                    ambientLevel = clampedAmbient,
                    voiceFocus = voiceFocus,
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
        automationEngine.notifyUserManualOverride()
        val clamped = level.coerceIn(1, 20)
        _state.value = _state.value.copy(ambientLevel = clamped)
        lastUserModeChangeTimestamp = System.currentTimeMillis()
        if (_state.value.noiseMode == NoiseControlMode.AMBIENT) {
            sendNoiseModeInternal(NoiseControlMode.AMBIENT, clamped, _state.value.voiceFocus, null)
        }
    }

    fun setVoiceFocus(focus: Boolean) {
        val currentMac = _state.value.deviceAddress
        if (currentMac.isBlank()) return
        automationEngine.notifyUserManualOverride()
        _state.value = _state.value.copy(voiceFocus = focus)
        lastUserModeChangeTimestamp = System.currentTimeMillis()
        if (_state.value.noiseMode == NoiseControlMode.AMBIENT) {
            sendNoiseModeInternal(NoiseControlMode.AMBIENT, _state.value.ambientLevel, focus, null)
        }
    }

    // =========================================================================
    // CONTROLES EXTENDIDOS FASE 2 (EQ, DSEE, Speak-to-Chat, Touch, Multipunto, Voz)
    // =========================================================================

    fun syncExtendedControlsAsync(onComplete: ((Boolean) -> Unit)? = null) {
        val current = _state.value
        val mac = current.deviceAddress
        if (mac.isBlank()) {
            onComplete?.invoke(false)
            return
        }
        extendedSyncJob?.cancel()
        extendedSyncJob = scope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isBusy = true)
            val result = rfcommClient.syncExtendedControls(mac, current.deviceName, _state.value)
            if (result.isSuccess) {
                mergeAndPublishIncomingState(result.getOrThrow().copy(isBusy = false, isConnected = true), fromPush = false)
                saveSettings()
                onComplete?.invoke(true)
            } else {
                _state.value = _state.value.copy(isBusy = false)
                onComplete?.invoke(false)
            }
        }
    }

    private fun runDeviceCommand(
        optimisticUpdate: (HeadsetState) -> HeadsetState,
        onComplete: ((Boolean) -> Unit)? = null,
        block: suspend (mac: String, name: String, snapshot: HeadsetState) -> Result<HeadsetState>
    ) {
        val previous = _state.value
        val mac = previous.deviceAddress
        if (mac.isBlank()) {
            onComplete?.invoke(false)
            return
        }
        val optimistic = optimisticUpdate(previous)
        _state.value = optimistic
        scope.launch(Dispatchers.IO) {
            val result = block(mac, optimistic.deviceName, optimistic)
            if (result.isSuccess) {
                mergeAndPublishIncomingState(result.getOrThrow().copy(isConnected = true), fromPush = false)
                saveSettings()
                onComplete?.invoke(true)
            } else {
                _state.value = previous
                onComplete?.invoke(false)
            }
        }
    }

    fun setEqualizerPresetAsync(presetId: Int, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand(
            optimisticUpdate = { it.copy(eqState = it.eqState.copy(isSupported = true, isLoaded = true, presetId = presetId)) },
            onComplete = onComplete
        ) { mac, name, snap ->
            rfcommClient.setEqualizerPreset(mac, name, snap, presetId)
        }
    }

    fun setEqualizerCustomAsync(clearBass: Int, bands: List<Int>, onComplete: ((Boolean) -> Unit)? = null) {
        val presetId = if (_state.value.protocolVersion < 2) EqPreset.UNSPECIFIED.id else EqPreset.CUSTOM.id
        runDeviceCommand(
            optimisticUpdate = {
                it.copy(
                    eqState = EqState(
                        isSupported = true,
                        isLoaded = true,
                        presetId = presetId,
                        clearBass = if (it.capabilities.tenBandEq) 0 else clearBass.coerceIn(-10, 10),
                        bands = bands,
                        isTenBand = it.capabilities.tenBandEq
                    )
                )
            },
            onComplete = onComplete
        ) { mac, name, snap ->
            rfcommClient.setEqualizerCustom(mac, name, snap, clearBass, bands)
        }
    }

    fun setDseeEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand({ it.copy(dseeEnabled = enabled) }, onComplete) { mac, name, snap ->
            rfcommClient.setDseeEnabled(mac, name, snap, enabled)
        }
    }

    fun setConnectionPriorityAsync(priority: ConnectionPriority, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand({ it.copy(connectionPriority = priority) }, onComplete) { mac, name, snap ->
            rfcommClient.setConnectionPriority(mac, name, snap, priority)
        }
    }

    fun setAutoPowerOffAsync(option: AutoPowerOffOption, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand({ it.copy(autoPowerOff = option) }, onComplete) { mac, name, snap ->
            rfcommClient.setAutoPowerOff(mac, name, snap, option)
        }
    }

    fun setSpeakToChatEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand({ it.copy(speakToChatEnabled = enabled) }, onComplete) { mac, name, snap ->
            rfcommClient.setSpeakToChatEnabled(mac, name, snap, enabled)
        }
    }

    fun setSpeakToChatConfigAsync(
        sensitivity: SpeakToChatSensitivity,
        returnTime: SpeakToChatReturnTime,
        focusOnVoice: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        runDeviceCommand(
            {
                it.copy(
                    speakToChatSensitivity = sensitivity,
                    speakToChatReturnTime = returnTime,
                    speakToChatFocusOnVoice = focusOnVoice
                )
            },
            onComplete
        ) { mac, name, snap ->
            rfcommClient.setSpeakToChatConfig(mac, name, snap, sensitivity, returnTime, focusOnVoice)
        }
    }

    fun setPauseWhenRemovedAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand({ it.copy(pauseWhenRemoved = enabled) }, onComplete) { mac, name, snap ->
            rfcommClient.setPauseWhenRemoved(mac, name, snap, enabled)
        }
    }

    fun setWindNoiseReductionAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand({ it.copy(windNoiseReduction = enabled) }, onComplete) { mac, name, snap ->
            rfcommClient.setWindNoiseReduction(mac, name, snap, enabled)
        }
    }

    fun setTouchAssignmentAsync(
        left: TouchPresetOption,
        right: TouchPresetOption,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val third = _state.value.touchThirdPreset
        val presets = buildList {
            add(left)
            add(right)
            if (third != null) add(third)
        }
        runDeviceCommand(
            { it.copy(touchLeftPreset = left, touchRightPreset = right) },
            onComplete
        ) { mac, name, snap ->
            rfcommClient.setTouchAssignment(mac, name, snap, presets)
        }
    }

    fun setTouchPanelEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand({ it.copy(touchPanelEnabled = enabled) }, onComplete) { mac, name, snap ->
            rfcommClient.setTouchPanelEnabled(mac, name, snap, enabled)
        }
    }

    fun setMultipointEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) {
        runDeviceCommand({ it.copy(multipointEnabled = enabled) }, onComplete) { mac, name, snap ->
            rfcommClient.setMultipointEnabled(mac, name, snap, enabled)
        }
    }

    fun setMultipointDeviceConnectionAsync(
        targetMac: String,
        connect: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val current = _state.value
        val mac = current.deviceAddress
        if (mac.isBlank()) {
            onComplete?.invoke(false)
            return
        }
        scope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isBusy = true)
            val result = rfcommClient.setMultipointDeviceConnection(mac, current.deviceName, _state.value, targetMac, connect)
            if (result.isSuccess) {
                mergeAndPublishIncomingState(result.getOrThrow().copy(isBusy = false, isConnected = true), fromPush = false)
                onComplete?.invoke(true)
            } else {
                _state.value = _state.value.copy(isBusy = false)
                onComplete?.invoke(false)
            }
        }
    }

    fun switchMultipointActiveSourceAsync(targetMac: String, onComplete: ((Boolean) -> Unit)? = null) {
        val current = _state.value
        val mac = current.deviceAddress
        if (mac.isBlank()) {
            onComplete?.invoke(false)
            return
        }
        scope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isBusy = true)
            val result = rfcommClient.switchMultipointActiveSource(mac, current.deviceName, _state.value, targetMac)
            if (result.isSuccess) {
                mergeAndPublishIncomingState(result.getOrThrow().copy(isBusy = false, isConnected = true), fromPush = false)
                onComplete?.invoke(true)
            } else {
                _state.value = _state.value.copy(isBusy = false)
                onComplete?.invoke(false)
            }
        }
    }

    fun setVoiceGuidanceAsync(
        enabled: Boolean,
        language: VoiceGuidanceLanguageOption,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        runDeviceCommand(
            { it.copy(voiceGuidanceEnabled = enabled, voiceGuidanceLanguage = language) },
            onComplete
        ) { mac, name, snap ->
            rfcommClient.setVoiceGuidance(mac, name, snap, enabled, language)
        }
    }

    fun setVoiceGuidanceVolumeAsync(volume: Int, onComplete: ((Boolean) -> Unit)? = null) {
        val clamped = volume.coerceIn(-2, 2)
        runDeviceCommand({ it.copy(voiceGuidanceVolume = clamped) }, onComplete) { mac, name, snap ->
            rfcommClient.setVoiceGuidanceVolume(mac, name, snap, clamped)
        }
    }

    fun remotePowerOffAsync(onComplete: ((Boolean) -> Unit)? = null) {
        val current = _state.value
        val mac = current.deviceAddress
        if (mac.isBlank() || current.protocolVersion < 2) {
            onComplete?.invoke(false)
            return
        }
        scope.launch(Dispatchers.IO) {
            val res = rfcommClient.remotePowerOff(mac, current.deviceName, current)
            if (res.isSuccess) {
                _state.value = _state.value.copy(isConnected = false, isBusy = false)
                audioMonitor.clearDevice()
                onComplete?.invoke(true)
            } else {
                onComplete?.invoke(false)
            }
        }
    }

    // =========================================================================
    // FASE 3.5: RESPALDO Y RESTAURACIÓN OFFLINE (.json)
    // =========================================================================

    fun exportBackupJson(): String {
        val currentAccent = ACCENT_PALETTE_ARGB.getOrElse(_accentIndex.value) { ACCENT_PALETTE_ARGB[0] }
        val bundle = ProfileBackupManager.createBundleFromState(
            state = _state.value,
            automation = _automationConfig.value,
            languageCode = _language.value.code,
            accentColorArgb = currentAccent,
            appVersionName = "1.1.0"
        )
        return ProfileBackupManager.exportJson(bundle)
    }

    fun restoreBackupJson(rawJson: String): Result<Unit> {
        val parsed = ProfileBackupManager.parseBackupJson(rawJson)
        if (parsed.isFailure) {
            return Result.failure(parsed.exceptionOrNull() ?: IllegalArgumentException("Invalid JSON"))
        }
        val bundle = parsed.getOrThrow()

        bundle.languageCode?.let { code ->
            setLanguage(AppLanguage.fromCode(code))
        }
        bundle.accentColorArgb?.let { argb ->
            val idx = findAccentIndex(argb)
            if (idx >= 0) setAccentIndex(idx)
        }
        updateAutomationConfig(bundle.automation)

        if (bundle.forcedModelFamily != null) {
            setUserForcedModel(_state.value.deviceAddress, bundle.forcedModelFamily)
        }

        val current = _state.value
        val restoredState = current.copy(
            noiseMode = bundle.noiseMode,
            ambientLevel = bundle.ambientLevel,
            voiceFocus = bundle.voiceFocus,
            windNoiseReduction = bundle.windNoiseReduction,
            eqState = current.eqState.copy(
                presetId = bundle.eqPresetId,
                clearBass = bundle.eqClearBass,
                isTenBand = bundle.eqIsTenBand,
                bands = if (bundle.eqBands.isNotEmpty()) bundle.eqBands else current.eqState.bands
            ),
            dseeEnabled = bundle.dseeEnabled ?: current.dseeEnabled,
            connectionPriority = bundle.connectionPriority ?: current.connectionPriority,
            autoPowerOff = bundle.autoPowerOff ?: current.autoPowerOff,
            speakToChatEnabled = bundle.speakToChatEnabled ?: current.speakToChatEnabled,
            speakToChatSensitivity = bundle.speakToChatSensitivity,
            speakToChatReturnTime = bundle.speakToChatReturnTime,
            speakToChatFocusOnVoice = bundle.speakToChatFocusOnVoice,
            pauseWhenRemoved = bundle.pauseWhenRemoved ?: current.pauseWhenRemoved,
            touchLeftPreset = bundle.touchLeftPreset ?: current.touchLeftPreset,
            touchRightPreset = bundle.touchRightPreset ?: current.touchRightPreset,
            touchPanelEnabled = bundle.touchPanelEnabled ?: current.touchPanelEnabled
        )
        _state.value = restoredState
        saveSettings()

        if (restoredState.isConnected && restoredState.deviceAddress.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                val mac = restoredState.deviceAddress
                val name = restoredState.deviceName
                if (restoredState.effectiveHasAnc) {
                    rfcommClient.setNoiseMode(
                        mac, name, bundle.noiseMode, bundle.ambientLevel, bundle.voiceFocus, restoredState
                    )
                }
                val caps = restoredState.capabilities
                if (caps.supportsEq) {
                    if (bundle.eqBands.isNotEmpty() &&
                        (bundle.eqPresetId == EqPreset.CUSTOM.id || bundle.eqPresetId == EqPreset.UNSPECIFIED.id)
                    ) {
                        rfcommClient.setEqualizerCustom(mac, name, restoredState, bundle.eqClearBass, bundle.eqBands)
                    } else {
                        rfcommClient.setEqualizerPreset(mac, name, restoredState, bundle.eqPresetId)
                    }
                }
                bundle.dseeEnabled?.let { if (caps.supportsDsee) rfcommClient.setDseeEnabled(mac, name, restoredState, it) }
                bundle.connectionPriority?.let { if (caps.supportsConnectionPriority) rfcommClient.setConnectionPriority(mac, name, restoredState, it) }
                bundle.autoPowerOff?.let { if (caps.supportsAutoPowerOff) rfcommClient.setAutoPowerOff(mac, name, restoredState, it) }
                bundle.speakToChatEnabled?.let { if (caps.supportsSpeakToChat) rfcommClient.setSpeakToChatEnabled(mac, name, restoredState, it) }
                if (caps.supportsSpeakToChat && restoredState.protocolVersion >= 2) {
                    rfcommClient.setSpeakToChatConfig(
                        mac, name, restoredState,
                        bundle.speakToChatSensitivity,
                        bundle.speakToChatReturnTime,
                        bundle.speakToChatFocusOnVoice
                    )
                }
                bundle.pauseWhenRemoved?.let { if (caps.supportsPauseWhenRemoved) rfcommClient.setPauseWhenRemoved(mac, name, restoredState, it) }
                if (caps.supportsWindNoiseReduction) {
                    rfcommClient.setWindNoiseReduction(mac, name, restoredState, bundle.windNoiseReduction)
                }
                if (caps.supportsTouchAssignment && bundle.touchLeftPreset != null && bundle.touchRightPreset != null) {
                    rfcommClient.setTouchAssignment(
                        mac, name, restoredState,
                        listOfNotNull(bundle.touchLeftPreset, bundle.touchRightPreset, restoredState.touchThirdPreset)
                    )
                }
                bundle.touchPanelEnabled?.let { if (caps.supportsTouchPanelToggle) rfcommClient.setTouchPanelEnabled(mac, name, restoredState, it) }
            }
        }
        return Result.success(Unit)
    }

    // =========================================================================
    // HISTORIAL Y PERSISTENCIA LOCAL (settings.json)
    // =========================================================================

    private fun recordDeviceInHistory(s: HeadsetState) {
        if (s.deviceAddress.isBlank()) return
        val current = _history.value.toMutableList()
        current.removeAll { it.mac.equals(s.deviceAddress, ignoreCase = true) }
        current.add(
            0,
            DeviceHistoryRecord(
                mac = s.deviceAddress,
                name = s.deviceName,
                deviceType = s.deviceType,
                leftLevel = s.leftBattery.level,
                rightLevel = s.rightBattery.level,
                caseLevel = s.caseBattery.level,
                singleLevel = s.singleBattery.level,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        )
        if (current.size > MAX_DEVICE_HISTORY) {
            _history.value = current.take(MAX_DEVICE_HISTORY)
        } else {
            _history.value = current
        }
    }

    private fun loadSettings() {
        try {
            if (!settingsFile.exists()) return
            val obj = JSONObject(settingsFile.readText(Charsets.UTF_8))

            val mac = obj.optString("macAddress", "")
            val name = obj.optString("deviceName", "WF-1000XM6").ifBlank { "WF-1000XM6" }
            val forcedFam = obj.optString("forcedFamily", "").takeIf { it.isNotBlank() }?.let {
                runCatching { SonyModelFamily.valueOf(it) }.getOrNull()
            }
            _themeMode.value = obj.optInt("themeMode", 2).coerceIn(0, 2)
            _isOled.value = obj.optBoolean("isOled", true)
            _accentIndex.value = obj.optInt("accentIndex", 0).coerceIn(0, ACCENT_PALETTE_ARGB.lastIndex)
            _language.value = AppLanguage.fromCode(obj.optString("language", "es"))
            _automationConfig.value = LocalAutomationConfig.fromJsonObject(obj.optJSONObject("automation"))

            val cachesObj = obj.optJSONObject("deviceCaches")
            if (cachesObj != null) {
                for (key in cachesObj.keys()) {
                    val c = cachesObj.optJSONObject(key) ?: continue
                    val eqBandsArr = c.optJSONArray("eqBands")
                    val eqBands = if (eqBandsArr != null) List(eqBandsArr.length()) { i -> eqBandsArr.optInt(i, 0) } else emptyList()
                    deviceCaches[key.uppercase()] = CachedDeviceProfile(
                        cachedFamily = c.optString("cachedFamily", "").takeIf { it.isNotBlank() }?.let { runCatching { SonyModelFamily.valueOf(it) }.getOrNull() },
                        forcedFamily = c.optString("forcedFamily", "").takeIf { it.isNotBlank() }?.let { runCatching { SonyModelFamily.valueOf(it) }.getOrNull() },
                        hardwareModelName = c.optString("hwModel", ""),
                        firmwareVersion = c.optString("fwVersion", ""),
                        protocolVersion = c.optInt("protoVersion", 2),
                        supportFunctions = c.optJSONArray("t1").toIntSet(),
                        table2SupportFunctions = c.optJSONArray("t2").toIntSet(),
                        gsBooleanConfirmed = c.optJSONArray("gs").toIntSet(),
                        eqState = EqState(
                            isSupported = c.optBoolean("eqSupported", false),
                            isLoaded = c.optBoolean("eqLoaded", false),
                            presetId = c.optInt("eqPresetId", 0),
                            clearBass = c.optInt("eqClearBass", 0),
                            bands = eqBands,
                            isTenBand = c.optBoolean("eqIsTenBand", false)
                        ),
                        dseeEnabled = c.optNullableBoolean("dsee"),
                        connectionPriority = c.optString("connPrio", "").takeIf { it.isNotBlank() }?.let { runCatching { ConnectionPriority.valueOf(it) }.getOrNull() },
                        autoPowerOff = c.optString("autoOff", "").takeIf { it.isNotBlank() }?.let { runCatching { AutoPowerOffOption.valueOf(it) }.getOrNull() },
                        speakToChatEnabled = c.optNullableBoolean("stcEnabled"),
                        speakToChatSensitivity = c.optString("stcSens", "").takeIf { it.isNotBlank() }?.let { runCatching { SpeakToChatSensitivity.valueOf(it) }.getOrNull() } ?: SpeakToChatSensitivity.AUTO,
                        speakToChatReturnTime = c.optString("stcRet", "").takeIf { it.isNotBlank() }?.let { runCatching { SpeakToChatReturnTime.valueOf(it) }.getOrNull() } ?: SpeakToChatReturnTime.MID,
                        speakToChatFocusOnVoice = c.optBoolean("stcFocus", false),
                        pauseWhenRemoved = c.optNullableBoolean("wear"),
                        windNoiseReduction = c.optBoolean("wind", false),
                        voiceGuidanceEnabled = c.optNullableBoolean("vgEnabled"),
                        voiceGuidanceLanguage = c.optString("vgLang", "").takeIf { it.isNotBlank() }?.let { runCatching { VoiceGuidanceLanguageOption.valueOf(it) }.getOrNull() } ?: VoiceGuidanceLanguageOption.UNDEFINED,
                        voiceGuidanceVolume = c.optInt("vgVol", 0),
                        multipointEnabled = c.optNullableBoolean("mpEnabled"),
                        touchLeftPreset = c.optString("touchLeft", "").takeIf { it.isNotBlank() }?.let { runCatching { TouchPresetOption.valueOf(it) }.getOrNull() },
                        touchRightPreset = c.optString("touchRight", "").takeIf { it.isNotBlank() }?.let { runCatching { TouchPresetOption.valueOf(it) }.getOrNull() },
                        touchThirdPreset = c.optString("touchThird", "").takeIf { it.isNotBlank() }?.let { runCatching { TouchPresetOption.valueOf(it) }.getOrNull() },
                        touchPanelEnabled = c.optNullableBoolean("touchPanel")
                    )
                }
            }

            val histArr = obj.optJSONArray("history")
            if (histArr != null) {
                val loadedHist = mutableListOf<DeviceHistoryRecord>()
                for (i in 0 until histArr.length()) {
                    val h = histArr.optJSONObject(i) ?: continue
                    val devType = runCatching { DeviceType.valueOf(h.optString("deviceType", DeviceType.TWS.name)) }
                        .getOrDefault(DeviceType.TWS)
                    loadedHist.add(
                        DeviceHistoryRecord(
                            mac = h.optString("mac", ""),
                            name = h.optString("name", ""),
                            deviceType = devType,
                            leftLevel = h.optInt("leftLevel", -1),
                            rightLevel = h.optInt("rightLevel", -1),
                            caseLevel = h.optInt("caseLevel", -1),
                            singleLevel = h.optInt("singleLevel", -1),
                            lastSeenTimestamp = h.optLong("lastSeenTimestamp", 0L)
                        )
                    )
                }
                _history.value = loadedHist.take(MAX_DEVICE_HISTORY)
            }

            if (mac.isNotBlank()) {
                applyCachedStateForDevice(mac, name)
                if (forcedFam != null) {
                    _state.value = _state.value.copy(forcedModelFamily = forcedFam, deviceType = forcedFam.defaultDeviceType)
                }
            } else {
                val effectiveFam = forcedFam ?: SonyModelFamily.detect(name)
                _state.value = _state.value.copy(
                    deviceName = name,
                    forcedModelFamily = forcedFam,
                    deviceType = effectiveFam.defaultDeviceType
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun saveSettings() {
        try {
            settingsFile.parentFile?.mkdirs()
            val cur = _state.value
            val root = JSONObject().apply {
                put("macAddress", cur.deviceAddress)
                put("deviceName", cur.deviceName)
                put("forcedFamily", cur.forcedModelFamily?.name ?: "")
                put("themeMode", _themeMode.value)
                put("isOled", _isOled.value)
                put("accentIndex", _accentIndex.value)
                put("language", _language.value.code)
                put("automation", _automationConfig.value.toJson())

                val cachesObj = JSONObject()
                deviceCaches.forEach { (macKey, profile) ->
                    cachesObj.put(
                        macKey,
                        JSONObject().apply {
                            profile.cachedFamily?.let { put("cachedFamily", it.name) }
                            profile.forcedFamily?.let { put("forcedFamily", it.name) }
                            if (profile.hardwareModelName.isNotBlank()) put("hwModel", profile.hardwareModelName)
                            if (profile.firmwareVersion.isNotBlank()) put("fwVersion", profile.firmwareVersion)
                            put("protoVersion", profile.protocolVersion)
                            put("t1", JSONArray(profile.supportFunctions.toList()))
                            put("t2", JSONArray(profile.table2SupportFunctions.toList()))
                            put("gs", JSONArray(profile.gsBooleanConfirmed.toList()))
                            put("eqSupported", profile.eqState.isSupported)
                            put("eqLoaded", profile.eqState.isLoaded)
                            put("eqPresetId", profile.eqState.presetId)
                            put("eqClearBass", profile.eqState.clearBass)
                            put("eqIsTenBand", profile.eqState.isTenBand)
                            put("eqBands", JSONArray(profile.eqState.bands))
                            profile.dseeEnabled?.let { put("dsee", it) }
                            profile.connectionPriority?.let { put("connPrio", it.name) }
                            profile.autoPowerOff?.let { put("autoOff", it.name) }
                            profile.speakToChatEnabled?.let { put("stcEnabled", it) }
                            put("stcSens", profile.speakToChatSensitivity.name)
                            put("stcRet", profile.speakToChatReturnTime.name)
                            put("stcFocus", profile.speakToChatFocusOnVoice)
                            profile.pauseWhenRemoved?.let { put("wear", it) }
                            put("wind", profile.windNoiseReduction)
                            profile.voiceGuidanceEnabled?.let { put("vgEnabled", it) }
                            put("vgLang", profile.voiceGuidanceLanguage.name)
                            put("vgVol", profile.voiceGuidanceVolume)
                            profile.multipointEnabled?.let { put("mpEnabled", it) }
                            profile.touchLeftPreset?.let { put("touchLeft", it.name) }
                            profile.touchRightPreset?.let { put("touchRight", it.name) }
                            profile.touchThirdPreset?.let { put("touchThird", it.name) }
                            profile.touchPanelEnabled?.let { put("touchPanel", it) }
                        }
                    )
                }
                put("deviceCaches", cachesObj)

                val histArr = JSONArray()
                _history.value.forEach { rec ->
                    histArr.put(
                        JSONObject().apply {
                            put("mac", rec.mac)
                            put("name", rec.name)
                            put("deviceType", rec.deviceType.name)
                            put("leftLevel", rec.leftLevel)
                            put("rightLevel", rec.rightLevel)
                            put("caseLevel", rec.caseLevel)
                            put("singleLevel", rec.singleLevel)
                            put("lastSeenTimestamp", rec.lastSeenTimestamp)
                        }
                    )
                }
                put("history", histArr)
            }
            settingsFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun shutdown() {
        try {
            pollingJob?.cancel()
            scheduleJob?.cancel()
            activeSyncJob?.cancel()
            activeModeChangeJob?.cancel()
            extendedSyncJob?.cancel()
            findMyBudsPlayer.shutdown()
            audioMonitor.shutdown()
            rfcommClient.disconnect("Cierre de aplicación")
            saveSettings()
        } catch (_: Throwable) {}
    }

    private fun JSONArray?.toIntSet(): Set<Int> {
        if (this == null) return emptySet()
        val out = LinkedHashSet<Int>(length())
        for (i in 0 until length()) {
            out.add(optInt(i))
        }
        return out
    }

    private fun JSONObject.optNullableBoolean(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    companion object {
        private const val MAX_DEVICE_HISTORY = 20
        private const val MAX_HEX_HISTORY = 180
        private const val EXTENDED_AUTO_SYNC_COOLDOWN_MS = 120_000L

        val ACCENT_PALETTE_ARGB = intArrayOf(
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

        fun findAccentIndex(argb: Int): Int =
            ACCENT_PALETTE_ARGB.indexOfFirst { it == argb }
    }
}
