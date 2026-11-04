package com.nuclear.soundconnect.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class SingleInstanceLock(private val port: Int = 47831) {
    private var serverSocket: ServerSocket? = null

    /**
     * Intenta adquirir el candado de instancia única.
     * Si ya existe otra instancia, le envía "ACTIVATE" y retorna false.
     * Si no existe, inicia el servidor en segundo plano y retorna true.
     */
    fun tryAcquire(onActivate: () -> Unit): Boolean {
        return try {
            val loopback = InetAddress.getByName("127.0.0.1")
            val socket = ServerSocket(port, 10, loopback)
            serverSocket = socket

            val thread = Thread({
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val message = reader.readLine()
                        if (message == "ACTIVATE") {
                            onActivate()
                        }
                        client.close()
                    } catch (_: Throwable) {
                        break
                    }
                }
            }, "NuclearWearable-SingleInstance-Listener")
            thread.isDaemon = true
            thread.start()

            true
        } catch (_: Throwable) {
            // Ya hay una instancia corriendo: avisarle que se active y salir
            try {
                val client = Socket("127.0.0.1", port)
                client.soTimeout = 1000
                val writer = client.getOutputStream()
                writer.write("ACTIVATE\n".toByteArray(Charsets.UTF_8))
                writer.flush()
                client.close()
            } catch (_: Throwable) {}
            false
        }
    }

    fun release() {
        try {
            serverSocket?.close()
        } catch (_: Throwable) {}
    }
}
