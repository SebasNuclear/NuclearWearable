package com.nuclear.soundconnect

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.nuclear.soundconnect.bluetooth.DeviceType
import com.nuclear.soundconnect.bluetooth.NoiseControlMode
import com.nuclear.soundconnect.bluetooth.SonyWindowsHeadsetManager
import com.nuclear.soundconnect.ui.*
import com.nuclear.soundconnect.util.SingleInstanceLock
import java.awt.Image
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.imageio.ImageIO
import kotlin.system.exitProcess

fun main() {
    // 1. Candado de instancia única: Si ya hay otra instancia corriendo, enfocarla y salir
    val instanceLock = SingleInstanceLock(47831)
    var activateEvent by mutableStateOf(0)

    val acquired = instanceLock.tryAcquire {
        activateEvent++
    }
    if (!acquired) {
        exitProcess(0)
    }

    // 2. Registrar AppUserModelID explícito para Windows 10/11
    try {
        if (System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true) {
            val shell32 = com.sun.jna.NativeLibrary.getInstance("shell32")
            val func = shell32.getFunction("SetCurrentProcessExplicitAppUserModelID")
            func.invokeInt(arrayOf(com.sun.jna.WString("Nuclear.SoundConnect.Wearable.Windows")))
        }
    } catch (_: Throwable) {}

    application {
        val manager = remember { SonyWindowsHeadsetManager() }
        val state by manager.state.collectAsState()
        val language by manager.language.collectAsState()
        val themeMode by manager.themeMode.collectAsState()
        val isOled by manager.isOled.collectAsState()
        val accentIndex by manager.accentIndex.collectAsState()

        val accentColor = ACCENT_PALETTE.getOrNull(accentIndex) ?: androidx.compose.ui.graphics.Color(0xFF00E676)
        val isSystemNight = isSystemInDarkTheme()
        val isDark = when (themeMode) {
            0 -> false
            1 -> true
            else -> isSystemNight
        }

        var isWindowVisible by remember { mutableStateOf(true) }
        var isTrayMenuVisible by remember { mutableStateOf(false) }
        var trayMenuPosition by remember { mutableStateOf(WindowPosition(0.dp, 0.dp)) }

        val strings = remember(language) { AppStrings.get(language) }
        val appIcon = painterResource("drawable/ic_app_icon.png")

        // Si la segunda instancia envió solicitud de activación, restaurar ventana
        LaunchedEffect(activateEvent) {
            if (activateEvent > 0) {
                isWindowVisible = true
                isTrayMenuVisible = false
            }
        }

        val onCloseApp: () -> Unit = {
            manager.shutdown()
            instanceLock.release()
            exitApplication()
        }

        // Tooltip dinámico para la barra de tareas
        val trayTooltip = remember(state, language) {
            val model = state.modelFamily.displayName
            if (!state.isConnected) {
                "$model - ${strings.disconnected}"
            } else if (state.deviceType == DeviceType.OVER_EAR) {
                val bat = state.singleBattery.displayPercent
                val mode = when (state.noiseMode) {
                    NoiseControlMode.ANC -> strings.noiseCancelling
                    NoiseControlMode.AMBIENT -> strings.ambientSound
                    NoiseControlMode.OFF -> strings.noiseOff
                }
                "$model: $bat ($mode)"
            } else {
                val l = state.leftBattery.displayPercent
                val r = state.rightBattery.displayPercent
                val c = state.caseBattery.displayPercent
                val mode = when (state.noiseMode) {
                    NoiseControlMode.ANC -> strings.noiseCancelling
                    NoiseControlMode.AMBIENT -> strings.ambientSound
                    NoiseControlMode.OFF -> strings.noiseOff
                }
                "$model: L $l | R $r | Case $c ($mode)"
            }
        }

        var activeTrayIcon by remember { mutableStateOf<TrayIcon?>(null) }

        // Integración AWT nativa para la bandeja del sistema (clic izq -> restaurar, clic der -> NuclearTrayMenu)
        DisposableEffect(Unit) {
            var iconInstance: TrayIcon? = null
            if (SystemTray.isSupported()) {
                try {
                    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/ic_app_icon.png")
                    val awtImage: Image? = if (stream != null) ImageIO.read(stream) else null

                    if (awtImage != null) {
                        val tray = SystemTray.getSystemTray()
                        val icon = TrayIcon(awtImage, trayTooltip)
                        icon.isImageAutoSize = true

                        icon.addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                if (e.button == MouseEvent.BUTTON1) {
                                    // Clic izquierdo: abrir o alternar ventana principal
                                    isWindowVisible = !isWindowVisible
                                    isTrayMenuVisible = false
                                } else if (e.button == MouseEvent.BUTTON3) {
                                    // Clic derecho: desplegar menú flotante NuclearUI encima del icono
                                    val screenSize = Toolkit.getDefaultToolkit().screenSize
                                    val menuWidth = 280
                                    val menuHeight = 320
                                    val x = (e.xOnScreen - menuWidth / 2).coerceIn(10, screenSize.width - menuWidth - 10)
                                    val y = (e.yOnScreen - menuHeight - 12).coerceIn(10, screenSize.height - menuHeight - 12)

                                    trayMenuPosition = WindowPosition(x.dp, y.dp)
                                    isTrayMenuVisible = !isTrayMenuVisible
                                }
                            }
                        })

                        tray.add(icon)
                        iconInstance = icon
                        activeTrayIcon = icon
                    }
                } catch (_: Throwable) {}
            }

            onDispose {
                if (iconInstance != null) {
                    try {
                        SystemTray.getSystemTray().remove(iconInstance)
                    } catch (_: Throwable) {}
                }
                instanceLock.release()
            }
        }

        // Actualizar tooltip cuando cambie el estado
        LaunchedEffect(trayTooltip) {
            activeTrayIcon?.toolTip = trayTooltip
        }

        // Menú flotante moderno NuclearUI en la bandeja del sistema (reemplazo de Windows 95 AWT)
        if (isTrayMenuVisible) {
            Window(
                onCloseRequest = { isTrayMenuVisible = false },
                state = rememberWindowState(
                    position = trayMenuPosition,
                    size = DpSize(280.dp, 320.dp)
                ),
                title = "NuclearWearable Tray",
                undecorated = true,
                transparent = true,
                alwaysOnTop = true,
                resizable = false
            ) {
                LaunchedEffect(Unit) {
                    window.addWindowFocusListener(object : WindowFocusListener {
                        override fun windowLostFocus(e: WindowEvent) {
                            isTrayMenuVisible = false
                        }
                        override fun windowGainedFocus(e: WindowEvent) {}
                    })
                }

                NuclearTrayMenu(
                    state = state,
                    strings = strings,
                    accentColor = accentColor,
                    isDark = isDark,
                    isOled = isOled,
                    onOpenMain = {
                        isTrayMenuVisible = false
                        isWindowVisible = true
                    },
                    onSelectMode = { mode ->
                        manager.setNoiseModeAsync(mode)
                    },
                    onRefresh = {
                        manager.syncTelemetryAsync()
                    },
                    onExit = {
                        isTrayMenuVisible = false
                        onCloseApp()
                    }
                )
            }
        }

        // Ventana principal estilizada estilo Single-Surface Companion
        val windowState = rememberWindowState(
            size = DpSize(430.dp, 690.dp),
            position = WindowPosition(Alignment.Center)
        )

        Window(
            onCloseRequest = onCloseApp,
            visible = isWindowVisible,
            state = windowState,
            title = "NuclearWearable",
            icon = appIcon,
            undecorated = true,
            transparent = true,
            resizable = false
        ) {
            LaunchedEffect(Unit) {
                try {
                    // 1. Ocultar la ventana de la barra de tareas de Windows (WS_EX_TOOLWINDOW)
                    // para que viva exclusivamente en la bandeja de apps de segundo plano (System Tray)
                    val hwndPtr = com.sun.jna.Native.getWindowPointer(window)
                    if (hwndPtr != null) {
                        val hwnd = com.sun.jna.platform.win32.WinDef.HWND(hwndPtr)
                        val user32 = com.sun.jna.platform.win32.User32.INSTANCE
                        val exStyle = user32.GetWindowLong(hwnd, com.sun.jna.platform.win32.WinUser.GWL_EXSTYLE)
                        // WS_EX_TOOLWINDOW = 0x00000080, WS_EX_APPWINDOW = 0x00040000
                        val newExStyle = (exStyle or 0x00000080) and 0x00040000.inv()
                        user32.SetWindowLong(hwnd, com.sun.jna.platform.win32.WinUser.GWL_EXSTYLE, newExStyle)
                        // SWP_FRAMECHANGED (0x20) | SWP_NOMOVE (0x2) | SWP_NOSIZE (0x1) | SWP_NOZORDER (0x4) = 0x27
                        user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, 0x0027)
                    }

                    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/ic_app_icon.png")
                    if (stream != null) {
                        val image = ImageIO.read(stream)
                        window.iconImage = image
                    }
                } catch (_: Throwable) {}
            }

            LaunchedEffect(isWindowVisible, activateEvent) {
                if (isWindowVisible) {
                    window.toFront()
                    window.requestFocus()
                }
            }

            MainView(
                manager = manager,
                onMinimize = { isWindowVisible = false },
                onClose = onCloseApp
            )
        }
    }
}
