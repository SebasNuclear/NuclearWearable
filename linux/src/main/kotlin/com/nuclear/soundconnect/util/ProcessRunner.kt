package com.nuclear.soundconnect.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String = ""
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Abstracción de ejecución de procesos externos en Linux (gdbus, busctl, sdptool, pw-dump, pactl, zenity)
 * para permitir pruebas unitarias deterministas sin depender de binarios reales en host de compilación.
 */
interface CommandRunner {
    fun run(command: List<String>, timeoutMs: Long = 3500L): CommandResult
    fun runWithStdin(command: List<String>, stdinText: String, timeoutMs: Long = 3500L): Boolean
    fun commandExists(executable: String): Boolean
}

object SystemCommandRunner : CommandRunner {
    private fun readStreamFully(stream: InputStream): String {
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream()
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8)
    }

    override fun run(command: List<String>, timeoutMs: Long): CommandResult {
        if (command.isEmpty()) return CommandResult(-1, "", "Empty command")
        return try {
            val process = ProcessBuilder(command).start()
            val stdoutHolder = StringBuilder()
            val stderrHolder = StringBuilder()

            val tOut = Thread({
                runCatching { stdoutHolder.append(readStreamFully(process.inputStream)) }
            }, "cmd-stdout").apply { isDaemon = true; start() }

            val tErr = Thread({
                runCatching { stderrHolder.append(readStreamFully(process.errorStream)) }
            }, "cmd-stderr").apply { isDaemon = true; start() }

            val finished = process.waitFor(timeoutMs.coerceAtLeast(250L), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                tOut.join(200)
                tErr.join(200)
                return CommandResult(-1, stdoutHolder.toString(), "Timeout after ${timeoutMs}ms")
            }
            tOut.join(400)
            tErr.join(400)
            CommandResult(process.exitValue(), stdoutHolder.toString(), stderrHolder.toString())
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Process failed")
        }
    }

    override fun runWithStdin(command: List<String>, stdinText: String, timeoutMs: Long): Boolean {
        if (command.isEmpty()) return false
        return try {
            val process = ProcessBuilder(command).start()
            process.outputStream.use { out ->
                out.write(stdinText.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val finished = process.waitFor(timeoutMs.coerceAtLeast(250L), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun commandExists(executable: String): Boolean {
        val result = run(listOf("which", executable), timeoutMs = 1000L)
        return result.isSuccess && result.stdout.isNotBlank()
    }
}
