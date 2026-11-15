package com.nuclear.soundconnect.tile

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.nuclear.soundconnect.R
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.HeadsetState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyHeadsetRepository
import com.nuclear.soundconnect.ui.AppLanguage
import com.nuclear.soundconnect.ui.AppStrings
import com.nuclear.soundconnect.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Tile nativo de Ajustes Rápidos de Android (Fase 3.1).
 * Cicla entre ANC -> Ambiente -> Desactivado con un solo toque, mostrando el estado de batería
 * y modelo activo usando íconos monocromáticos compatibles con Material You / One UI.
 */
class NuclearQuickSettingsTileService : TileService() {

    companion object {
        fun requestRefresh(context: Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, NuclearQuickSettingsTileService::class.java)
                )
            } catch (_: Exception) {}
        }
    }

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        renderCurrentState()
    }

    override fun onStartListening() {
        super.onStartListening()
        val repo = SonyHeadsetRepository.getInstance(applicationContext)
        renderState(repo.state.value, repo.language.value)

        collectorJob?.cancel()
        collectorJob = tileScope.launch {
            combine(repo.state, repo.language) { s, l -> s to l }
                .collectLatest { (state, lang) ->
                    renderState(state, lang)
                }
        }

        // Refresco ligero en segundo plano si tenemos permisos y un Sony conectado sin sesión viva reciente
        if (hasBluetoothPermission()) {
            val staleMs = System.currentTimeMillis() - repo.state.value.lastSyncTime
            if (!repo.rfcommClient.isSessionActive() || staleMs > 25_000L) {
                repo.syncTelemetryAsync(silent = true)
            }
        }
    }

    override fun onStopListening() {
        collectorJob?.cancel()
        collectorJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()

        if (!hasBluetoothPermission()) {
            launchMainApp(requireUnlock = true)
            return
        }

        val repo = SonyHeadsetRepository.getInstance(applicationContext)
        val current = repo.state.value
        val lang = repo.language.value

        if (!current.isConnected) {
            val btDevice = repo.getTargetBluetoothDevice()
            if (btDevice != null && isBluetoothDeviceConnected(btDevice)) {
                renderState(current.copy(isConnected = true, isBusy = true), lang)
                repo.syncTelemetryAsync(silent = false)
            } else {
                launchMainApp(requireUnlock = isLocked)
            }
            return
        }

        if (!current.effectiveHasAnc) {
            launchMainApp(requireUnlock = isLocked)
            return
        }

        val nextMode = when (current.noiseMode) {
            NoiseControlMode.ANC -> NoiseControlMode.AMBIENT
            NoiseControlMode.AMBIENT -> NoiseControlMode.OFF
            NoiseControlMode.OFF -> NoiseControlMode.ANC
        }

        renderState(current.copy(noiseMode = nextMode, isBusy = true), lang)
        repo.setNoiseModeAsync(nextMode)
    }

    private fun renderCurrentState() {
        val repo = SonyHeadsetRepository.getInstance(applicationContext)
        renderState(repo.state.value, repo.language.value)
    }

    private fun renderState(state: HeadsetState, lang: AppLanguage) {
        val tile = qsTile ?: return
        val strings = AppStrings.get(lang)
        val hasPerm = hasBluetoothPermission()

        if (!hasPerm) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = strings.appName
            tile.subtitle = strings.btPermGrantBtn
            tile.icon = Icon.createWithResource(this, R.drawable.ic_anc_off)
            tile.stateDescription = "${tile.label}, ${tile.subtitle}"
            tile.updateTile()
            return
        }

        if (!state.isConnected) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = state.modelFamily.displayName
            tile.subtitle = strings.disconnected
            tile.icon = Icon.createWithResource(this, R.drawable.ic_anc_off)
            tile.stateDescription = "${tile.label}, ${strings.disconnected}"
            tile.updateTile()
            return
        }

        val batterySummary = formatBatterySummary(state)
        val hasAnc = state.effectiveHasAnc

        if (!hasAnc) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = state.modelFamily.displayName
            tile.subtitle = if (batterySummary.isNotBlank()) {
                "$batterySummary · ${strings.connected}"
            } else {
                strings.connected
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_anc_on)
            tile.stateDescription = "${tile.label}, ${tile.subtitle}"
            tile.updateTile()
            return
        }

        val modeLabel = when (state.noiseMode) {
            NoiseControlMode.ANC -> "ANC"
            NoiseControlMode.AMBIENT -> strings.ambientSound
            NoiseControlMode.OFF -> strings.noiseOff
        }

        val iconRes = when (state.noiseMode) {
            NoiseControlMode.ANC -> R.drawable.ic_anc_on
            NoiseControlMode.AMBIENT -> R.drawable.ic_ambient_sound
            NoiseControlMode.OFF -> R.drawable.ic_anc_off
        }

        tile.state = when (state.noiseMode) {
            NoiseControlMode.ANC, NoiseControlMode.AMBIENT -> Tile.STATE_ACTIVE
            NoiseControlMode.OFF -> Tile.STATE_INACTIVE
        }
        tile.label = modeLabel
        tile.subtitle = if (batterySummary.isNotBlank()) {
            "$batterySummary · ${state.modelFamily.displayName}"
        } else {
            state.modelFamily.displayName
        }
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.stateDescription = "$modeLabel, ${tile.subtitle}"
        tile.updateTile()
    }

    private fun formatBatterySummary(state: HeadsetState): String {
        return if (state.deviceType == DeviceType.OVER_EAR) {
            if (state.singleBattery.isAvailable) state.singleBattery.displayPercent else ""
        } else {
            val parts = mutableListOf<String>()
            if (state.leftBattery.isAvailable) parts.add("L ${state.leftBattery.level}%")
            if (state.rightBattery.isAvailable) parts.add("R ${state.rightBattery.level}%")
            when {
                parts.isNotEmpty() -> parts.joinToString(" · ")
                state.singleBattery.isAvailable -> state.singleBattery.displayPercent
                else -> ""
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun isBluetoothDeviceConnected(device: android.bluetooth.BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            (method.invoke(device) as? Boolean) ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun launchMainApp(requireUnlock: Boolean) {
        val openAction = {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }

        if (requireUnlock && isLocked) {
            unlockAndRun { openAction() }
        } else {
            openAction()
        }
    }
}
