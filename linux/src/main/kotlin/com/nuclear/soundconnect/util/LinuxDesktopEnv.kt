package com.nuclear.soundconnect.util

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Integración con entorno de escritorio Linux (XDG, Wayland/X11, GNOME/KDE, selectores nativos y portapapeles).
 * Funciona 100% sin root ni acceso a red.
 */
object LinuxDesktopEnv {

    val isLinux: Boolean by lazy {
        System.getProperty("os.name", "").lowercase().contains("linux")
    }

    val isWaylandSession: Boolean by lazy {
        val sessionType = System.getenv("XDG_SESSION_TYPE")?.lowercase() ?: ""
        val waylandDisplay = System.getenv("WAYLAND_DISPLAY") ?: ""
        sessionType == "wayland" || waylandDisplay.isNotBlank()
    }

    val desktopName: String by lazy {
        (System.getenv("XDG_CURRENT_DESKTOP") ?: System.getenv("DESKTOP_SESSION") ?: "").lowercase()
    }

    val isGnomeDesktop: Boolean get() = desktopName.contains("gnome") || desktopName.contains("ubuntu")
    val isKdeDesktop: Boolean get() = desktopName.contains("kde") || desktopName.contains("plasma")

    /**
     * Directorio XDG de configuración sin tocar home directamente cuando XDG_CONFIG_HOME está definido.
     * Prioridad: `$XDG_CONFIG_HOME/NuclearWearable` -> `~/.config/NuclearWearable`.
     */
    fun resolveConfigDir(
        xdgConfigHome: String? = System.getenv("XDG_CONFIG_HOME"),
        userHome: String = System.getProperty("user.home", ".")
    ): File {
        val base = if (!xdgConfigHome.isNullOrBlank()) {
            File(xdgConfigHome)
        } else {
            File(userHome, ".config")
        }
        val appDir = File(base, "NuclearWearable")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return appDir
    }

    fun isTrayUsable(): Boolean {
        return runCatching { SystemTray.isSupported() }.getOrDefault(false)
    }

    /**
     * En GNOME bajo Wayland puro sin extensión AppIndicator, AWT SystemTray suele no tener icono visible.
     * Evita ocultar la última ventana en un tray inexistente para no atrapar al usuario sin controles.
     */
    fun shouldMinimizeToTrayOnClose(trayAvailable: Boolean): Boolean {
        if (!trayAvailable) return false
        if (isLinux && isWaylandSession && isGnomeDesktop) {
            // En GNOME Wayland el soporte de XEmbed legacy es frecuentemente bloqueado por GNOME Shell.
            // Si the extension exists, SystemTray reports supported, pero cerrando conservamos acceso seguro o confirmamos.
            return System.getenv("NUCLEAR_FORCE_GNOME_TRAY") == "1"
        }
        return true
    }

    /**
     * Abre el panel de ajustes Bluetooth del entorno de escritorio Linux sin blocking UI.
     */
    fun openBluetoothSettings(runner: CommandRunner = SystemCommandRunner): Boolean {
        val candidates = mutableListOf<List<String>>()
        if (isKdeDesktop) {
            candidates += listOf("kcmshell6", "kcm_bluetooth")
            candidates += listOf("kcmshell5", "kcm_bluetooth")
            candidates += listOf("systemsettings", "kcm_bluetooth")
        }
        if (isGnomeDesktop) {
            candidates += listOf("gnome-control-center", "bluetooth")
        }
        candidates += listOf("blueman-manager")
        candidates += listOf("blueberry")
        candidates += listOf("gnome-control-center", "bluetooth")
        candidates += listOf("kcmshell6", "kcm_bluetooth")
        candidates += listOf("systemsettings")

        for (cmd in candidates) {
            if (runner.commandExists(cmd.first())) {
                val started = runCatching {
                    ProcessBuilder(cmd).start()
                    true
                }.getOrDefault(false)
                if (started) return true
            }
        }
        return false
    }

    /**
     * Copia texto al portapapeles combinando AWT Clipboard + fallback `wl-copy`/`xclip`/`xsel` en Wayland/X11.
     */
    fun copyToClipboard(text: String, runner: CommandRunner = SystemCommandRunner): Boolean {
        val awtOk = runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
            true
        }.getOrDefault(false)

        if (!isLinux) return awtOk

        val nativeCmds = if (isWaylandSession) {
            listOf(
                listOf("wl-copy"),
                listOf("xclip", "-selection", "clipboard"),
                listOf("xsel", "--clipboard", "--input")
            )
        } else {
            listOf(
                listOf("xclip", "-selection", "clipboard"),
                listOf("xsel", "--clipboard", "--input"),
                listOf("wl-copy")
            )
        }

