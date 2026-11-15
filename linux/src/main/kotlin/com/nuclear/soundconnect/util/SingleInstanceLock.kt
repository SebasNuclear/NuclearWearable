package com.nuclear.soundconnect.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Evita múltiples instancias simultáneas de NuclearWearable Linux en sesión de usuario.
 * Si ya existe una instancia viva, envía señal "SHOW" por loopback para enfocar la ventana existente.
 */
object SingleInstanceLock {
    private const val LOCK_PORT = 47832
    private var serverSocket: ServerSocket? = null

    fun acquire(onShowRequested: () -> Unit): Boolean {
        return try {
            val ss = ServerSocket(LOCK_PORT, 1, InetAddress.getByName("127.0.0.1"))
            serverSocket = ss
            Thread({
                while (!ss.isClosed) {
                    try {
                        val client = ss.accept()
                        client.use { s ->
                            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                            val line = reader.readLine()
                            if (line == "SHOW") {
                                onShowRequested()
                            }
                        }
                    } catch (_: Exception) {
                        if (ss.isClosed) break
                    }
                }
            }, "NuclearWearable-LinuxSingleInstance").apply {
                isDaemon = true
                start()
            }
            true
        } catch (_: Exception) {
            try {
                Socket(InetAddress.getByName("127.0.0.1"), LOCK_PORT).use { s ->
                    s.getOutputStream().write("SHOW\n".toByteArray(Charsets.UTF_8))
                    s.getOutputStream().flush()
                }
            } catch (_: Exception) {}
            false
        }
    }

    fun release() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
    }
}
