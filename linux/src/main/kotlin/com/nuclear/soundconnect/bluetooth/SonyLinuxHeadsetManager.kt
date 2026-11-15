package com.nuclear.soundconnect.bluetooth

import com.nuclear.soundconnect.audio.EarSide
import com.nuclear.soundconnect.audio.FindBudsPlaybackState
import com.nuclear.soundconnect.audio.FindMyBudsTonePlayer
import com.nuclear.soundconnect.audio.LinuxAudioCodecMonitor
import com.nuclear.soundconnect.audio.LiveCodecHudInfo
import com.nuclear.soundconnect.automation.LocalAutomationConfig
import com.nuclear.soundconnect.automation.LocalAutomationEngine
import com.nuclear.soundconnect.backup.NuclearBackupBundle
import com.nuclear.soundconnect.backup.ProfileBackupManager
import com.nuclear.soundconnect.ui.AppLanguage
import com.nuclear.soundconnect.util.CommandRunner
import com.nuclear.soundconnect.util.LinuxDesktopEnv
import com.nuclear.soundconnect.util.SystemCommandRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class KnownDeviceRecord(
    val address: String,
    val name: String,
    val modelFamily: SonyModelFamily,
    val deviceType: DeviceType = modelFamily.defaultDeviceType,
    val leftLevel: Int = -1,
    val rightLevel: Int = -1,
    val caseLevel: Int = -1,
    val singleLevel: Int = -1,
    val lastSeenMillis: Long,
    val isConnected: Boolean = false
)

/**
 * Repositorio central v1.1.0 para Linux Desktop.
 * Coordina D-Bus `org.bluez`, RFCOMM nativo JNA (`SonyRfcommClient`), códec en vivo PipeWire/PulseAudio,
 * motor de automatización local sin red, baliza estéreo Find My Buds y respaldo JSON offline en XDG config.
 */
