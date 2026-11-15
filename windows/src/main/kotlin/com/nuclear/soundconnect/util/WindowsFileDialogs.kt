package com.nuclear.soundconnect.util

import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/**
 * Local Windows desktop utilities for file open/save dialogs (.json / .txt),
 * clipboard copy, and launching Windows Bluetooth settings without internet/telemetry.
 */
object WindowsFileDialogs {

    fun copyToClipboard(text: String): Boolean = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)

    fun saveTextFile(defaultFileName: String, extension: String, content: String): Result<File?> = runCatching {
        val dialog = FileDialog(null as Frame?, defaultFileName, FileDialog.SAVE).apply {
            file = defaultFileName
            setFilenameFilter { _, name -> name.endsWith(".$extension", ignoreCase = true) }
            isVisible = true
        }
        val selectedDir = dialog.directory
        val selectedFile = dialog.file
        dialog.dispose()
        if (selectedDir.isNullOrBlank() || selectedFile.isNullOrBlank()) {
            return@runCatching null
        }
        val finalName = if (selectedFile.endsWith(".$extension", ignoreCase = true)) {
            selectedFile
        } else {
            "$selectedFile.$extension"
        }
        val target = File(selectedDir, finalName)
        target.writeText(content, Charsets.UTF_8)
        target
    }

    fun openTextFile(title: String, extensions: List<String>): Result<String?> = runCatching {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            setFilenameFilter { _, name ->
                extensions.any { ext -> name.endsWith(".$ext", ignoreCase = true) }
            }
            isVisible = true
        }
        val selectedDir = dialog.directory
        val selectedFile = dialog.file
        dialog.dispose()
        if (selectedDir.isNullOrBlank() || selectedFile.isNullOrBlank()) {
            return@runCatching null
        }
        File(selectedDir, selectedFile).readText(Charsets.UTF_8)
    }

    fun openBluetoothSettings(): Boolean = runCatching {
        ProcessBuilder("cmd", "/c", "start", "", "ms-settings:bluetooth")
            .redirectErrorStream(true)
            .start()
        true
    }.getOrDefault(false)
}
