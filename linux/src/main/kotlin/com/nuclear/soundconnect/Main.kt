package com.nuclear.soundconnect

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyLinuxHeadsetManager
import com.nuclear.soundconnect.ui.ACCENT_PALETTE
import com.nuclear.soundconnect.ui.AppStrings
import com.nuclear.soundconnect.ui.MainView
import com.nuclear.soundconnect.ui.NuclearTrayMenu
import com.nuclear.soundconnect.util.LinuxDesktopEnv
import com.nuclear.soundconnect.util.SingleInstanceLock
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Toolkit

fun main() {
    LinuxDesktopEnv.configureSkikoDefaults()

    var requestShowSignal: (() -> Unit)? = null
    val acquired = SingleInstanceLock.acquire {
        requestShowSignal?.invoke()
    }
    if (!acquired) return

    application {
        val manager = remember { SonyLinuxHeadsetManager() }
        val state by manager.state.collectAsState()
        val themeMode by manager.themeMode.collectAsState()
        val isOled by manager.isOledMode.collectAsState()
        val accentIndex by manager.accentIndex.collectAsState()
        val language by manager.language.collectAsState()

        val strings = remember(language) { AppStrings.get(language) }
        val accentColor = ACCENT_PALETTE.getOrNull(accentIndex) ?: Color(0xFF00E676)
        val isSystemNight = isSystemInDarkTheme()
        val isDark = when (themeMode) {
            0 -> false
            1 -> true
            else -> isSystemNight
        }

        val traySupported = remember { LinuxDesktopEnv.isTrayUsable() }
        val minimizeOnClose = remember(traySupported) {
            LinuxDesktopEnv.shouldMinimizeToTrayOnClose(traySupported)
        }

        var isMainWindowVisible by remember { mutableStateOf(true) }
        var isTrayPopupVisible by remember { mutableStateOf(false) }

        val mainWindowState = rememberWindowState(
            size = DpSize(430.dp, 720.dp),
            position = WindowPosition(Alignment.Center)
        )
        val trayPopupState = rememberWindowState(
            size = DpSize(400.dp, 340.dp),
            position = WindowPosition(Alignment.TopEnd)
        )

        LaunchedEffect(Unit) {
            requestShowSignal = {
                isMainWindowVisible = true
                mainWindowState.isMinimized = false
                isTrayPopupVisible = false
            }
            manager.startMonitoring()
        }

        val appIcon = painterResource("drawable/ic_app_icon.png")

        fun exitApp() {
            manager.shutdown()
            SingleInstanceLock.release()
            exitApplication()
        }

        // Bandeja del sistema X11 / StatusNotifier (con menú nativo + flyout NuclearUI)
        if (traySupported) {
            val trayState = rememberTrayState()
            val tooltipText = buildString {
                append("NuclearWearable - ${state.deviceName}")
                if (state.isConnected) {
                    if (state.leftBattery.isAvailable) append(" | L:${state.leftBattery.level}%")
                    if (state.rightBattery.isAvailable) append(" R:${state.rightBattery.level}%")
                    if (state.singleBattery.isAvailable) append(" B:${state.singleBattery.level}%")
                } else {
                    append(" (${strings.disconnected})")
                }
            }

            Tray(
                icon = appIcon,
                state = trayState,
                tooltip = tooltipText,
                onAction = {
                    if (isTrayPopupVisible) {
                        isTrayPopupVisible = false
                    } else {
                        val pos = computeLinuxTrayPopupPosition(400, 340)
                        trayPopupState.position = WindowPosition(pos.first.dp, pos.second.dp)
                        isTrayPopupVisible = true
                    }
                },
                menu = {
                    Item(
                        text = strings.trayOpen,
                        onClick = {
                            isMainWindowVisible = true
                            mainWindowState.isMinimized = false
                            isTrayPopupVisible = false
                        }
                    )
                    if (state.effectiveHasAnc) {
                        Separator()
                        Item(
                            text = "${if (state.noiseMode == NoiseControlMode.ANC) "● " else ""}${strings.noiseCancelling}",
                            onClick = { manager.setNoiseModeAsync(NoiseControlMode.ANC) }
                        )
                        Item(
                            text = "${if (state.noiseMode == NoiseControlMode.AMBIENT) "● " else ""}${strings.ambientSound}",
                            onClick = { manager.setNoiseModeAsync(NoiseControlMode.AMBIENT) }
                        )
                        Item(
                            text = "${if (state.noiseMode == NoiseControlMode.OFF) "● " else ""}${strings.noiseOff}",
                            onClick = { manager.setNoiseModeAsync(NoiseControlMode.OFF) }
                        )
                    }
                    Separator()
                    Item(
                        text = strings.refreshButton,
                        onClick = { manager.syncTelemetryAsync() }
                    )
                    Item(
                        text = strings.trayExit,
                        onClick = { exitApp() }
                    )
                }
            )
        }

        // Ventana Flyout de Bandeja del Sistema
        if (isTrayPopupVisible) {
            Window(
                onCloseRequest = { isTrayPopupVisible = false },
                state = trayPopupState,
                title = "NuclearWearable Quick Controls",
                icon = appIcon,
                undecorated = true,
                transparent = true,
                resizable = false,
                alwaysOnTop = true
            ) {
                NuclearTrayMenu(
                    state = state,
                    strings = strings,
                    accentColor = accentColor,
                    isDark = isDark,
                    isOled = isOled,
                    onOpenMain = {
                        isTrayPopupVisible = false
                        isMainWindowVisible = true
                        mainWindowState.isMinimized = false
                    },
                    onSelectMode = { mode -> manager.setNoiseModeAsync(mode) },
                    onRefresh = { manager.syncTelemetryAsync() },
                    onExit = { exitApp() }
                )
            }
        }

        // Ventana Principal NuclearUI v1.1.0
        Window(
            onCloseRequest = {
                if (minimizeOnClose) {
                    isMainWindowVisible = false
                } else {
                    exitApp()
                }
            },
            state = mainWindowState,
            visible = isMainWindowVisible,
            title = "NuclearWearable",
            icon = appIcon,
            undecorated = true,
            transparent = true,
            resizable = true
        ) {
            MainView(
                manager = manager,
                onMinimize = { mainWindowState.isMinimized = true },
                onClose = {
                    if (minimizeOnClose) {
                        isMainWindowVisible = false
                    } else {
                        exitApp()
                    }
                }
            )
        }
    }
}