        for (cmd in nativeCmds) {
            if (runner.commandExists(cmd.first()) && runner.runWithStdin(cmd, text)) {
                return true
            }
        }
        return awtOk
    }

    /**
     * Selector nativo para guardar archivo (.json / .txt) usando `zenity` o `kdialog` según escritorio,
     * con fallback offline a `JFileChooser` / `FileDialog`.
     */
    fun chooseSaveFile(
        title: String,
        defaultFileName: String,
        extension: String,
        extensionDescription: String,
        runner: CommandRunner = SystemCommandRunner
    ): File? {
        if (isLinux) {
            if (isKdeDesktop && runner.commandExists("kdialog")) {
                val res = runner.run(
                    listOf("kdialog", "--getsavefilename", defaultFileName, "*.$extension|$extensionDescription", "--title", title),
                    timeoutMs = 120_000L
                )
                if (res.isSuccess) {
                    val path = res.stdout.trim()
                    if (path.isNotBlank()) return ensureExtension(File(path), extension)
                }
                if (res.exitCode == 1) return null // Cancelado explícitamente por el usuario
            }
            if (runner.commandExists("zenity")) {
                val res = runner.run(
                    listOf(
                        "zenity",
                        "--file-selection",
                        "--save",
                        "--confirm-overwrite",
                        "--title=$title",
                        "--filename=$defaultFileName",
                        "--file-filter=$extensionDescription | *.$extension"
                    ),
                    timeoutMs = 120_000L
                )
                if (res.isSuccess) {
                    val path = res.stdout.trim()
                    if (path.isNotBlank()) return ensureExtension(File(path), extension)
                }
                if (res.exitCode == 1) return null
            }
        }

        val swingFile = runCatching {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                selectedFile = File(defaultFileName)
                fileFilter = FileNameExtensionFilter(extensionDescription, extension)
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                ensureExtension(chooser.selectedFile, extension)
            } else null
        }.getOrNull()
        if (swingFile != null) return swingFile

        return runCatching {
            val fd = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
                file = defaultFileName
                isVisible = true
            }
            if (fd.directory != null && fd.file != null) {
                ensureExtension(File(fd.directory, fd.file), extension)
            } else null
        }.getOrNull()
    }

    /**
     * Selector nativo para abrir archivo (.json) usando `zenity` o `kdialog` según escritorio,
     * con fallback a `JFileChooser` / `FileDialog`.
     */
    fun chooseOpenFile(
        title: String,
        extension: String,
        extensionDescription: String,
        runner: CommandRunner = SystemCommandRunner
    ): File? {
        if (isLinux) {
            if (isKdeDesktop && runner.commandExists("kdialog")) {
                val res = runner.run(
                    listOf("kdialog", "--getopenfilename", System.getProperty("user.home", "."), "*.$extension|$extensionDescription", "--title", title),
                    timeoutMs = 120_000L
                )
                if (res.isSuccess) {
                    val path = res.stdout.trim()
                    if (path.isNotBlank()) return File(path)
                }
                if (res.exitCode == 1) return null
            }
            if (runner.commandExists("zenity")) {
                val res = runner.run(
                    listOf(
                        "zenity",
                        "--file-selection",
                        "--title=$title",
                        "--file-filter=$extensionDescription | *.$extension"
                    ),
                    timeoutMs = 120_000L
                )
                if (res.isSuccess) {
                    val path = res.stdout.trim()
                    if (path.isNotBlank()) return File(path)
                }
                if (res.exitCode == 1) return null
            }
        }

        val swingFile = runCatching {
            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileFilter = FileNameExtensionFilter(extensionDescription, extension)
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile
            } else null
        }.getOrNull()
        if (swingFile != null) return swingFile

        return runCatching {
            val fd = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
                isVisible = true
            }
            if (fd.directory != null && fd.file != null) File(fd.directory, fd.file) else null
        }.getOrNull()
    }

    private fun ensureExtension(file: File, ext: String): File {
        val cleanExt = ext.trimStart('.')
        return if (file.name.endsWith(".$cleanExt", ignoreCase = true)) {
            file
        } else {
            File(file.parentFile, "${file.name}.$cleanExt")
        }
    }

    /**
     * Configura banderas estables de renderizado Skiko antes de arrancar Compose Desktop en Linux.
     * Evita artefactos de transparencia o crashes Vulkan en compositors Wayland/XWayland.
     */
    fun configureSkikoDefaults() {
        if (!isLinux) return
        if (System.getProperty("skiko.vsync.enabled") == null) {
            System.setProperty("skiko.vsync.enabled", "false")
        }
        // Si el compositor o driver Mesa tiene fallos, el usuario puede definir SKIKO_RENDER_API=SOFTWARE o OPENGL
        if (System.getProperty("skiko.renderApi") == null && System.getenv("SKIKO_RENDER_API").isNullOrBlank()) {
            // OPENGL es el backend más estable en X11 y XWayland (KDE/GNOME) para ventanas transparentes/squircle
            System.setProperty("skiko.renderApi", "OPENGL")
        }
    }
}