class SonyLinuxHeadsetManager(
    private val commandRunner: CommandRunner = SystemCommandRunner,
    connector: RfcommConnector = LinuxRfcommConnector(commandRunner)
) {

    companion object {
        val NUCLEAR_ACCENT_PALETTE: List<Int> = listOf(
            0xFF00E676.toInt(), // Verde Atómico Nuclear
            0xFF10B981.toInt(), // Esmeralda
            0xFF00B0FF.toInt(), // Azul Eléctrico
            0xFF007AFF.toInt(), // Azul Zafiro One UI
            0xFF00D2FF.toInt(), // Cian Hielo Ártico
            0xFFFFB703.toInt(), // Oro Solar
            0xFFFFB300.toInt(), // Ámbar Retro
            0xFFFF5722.toInt(), // Magma Solar (Naranja)
            0xFFFF1744.toInt(), // Rojo Carmesí
            0xFFFF007F.toInt(), // Rosa Ciberpunk
            0xFFA855F7.toInt(), // Morado Neón
            0xFF8B5CF6.toInt()  // Violeta Tóxico
        )

        private const val USER_OVERRIDE_PROTECTION_MS = 5_000L
        private const val PERIODIC_SYNC_INTERVAL_MS = 45_000L
        private const val EXTENDED_SYNC_INTERVAL_MS = 120_000L
        private const val BLUEZ_POLL_INTERVAL_MS = 4_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsLock = Any()
    private val configFile: File = File(LinuxDesktopEnv.resolveConfigDir(), "settings.json")

    private val rfcommClient = SonyRfcommClient(connector)
    val codecMonitor = LinuxAudioCodecMonitor(commandRunner)
    private val findBudsPlayer = FindMyBudsTonePlayer()

    private val _state = MutableStateFlow(HeadsetState())
    val state: StateFlow<HeadsetState> = _state.asStateFlow()

    private val _hexLogs = MutableStateFlow<List<HexLogEntry>>(emptyList())
    val hexLogs: StateFlow<List<HexLogEntry>> = _hexLogs.asStateFlow()

    private val _pairedSonyDevices = MutableStateFlow<List<SonyBluetoothDevice>>(emptyList())
    val pairedSonyDevices: StateFlow<List<SonyBluetoothDevice>> = _pairedSonyDevices.asStateFlow()

    private val _isAdapterPowered = MutableStateFlow(true)
    val isAdapterPowered: StateFlow<Boolean> = _isAdapterPowered.asStateFlow()

    private val _history = MutableStateFlow<List<KnownDeviceRecord>>(emptyList())
    val history: StateFlow<List<KnownDeviceRecord>> = _history.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.ES)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(1) // 0=Light, 1=Dark, 2=System
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _isOledMode = MutableStateFlow(true)
    val isOledMode: StateFlow<Boolean> = _isOledMode.asStateFlow()

    private val _accentIndex = MutableStateFlow(0)
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    val liveCodecHud: StateFlow<LiveCodecHudInfo> = codecMonitor.hudInfo
    val findBudsState: StateFlow<FindBudsPlaybackState> = findBudsPlayer.state

    private var preferredAddress: String = ""
    private val forcedModelsByMac = ConcurrentHashMap<String, SonyModelFamily>()
    private val cachedModelsByMac = ConcurrentHashMap<String, SonyModelFamily>()
    private val cachedHardwareByMac = ConcurrentHashMap<String, String>()
    private val cachedFirmwareByMac = ConcurrentHashMap<String, String>()
    private val cachedProtoByMac = ConcurrentHashMap<String, Int>()
    private val extendedCacheByMac = ConcurrentHashMap<String, JSONObject>()

    @Volatile
    private var lastUserModeChangeAt: Long = 0L

    @Volatile
    private var lastExtendedSyncAt: Long = 0L

    private var ambientJob: Job? = null
    private var monitorJob: Job? = null

    val automationEngine: LocalAutomationEngine

    val automationConfig: StateFlow<LocalAutomationConfig>
        get() = automationEngine.config

    init {
        val loadedAuto = loadSettings()
        automationEngine = LocalAutomationEngine(
            scope = scope,
            codecMonitor = codecMonitor,
            stateProvider = { _state.value },
            applyNoiseControl = { mode, level, focus, reason ->
                applyAutomationNoiseControl(mode, level, focus, reason)
            },
            onPersistConfig = { cfg -> persistAutomationConfig(cfg) },
            initialConfig = loadedAuto
        )
        observeClientFlows()
    }

    private fun observeClientFlows() {
        scope.launch {
            rfcommClient.hexLogs.collect { entry ->
                _hexLogs.value = (_hexLogs.value + entry).takeLast(350)
            }
        }
        scope.launch {
            rfcommClient.liveStateUpdates.collect { live ->
                val cur = _state.value
                val sinceUserOverride = System.currentTimeMillis() - lastUserModeChangeAt
                val protected = if (sinceUserOverride < USER_OVERRIDE_PROTECTION_MS) {
                    live.copy(
                        noiseMode = cur.noiseMode,
                        ambientLevel = cur.ambientLevel,
                        voiceFocus = cur.voiceFocus
                    )
                } else {
                    live
                }
                val enriched = withCapabilitiesResolved(protected)
                _state.value = enriched
                saveExtendedAndModelCaches(enriched)
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

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        automationEngine.start()
        monitorJob = scope.launch {
            var lastSyncTick = 0L
            while (isActive) {
                runCatching { pollBluezAndMaintainSession() }
                val now = System.currentTimeMillis()
                val cur = _state.value
                if (cur.isConnected && now - lastSyncTick >= PERIODIC_SYNC_INTERVAL_MS) {
                    lastSyncTick = now
                    refreshCodecHud()
                    syncTelemetryInternal(silent = true)
                    if (now - lastExtendedSyncAt >= EXTENDED_SYNC_INTERVAL_MS) {
                        lastExtendedSyncAt = now
                        syncExtendedControlsInternal()
                    }
                }
                delay(BLUEZ_POLL_INTERVAL_MS)
            }
        }
    }

    fun shutdown() {
        findBudsPlayer.stop()
        automationEngine.stop()
        monitorJob?.cancel()
        monitorJob = null
        scope.launch {
            rfcommClient.closePersistentSession("Aplicación cerrada")
        }
    }

    private suspend fun pollBluezAndMaintainSession() {
        val bluezSnap = LinuxBluezMonitor.querySnapshot(commandRunner)
        _isAdapterPowered.value = bluezSnap.isAdapterPowered

        val sonyPaired = bluezSnap.devices.filter { SonyRfcommClient.isSonyAudioDevice(it) }
        _pairedSonyDevices.value = sonyPaired

        // Actualizar historial con conectados actuales
        val curHistory = _history.value.toMutableList()
        var historyChanged = false
        for (dev in sonyPaired.filter { it.isConnected }) {
            val idx = curHistory.indexOfFirst { it.address.equals(dev.address, ignoreCase = true) }
            val existing = if (idx >= 0) curHistory[idx] else null
            val fam = forcedModelsByMac[dev.address]
                ?: cachedModelsByMac[dev.address]
                ?: SonyModelFamily.detect(dev.name)
            val updated = KnownDeviceRecord(
                address = dev.address,
                name = dev.name ?: existing?.name ?: dev.address,
                modelFamily = fam,
                deviceType = fam.defaultDeviceType,
                leftLevel = existing?.leftLevel ?: -1,
                rightLevel = existing?.rightLevel ?: -1,
                caseLevel = existing?.caseLevel ?: -1,
                singleLevel = dev.osBatteryPercent ?: existing?.singleLevel ?: -1,
                lastSeenMillis = System.currentTimeMillis(),
                isConnected = true
            )
            if (idx >= 0) {
                curHistory[idx] = updated
            } else {
                curHistory.add(0, updated)
            }
            historyChanged = true
        }
        // Reflejar isConnected real en registros existentes
        for (i in curHistory.indices) {
            val isConn = sonyPaired.any { it.address.equals(curHistory[i].address, ignoreCase = true) && it.isConnected }
            if (curHistory[i].isConnected != isConn) {
                curHistory[i] = curHistory[i].copy(isConnected = isConn)
            }
        }
        if (historyChanged) {
            _history.value = curHistory.sortedByDescending { it.lastSeenMillis }
            saveSettings()
        }

        val target = selectTargetFromList(sonyPaired, preferredAddress.ifBlank { null })
        if (target == null) {
            if (_state.value.isConnected) {
                rfcommClient.closePersistentSession("Sin dispositivos Sony disponibles")
                _state.value = _state.value.copy(isConnected = false, isBusy = false)
            }
            return
        }

        val isNewOrChangedTarget = !_state.value.deviceAddress.equals(target.address, ignoreCase = true)
        if (!target.isConnected) {
            if (_state.value.isConnected) {
                rfcommClient.closePersistentSession("Bluetooth desconectado")
            }
            val seeded = seedStateForDevice(target, connected = false)
            _state.value = seeded
            return
        }

        if (isNewOrChangedTarget || !_state.value.isConnected || !rfcommClient.isSessionActive(target.address)) {
            val seeded = seedStateForDevice(target, connected = true)
            _state.value = seeded
            refreshCodecHud()

            val res = rfcommClient.ensurePersistentSession(target, seeded)
            if (res.isSuccess) {
                val synced = withCapabilitiesResolved(res.getOrThrow().copy(isConnected = true))
                _state.value = synced
                saveExtendedAndModelCaches(synced)
                refreshCodecHud()
                lastExtendedSyncAt = System.currentTimeMillis()
                syncExtendedControlsInternal()
            } else {
                // Fallback limpio de batería OS BlueZ si RFCOMM falla momentáneamente
                val fallbackPct = target.osBatteryPercent
                if (fallbackPct != null) {
                    val fState = if (seeded.deviceType == DeviceType.OVER_EAR) {
                        seeded.copy(singleBattery = BatteryInfo(fallbackPct, false))
                    } else {
                        seeded.copy(leftBattery = BatteryInfo(fallbackPct, false))
                    }
                    _state.value = fState
                }
            }
        }
    }

    private fun selectTargetFromList(
        sonyDevices: List<SonyBluetoothDevice>,
        prefAddr: String?
    ): SonyBluetoothDevice? {
        if (sonyDevices.isEmpty()) return null
        if (!prefAddr.isNullOrBlank()) {
            val p = sonyDevices.firstOrNull { it.address.equals(prefAddr, ignoreCase = true) }
            if (p != null && p.isConnected) return p
        }
        val connected = sonyDevices.filter { it.isConnected }
        if (connected.isNotEmpty()) {
            if (!prefAddr.isNullOrBlank()) {
                connected.firstOrNull { it.address.equals(prefAddr, ignoreCase = true) }?.let { return it }
            }
            return connected.first()
        }
        if (!prefAddr.isNullOrBlank()) {
            sonyDevices.firstOrNull { it.address.equals(prefAddr, ignoreCase = true) }?.let { return it }
        }
        return sonyDevices.first()
    }

    private fun seedStateForDevice(device: SonyBluetoothDevice, connected: Boolean): HeadsetState {
        val mac = device.address.uppercase()
        val devName = device.name ?: mac
        val forced = forcedModelsByMac[mac]
        val cached = cachedModelsByMac[mac]
        val cachedHw = cachedHardwareByMac[mac] ?: ""
        val cachedFw = cachedFirmwareByMac[mac] ?: ""
        val cachedProto = cachedProtoByMac[mac] ?: 2
        val baseType = rfcommClient.detectDeviceType(device, forced)

        var state = HeadsetState(
            deviceName = devName,
            deviceAddress = mac,
            isConnected = connected,
            deviceType = baseType,
            protocolVersion = cachedProto,
            hardwareModelName = cachedHw,
            firmwareVersion = cachedFw,
            forcedModelFamily = forced,
            cachedModelFamily = cached
        )

        // Aplicar caché extendida si existe para respuesta UI instantánea
        extendedCacheByMac[mac]?.let { ext ->
            state = state.copy(
                noiseMode = runCatching { NoiseControlMode.valueOf(ext.optString("noiseMode", NoiseControlMode.ANC.name)) }
                    .getOrDefault(state.noiseMode),
                ambientLevel = ext.optInt("ambientLevel", state.ambientLevel).coerceIn(1, 20),
                voiceFocus = ext.optBoolean("voiceFocus", state.voiceFocus),
                dseeEnabled = if (ext.has("dseeEnabled")) ext.optBoolean("dseeEnabled") else state.dseeEnabled,
                connectionPriority = ext.optString("connectionPriority", "")
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { ConnectionPriority.valueOf(it) }.getOrNull() }
                    ?: state.connectionPriority,
                autoPowerOff = ext.optString("autoPowerOff", "")
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { AutoPowerOffOption.valueOf(it) }.getOrNull() }
                    ?: state.autoPowerOff,
                speakToChatEnabled = if (ext.has("speakToChatEnabled")) ext.optBoolean("speakToChatEnabled") else state.speakToChatEnabled,
                pauseWhenRemoved = if (ext.has("pauseWhenRemoved")) ext.optBoolean("pauseWhenRemoved") else state.pauseWhenRemoved,
                multipointEnabled = if (ext.has("multipointEnabled")) ext.optBoolean("multipointEnabled") else state.multipointEnabled,
                touchPanelEnabled = if (ext.has("touchPanelEnabled")) ext.optBoolean("touchPanelEnabled") else state.touchPanelEnabled
            )
        }

        return withCapabilitiesResolved(state)
    }

    private fun withCapabilitiesResolved(raw: HeadsetState): HeadsetState = raw

    fun selectDevice(address: String) {
        val mac = address.uppercase()
        preferredAddress = mac
        saveSettings()
        scope.launch {
            rfcommClient.closePersistentSession("Cambio manual de dispositivo")
            val snap = LinuxBluezMonitor.querySnapshot(commandRunner)
            val dev = snap.devices.firstOrNull { it.address.equals(mac, ignoreCase = true) }
            if (dev != null) {
                if (!dev.isConnected) {
                    LinuxBluezMonitor.triggerConnectDevice(mac, commandRunner)
                }
                pollBluezAndMaintainSession()
            }
        }
    }

    fun setUserForcedModel(family: SonyModelFamily?) {
        val mac = _state.value.deviceAddress
        if (mac.isNotBlank()) {
            if (family != null) {
                forcedModelsByMac[mac] = family
            } else {
                forcedModelsByMac.remove(mac)
            }
        }
        val next = withCapabilitiesResolved(
            _state.value.copy(
                forcedModelFamily = family,
                deviceType = family?.defaultDeviceType ?: _state.value.deviceType
            )
        )
        _state.value = next
        rfcommClient.updateSessionContext(next)
        saveSettings()
    }

    fun clearHistoryEntry(address: String) {
        val clean = address.uppercase()
        _history.value = _history.value.filterNot { it.address.equals(clean, ignoreCase = true) }
        forcedModelsByMac.remove(clean)
        cachedModelsByMac.remove(clean)
        saveSettings()
    }

    fun clearHexLogs() {
        _hexLogs.value = emptyList()
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        saveSettings()
    }

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode.coerceIn(0, 2)
        saveSettings()
    }

    fun setOledMode(enabled: Boolean) {
        _isOledMode.value = enabled
        saveSettings()
    }

    fun setAccentIndex(idx: Int) {
        _accentIndex.value = idx.coerceIn(0, NUCLEAR_ACCENT_PALETTE.lastIndex)
        saveSettings()
    }

    fun refresh() {
        syncTelemetryAsync(silent = false)
    }

    fun disconnectSession() {
        scope.launch {
            rfcommClient.closePersistentSession("Desconexión manual")
            _state.value = _state.value.copy(isConnected = false, isBusy = false)
        }
    }

    fun openLinuxBluetoothSettings() {
        scope.launch {
            LinuxDesktopEnv.openBluetoothSettings(commandRunner)
        }
    }

    fun refreshCodecHud() {
        val cur = _state.value
        codecMonitor.refreshNow(cur.deviceAddress, cur.codecName)
    }

    fun syncTelemetryAsync(silent: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val ok = syncTelemetryInternal(silent)
            if (ok) syncExtendedControlsInternal()
            onComplete?.invoke(ok)
        }
    }

    private suspend fun syncTelemetryInternal(silent: Boolean): Boolean {
        val target = currentTargetDevice() ?: return false
        if (!silent) {
            _state.value = _state.value.copy(isBusy = true)
        }
        val res = rfcommClient.syncTelemetry(target, _state.value)
        return if (res.isSuccess) {
            val updated = withCapabilitiesResolved(res.getOrThrow().copy(isConnected = true, isBusy = false))
            _state.value = updated
            saveExtendedAndModelCaches(updated)
            true
        } else {
            if (!silent) _state.value = _state.value.copy(isBusy = false)
            false
        }
    }

    fun syncExtendedControlsAsync(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val ok = syncExtendedControlsInternal()
            onComplete?.invoke(ok)
        }
    }

    private suspend fun syncExtendedControlsInternal(): Boolean {
        val target = currentTargetDevice() ?: return false
        val res = rfcommClient.syncExtendedControls(target, _state.value)
        return if (res.isSuccess) {
            val updated = withCapabilitiesResolved(res.getOrThrow().copy(isConnected = true, isBusy = false))
            _state.value = updated
            saveExtendedAndModelCaches(updated)
            true
        } else {
            false
        }
    }

    fun setNoiseModeAsync(mode: NoiseControlMode, onComplete: ((Boolean) -> Unit)? = null) {
        lastUserModeChangeAt = System.currentTimeMillis()
        automationEngine.markUserManualModeChange()
        val prev = _state.value
        val optimistic = prev.copy(noiseMode = mode)
        _state.value = optimistic

        scope.launch {
            val target = currentTargetDevice()
            if (target == null) {
                onComplete?.invoke(false)
                return@launch
            }
            val res = rfcommClient.setNoiseMode(
                target,
                mode,
                optimistic.ambientLevel,
                optimistic.voiceFocus,
                optimistic
            )
            if (res.isSuccess) {
                val applied = withCapabilitiesResolved(res.getOrThrow().copy(isConnected = true, isBusy = false))
                _state.value = applied
                saveExtendedAndModelCaches(applied)
                onComplete?.invoke(true)
            } else {
                onComplete?.invoke(false)
            }
        }
    }

    fun setAmbientLevel(level: Int) {
        lastUserModeChangeAt = System.currentTimeMillis()
        automationEngine.markUserManualModeChange()
        val clamped = level.coerceIn(1, 20)
        val optimistic = _state.value.copy(
            noiseMode = NoiseControlMode.AMBIENT,
            ambientLevel = clamped
        )
        _state.value = optimistic
        ambientJob?.cancel()
        ambientJob = scope.launch {
            delay(85L)
            val target = currentTargetDevice() ?: return@launch
            rfcommClient.setNoiseMode(
                target,
                NoiseControlMode.AMBIENT,
                clamped,
                optimistic.voiceFocus,
                optimistic
            ).onSuccess {
                val applied = withCapabilitiesResolved(it.copy(isConnected = true, isBusy = false))
                _state.value = applied
                saveExtendedAndModelCaches(applied)
            }
        }
    }

    fun setVoiceFocus(enabled: Boolean) {
        lastUserModeChangeAt = System.currentTimeMillis()
        automationEngine.markUserManualModeChange()
        val optimistic = _state.value.copy(
            noiseMode = NoiseControlMode.AMBIENT,
            voiceFocus = enabled
        )
        _state.value = optimistic
        scope.launch {
            val target = currentTargetDevice() ?: return@launch
            rfcommClient.setNoiseMode(
                target,
                NoiseControlMode.AMBIENT,
                optimistic.ambientLevel,
                enabled,
                optimistic
            ).onSuccess {
                val applied = withCapabilitiesResolved(it.copy(isConnected = true, isBusy = false))
                _state.value = applied
                saveExtendedAndModelCaches(applied)
            }
        }
    }

    private fun applyAutomationNoiseControl(
        mode: NoiseControlMode,
        ambientLevel: Int,
        voiceFocus: Boolean,
        @Suppress("UNUSED_PARAMETER") reason: String
    ) {
        val cur = _state.value
        if (!cur.isConnected || !cur.effectiveHasAnc) return
        val next = cur.copy(noiseMode = mode, ambientLevel = ambientLevel, voiceFocus = voiceFocus)
        _state.value = next
        scope.launch {
            val target = currentTargetDevice() ?: return@launch
            rfcommClient.setNoiseMode(target, mode, ambientLevel, voiceFocus, next).onSuccess {
                _state.value = withCapabilitiesResolved(it.copy(isConnected = true, isBusy = false))
            }
        }
    }

    fun setEqualizerPresetAsync(presetId: Int, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setEqualizerPreset(target, cur, presetId)
        }

    fun setEqualizerCustomAsync(clearBass: Int, bands: List<Int>, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setEqualizerCustom(target, cur, clearBass, bands)
        }

    fun setDseeEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setDseeEnabled(target, cur, enabled)
        }

    fun setConnectionPriorityAsync(priority: ConnectionPriority, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setConnectionPriority(target, cur, priority)
        }

    fun setAutoPowerOffAsync(option: AutoPowerOffOption, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setAutoPowerOff(target, cur, option)
        }

    fun setSpeakToChatEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setSpeakToChatEnabled(target, cur, enabled)
        }

    fun setSpeakToChatConfigAsync(
        sensitivity: SpeakToChatSensitivity,
        returnTime: SpeakToChatReturnTime,
        focusOnVoice: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) = launchDeviceTx(onComplete) { target, cur ->
        rfcommClient.setSpeakToChatConfig(target, cur, sensitivity, returnTime, focusOnVoice)
    }

    fun setPauseWhenRemovedAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setPauseWhenRemoved(target, cur, enabled)
        }

    fun setWindNoiseReductionAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setWindNoiseReduction(target, cur, enabled)
        }

    fun setTouchAssignmentAsync(
        left: TouchPresetOption,
        right: TouchPresetOption,
        onComplete: ((Boolean) -> Unit)? = null
    ) = launchDeviceTx(onComplete) { target, cur ->
        rfcommClient.setTouchAssignment(target, cur, listOf(left, right))
    }

    fun setTouchPanelEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setTouchPanelEnabled(target, cur, enabled)
        }

    fun setMultipointEnabledAsync(enabled: Boolean, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setMultipointEnabled(target, cur, enabled)
        }

    fun setMultipointDeviceConnectionAsync(
        targetMac: String,
        connect: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) = launchDeviceTx(onComplete) { target, cur ->
        rfcommClient.setMultipointDeviceConnection(target, cur, targetMac, connect)
    }

    fun switchMultipointActiveSourceAsync(
        targetMac: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) = launchDeviceTx(onComplete) { target, cur ->
        rfcommClient.switchMultipointActiveSource(target, cur, targetMac)
    }

    fun setVoiceGuidanceAsync(
        enabled: Boolean,
        language: VoiceGuidanceLanguageOption,
        onComplete: ((Boolean) -> Unit)? = null
    ) = launchDeviceTx(onComplete) { target, cur ->
        rfcommClient.setVoiceGuidance(target, cur, enabled, language)
    }

    fun setVoiceGuidanceVolumeAsync(volume: Int, onComplete: ((Boolean) -> Unit)? = null) =
        launchDeviceTx(onComplete) { target, cur ->
            rfcommClient.setVoiceGuidanceVolume(target, cur, volume)
        }

    fun remotePowerOffAsync(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val target = currentTargetDevice()
            if (target == null) {
                onComplete?.invoke(false)
                return@launch
            }
            val ok = rfcommClient.remotePowerOff(target, _state.value).isSuccess
            if (ok) {
                _state.value = _state.value.copy(isConnected = false, isBusy = false)
            }
            onComplete?.invoke(ok)
        }
    }

    fun startFindMyBuds(side: EarSide) {
        findBudsPlayer.start(side)
    }

    fun stopFindMyBuds() {
        findBudsPlayer.stop()
    }

    fun updateAutomationConfig(config: LocalAutomationConfig) {
        automationEngine.updateConfig(config)
    }

    fun exportBackupJson(): String {
        val argb = NUCLEAR_ACCENT_PALETTE[_accentIndex.value.coerceIn(0, NUCLEAR_ACCENT_PALETTE.lastIndex)]
        val bundle = ProfileBackupManager.createBundleFromState(
            state = _state.value.copy(accentColor = argb),
            automationConfig = automationConfig.value,
            languageCode = if (_language.value == AppLanguage.EN) "en" else "es"
        ).copy(appVersionName = "1.1.0-linux")
        return ProfileBackupManager.exportJson(bundle)
    }

    fun restoreBackupJson(rawJson: String, onApplied: ((Boolean) -> Unit)? = null): Result<NuclearBackupBundle> {
        val parsed = ProfileBackupManager.parseBackupJson(rawJson)
        val bundle = parsed.getOrElse {
            onApplied?.invoke(false)
            return Result.failure(it)
        }

        // Aplicar preferencias locales inmediatamente
        val lang = if (bundle.languageCode.equals("en", ignoreCase = true)) AppLanguage.EN else AppLanguage.ES
        _language.value = lang

        val matchedAccentIdx = NUCLEAR_ACCENT_PALETTE.indexOfFirst { it == bundle.accentColorArgb }
        if (matchedAccentIdx >= 0) {
            _accentIndex.value = matchedAccentIdx
        }

        automationEngine.updateConfig(bundle.automationConfig)
        bundle.forcedModelFamily?.let { setUserForcedModel(it) }
        saveSettings()

        // Aplicar ajustes de hardware al chip Sony si está conectado
        scope.launch {
            val target = currentTargetDevice()
            if (target == null) {
                onApplied?.invoke(true) // Preferencias locales restauradas offline
                return@launch
            }
            var s = _state.value.copy(
                ambientLevel = bundle.ambientLevel,
                voiceFocus = bundle.voiceFocus
            )
            rfcommClient.setNoiseMode(target, bundle.noiseMode, bundle.ambientLevel, bundle.voiceFocus, s)
                .onSuccess { s = it }
            if (bundle.eqPresetId == EqPreset.CUSTOM.id && bundle.eqBands.isNotEmpty()) {
                rfcommClient.setEqualizerCustom(target, s, bundle.eqClearBass, bundle.eqBands)
                    .onSuccess { s = it }
            } else {
                rfcommClient.setEqualizerPreset(target, s, bundle.eqPresetId)
                    .onSuccess { s = it }
            }
            bundle.dseeEnabled?.let { dsee ->
                rfcommClient.setDseeEnabled(target, s, dsee).onSuccess { s = it }
            }
            bundle.connectionPriority?.let { prio ->
                rfcommClient.setConnectionPriority(target, s, prio).onSuccess { s = it }
            }
            bundle.autoPowerOff?.let { apo ->
                rfcommClient.setAutoPowerOff(target, s, apo).onSuccess { s = it }
            }
            bundle.speakToChatEnabled?.let { stc ->
                rfcommClient.setSpeakToChatEnabled(target, s, stc).onSuccess { s = it }
                rfcommClient.setSpeakToChatConfig(
                    target, s,
                    bundle.speakToChatSensitivity,
                    bundle.speakToChatReturnTime,
                    bundle.speakToChatFocusOnVoice
                ).onSuccess { s = it }
            }
            bundle.pauseWhenRemoved?.let { pwr ->
                rfcommClient.setPauseWhenRemoved(target, s, pwr).onSuccess { s = it }
            }
            if (bundle.touchLeftPreset != null && bundle.touchRightPreset != null) {
                rfcommClient.setTouchAssignment(target, s, listOf(bundle.touchLeftPreset, bundle.touchRightPreset))
                    .onSuccess { s = it }
            }
            bundle.touchPanelEnabled?.let { tp ->
                rfcommClient.setTouchPanelEnabled(target, s, tp).onSuccess { s = it }
            }
            val finalState = withCapabilitiesResolved(s.copy(isConnected = true, isBusy = false))
            _state.value = finalState
            saveExtendedAndModelCaches(finalState)
            onApplied?.invoke(true)
        }

        return Result.success(bundle)
    }

    private fun launchDeviceTx(
        onComplete: ((Boolean) -> Unit)?,
        block: suspend (SonyBluetoothDevice, HeadsetState) -> Result<HeadsetState>
    ) {
        scope.launch {
            val target = currentTargetDevice()
            if (target == null) {
                onComplete?.invoke(false)
                return@launch
            }
            val res = block(target, _state.value)
            if (res.isSuccess) {
                val updated = withCapabilitiesResolved(res.getOrThrow().copy(isConnected = true, isBusy = false))
                _state.value = updated
                saveExtendedAndModelCaches(updated)
                onComplete?.invoke(true)
            } else {
                onComplete?.invoke(false)
            }
        }
    }

    private fun currentTargetDevice(): SonyBluetoothDevice? {
        val addr = _state.value.deviceAddress
        val paired = _pairedSonyDevices.value
        val match = paired.firstOrNull { it.address.equals(addr, ignoreCase = true) }
        if (match != null && match.isConnected) return match
        return rfcommClient.findTargetDevice(preferredAddress.ifBlank { null })
            ?.takeIf { it.isConnected }
    }

    private fun saveExtendedAndModelCaches(s: HeadsetState) {
        val mac = s.deviceAddress.uppercase()
        if (mac.isBlank()) return
        if (s.hardwareModelName.isNotBlank()) cachedHardwareByMac[mac] = s.hardwareModelName
        if (s.firmwareVersion.isNotBlank()) cachedFirmwareByMac[mac] = s.firmwareVersion
        cachedProtoByMac[mac] = s.protocolVersion
        if (s.modelFamily != SonyModelFamily.GENERIC_TWS && s.modelFamily != SonyModelFamily.GENERIC_OVER_EAR) {
            cachedModelsByMac[mac] = s.modelFamily
        }
        val extObj = JSONObject().apply {
            put("noiseMode", s.noiseMode.name)
            put("ambientLevel", s.ambientLevel)
            put("voiceFocus", s.voiceFocus)
            s.dseeEnabled?.let { put("dseeEnabled", it) }
            s.connectionPriority?.let { put("connectionPriority", it.name) }
            s.autoPowerOff?.let { put("autoPowerOff", it.name) }
            s.speakToChatEnabled?.let { put("speakToChatEnabled", it) }
            s.pauseWhenRemoved?.let { put("pauseWhenRemoved", it) }
            s.multipointEnabled?.let { put("multipointEnabled", it) }
            s.touchPanelEnabled?.let { put("touchPanelEnabled", it) }
        }
        extendedCacheByMac[mac] = extObj

        // Actualizar también historial con los últimos porcentajes de batería leídos
        val list = _history.value.toMutableList()
        val idx = list.indexOfFirst { it.address.equals(mac, ignoreCase = true) }
        val updatedRec = KnownDeviceRecord(
            address = mac,
            name = s.deviceName.ifBlank { mac },
            modelFamily = s.modelFamily,
            deviceType = s.deviceType,
            leftLevel = if (s.leftBattery.isAvailable) s.leftBattery.level else (list.getOrNull(idx)?.leftLevel ?: -1),
            rightLevel = if (s.rightBattery.isAvailable) s.rightBattery.level else (list.getOrNull(idx)?.rightLevel ?: -1),
            caseLevel = if (s.caseBattery.isAvailable) s.caseBattery.level else (list.getOrNull(idx)?.caseLevel ?: -1),
            singleLevel = if (s.singleBattery.isAvailable) s.singleBattery.level else (list.getOrNull(idx)?.singleLevel ?: -1),
            lastSeenMillis = System.currentTimeMillis(),
            isConnected = s.isConnected
        )
        if (idx >= 0) list[idx] = updatedRec else list.add(0, updatedRec)
        _history.value = list.sortedByDescending { it.lastSeenMillis }

        saveSettings()
    }

    private fun persistAutomationConfig(cfg: LocalAutomationConfig) {
        synchronized(settingsLock) {
            saveSettingsLocked(cfg)
        }
    }

    private fun saveSettings() {
        synchronized(settingsLock) {
            saveSettingsLocked(automationEngine.config.value)
        }
    }

    private fun saveSettingsLocked(autoCfg: LocalAutomationConfig) {
        runCatching {
            val root = JSONObject().apply {
                put("themeMode", _themeMode.value)
                put("isOled", _isOledMode.value)
                put("accentIndex", _accentIndex.value)
                put("languageCode", if (_language.value == AppLanguage.EN) "en" else "es")
                put("preferredAddress", preferredAddress)
                put("automation", autoCfg.toJson())

                put("forcedModels", JSONObject().apply {
                    forcedModelsByMac.forEach { (k, v) -> put(k, v.name) }
                })
                put("cachedModels", JSONObject().apply {
                    cachedModelsByMac.forEach { (k, v) -> put(k, v.name) }
                })
                put("cachedHw", JSONObject().apply {
                    cachedHardwareByMac.forEach { (k, v) -> put(k, v) }
                })
                put("cachedFw", JSONObject().apply {
                    cachedFirmwareByMac.forEach { (k, v) -> put(k, v) }
                })
                put("cachedProto", JSONObject().apply {
                    cachedProtoByMac.forEach { (k, v) -> put(k, v) }
                })
                put("extendedCache", JSONObject().apply {
                    extendedCacheByMac.forEach { (k, v) -> put(k, v) }
                })
                put("history", JSONArray().apply {
                    _history.value.take(20).forEach { rec ->
                        put(JSONObject().apply {
                            put("address", rec.address)
                            put("name", rec.name)
                            put("modelFamily", rec.modelFamily.name)
                            put("deviceType", rec.deviceType.name)
                            put("leftLevel", rec.leftLevel)
                            put("rightLevel", rec.rightLevel)
                            put("caseLevel", rec.caseLevel)
                            put("singleLevel", rec.singleLevel)
                            put("lastSeenMillis", rec.lastSeenMillis)
                        })
                    }
                })
            }
            configFile.parentFile?.mkdirs()
            configFile.writeText(root.toString(2), Charsets.UTF_8)
        }
    }

    private fun loadSettings(): LocalAutomationConfig {
        synchronized(settingsLock) {
            if (!configFile.exists()) return LocalAutomationConfig()
            return runCatching {
                val root = JSONObject(configFile.readText(Charsets.UTF_8))
                _themeMode.value = root.optInt("themeMode", 1).coerceIn(0, 2)
                _isOledMode.value = root.optBoolean("isOled", true)
                _accentIndex.value = root.optInt("accentIndex", 0).coerceIn(0, NUCLEAR_ACCENT_PALETTE.lastIndex)
                _language.value = if (root.optString("languageCode", "es").equals("en", ignoreCase = true)) {
                    AppLanguage.EN
                } else {
                    AppLanguage.ES
                }
                preferredAddress = root.optString("preferredAddress", "")

                root.optJSONObject("forcedModels")?.let { obj ->
                    obj.keys().forEach { mac ->
                        runCatching { SonyModelFamily.valueOf(obj.getString(mac)) }.getOrNull()?.let { fam ->
                            forcedModelsByMac[mac] = fam
                        }
                    }
                }
                root.optJSONObject("cachedModels")?.let { obj ->
                    obj.keys().forEach { mac ->
                        runCatching { SonyModelFamily.valueOf(obj.getString(mac)) }.getOrNull()?.let { fam ->
                            cachedModelsByMac[mac] = fam
                        }
                    }
                }
                root.optJSONObject("cachedHw")?.let { obj ->
                    obj.keys().forEach { mac -> cachedHardwareByMac[mac] = obj.optString(mac, "") }
                }
                root.optJSONObject("cachedFw")?.let { obj ->
                    obj.keys().forEach { mac -> cachedFirmwareByMac[mac] = obj.optString(mac, "") }
                }
                root.optJSONObject("cachedProto")?.let { obj ->
                    obj.keys().forEach { mac -> cachedProtoByMac[mac] = obj.optInt(mac, 2) }
                }
                root.optJSONObject("extendedCache")?.let { obj ->
                    obj.keys().forEach { mac ->
                        obj.optJSONObject(mac)?.let { extendedCacheByMac[mac] = it }
                    }
                }
                root.optJSONArray("history")?.let { arr ->
                    val list = mutableListOf<KnownDeviceRecord>()
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val addr = item.optString("address", "")
                        if (addr.isBlank()) continue
                        val fam = runCatching {
                            SonyModelFamily.valueOf(item.optString("modelFamily", ""))
                        }.getOrDefault(SonyModelFamily.GENERIC_TWS)
                        val dType = runCatching {
                            DeviceType.valueOf(item.optString("deviceType", fam.defaultDeviceType.name))
                        }.getOrDefault(fam.defaultDeviceType)
                        list += KnownDeviceRecord(
                            address = addr,
                            name = item.optString("name", addr),
                            modelFamily = fam,
                            deviceType = dType,
                            leftLevel = item.optInt("leftLevel", -1),
                            rightLevel = item.optInt("rightLevel", -1),
                            caseLevel = item.optInt("caseLevel", -1),
                            singleLevel = item.optInt("singleLevel", -1),
                            lastSeenMillis = item.optLong("lastSeenMillis", 0L)
                        )
                    }
                    _history.value = list
                }
                val autoObj = root.optJSONObject("automation")
                if (autoObj != null) LocalAutomationConfig.fromJson(autoObj) else LocalAutomationConfig()
            }.getOrDefault(LocalAutomationConfig())
        }
    }
}