private fun computeLinuxTrayPopupPosition(popupWidthDp: Int, popupHeightDp: Int): Pair<Int, Int> {
    val screenSize = runCatching { Toolkit.getDefaultToolkit().screenSize }.getOrNull()
    val bounds = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    }.getOrNull()

    val sw = screenSize?.width ?: 1920
    val sh = screenSize?.height ?: 1080
    val mouse = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()

    val targetX = if (mouse != null) {
        (mouse.x - popupWidthDp / 2).coerceIn(12, (sw - popupWidthDp - 12).coerceAtLeast(12))
    } else {
        (bounds?.let { it.x + it.width - popupWidthDp - 14 } ?: (sw - popupWidthDp - 14)).coerceAtLeast(12)
    }

    val targetY = if (mouse != null) {
        if (mouse.y < sh / 2) {
            // Barra superior típica de GNOME/XFCE: abrir debajo del cursor
            (mouse.y + 18).coerceIn(12, (sh - popupHeightDp - 12).coerceAtLeast(12))
        } else {
            // Barra inferior típica de KDE/Mint: abrir arriba del cursor
            (mouse.y - popupHeightDp - 18).coerceIn(12, (sh - popupHeightDp - 12).coerceAtLeast(12))
        }
    } else {
        bounds?.let { (it.y + it.height - popupHeightDp - 14).coerceAtLeast(it.y + 14) } ?: 48
    }

    return targetX to targetY
}
