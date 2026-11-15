package com.nuclear.soundconnect.bluetooth

import com.nuclear.soundconnect.util.CommandRunner
import com.nuclear.soundconnect.util.SystemCommandRunner
import com.sun.jna.Native
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Conector RFCOMM nativo de Linux Desktop usando sockets `AF_BLUETOOTH` / `BTPROTO_RFCOMM` vía JNA `libc`,
 * con descubrimiento de dispositivos y batería de respaldo a través de `org.bluez` D-Bus (sin root).
 */
class LinuxRfcommConnector(
    private val commandRunner: CommandRunner = SystemCommandRunner
) : RfcommConnector {

    override fun isAdapterPowered(): Boolean {
        return LinuxBluezMonitor.querySnapshot(commandRunner).isAdapterPowered
    }

    override fun findPairedDevices(): List<SonyBluetoothDevice> {
        return LinuxBluezMonitor.querySnapshot(commandRunner).devices
    }

    override fun connect(device: SonyBluetoothDevice, uuid: UUID): RfcommSocketConnection? {
        val c = LinuxBluetoothNative.libc ?: return null
        val channel = LinuxBluezMonitor.resolveRfcommChannel(device.address, uuid, commandRunner)
            ?: return null

        var fd = -1
        return try {
            fd = c.socket(
                LinuxBluetoothNative.AF_BLUETOOTH,
                LinuxBluetoothNative.SOCK_STREAM,
                LinuxBluetoothNative.BTPROTO_RFCOMM
            )
            if (fd < 0) return null

            LinuxBluetoothNative.setNonBlocking(c, fd, true)
            val addr = LinuxBluetoothNative.buildSockAddrRc(device.address, channel)
            val rc = c.connect(fd, addr, addr.size())
            if (rc != 0) {
                val err = Native.getLastError()
                if (err != LinuxBluetoothNative.EINPROGRESS) {
                    closeFdSafe(c, fd)
                    return null
                }
                if (!LinuxBluetoothNative.waitPoll(c, fd, LinuxBluetoothNative.POLLOUT, timeoutMs = 4500L)) {
                    closeFdSafe(c, fd)
                    return null
                }
                if (LinuxBluetoothNative.getSocketError(c, fd) != 0) {
                    closeFdSafe(c, fd)
                    return null
                }
            }

            LinuxRfcommSocketConnection(c, fd)
        } catch (_: Exception) {
            if (fd >= 0) closeFdSafe(c, fd)
            null
        }
    }

    private fun closeFdSafe(c: LinuxBluetoothNative.LibC, fd: Int) {
        runCatching { c.shutdown(fd, LinuxBluetoothNative.SHUT_RDWR) }
        runCatching { c.close(fd) }
    }
}

private class LinuxRfcommSocketConnection(
    private val c: LinuxBluetoothNative.LibC,
    private val fd: Int
) : RfcommSocketConnection {

    private val closed = AtomicBoolean(false)

    override val isConnected: Boolean
        get() = !closed.get()

    override val inputStream: InputStream = object : InputStream() {
        private val singleByte = ByteArray(1)

        override fun read(): Int {
            val n = read(singleByte, 0, 1)
            return if (n <= 0) -1 else (singleByte[0].toInt() and 0xFF)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (closed.get()) return -1
            val chunk = ByteArray(minOf(len, 4096))

            while (!closed.get()) {
                val hasData = LinuxBluetoothNative.waitPoll(c, fd, LinuxBluetoothNative.POLLIN, timeoutMs = 200L)
                if (closed.get()) return -1
                if (!hasData) {
                    // Verificar si hubo HUP/ERR real sin datos disponibles
                    val pfd = LinuxBluetoothNative.PollFd().apply {
                        this.fd = this@LinuxRfcommSocketConnection.fd
                        this.events = LinuxBluetoothNative.POLLIN
                    }
                    val p = c.poll(arrayOf(pfd), 1, 0)
                    if (p > 0 && (pfd.revents.toInt() and (LinuxBluetoothNative.POLLHUP.toInt() or LinuxBluetoothNative.POLLERR.toInt() or LinuxBluetoothNative.POLLNVAL.toInt())) != 0) {
                        close()
                        return -1
                    }
                    continue
                }

                val r = c.recv(fd, chunk, chunk.size, 0)
                if (r > 0) {
                    System.arraycopy(chunk, 0, b, off, r)
                    return r
                }
                if (r == 0) {
                    close()
                    return -1
                }
                val err = Native.getLastError()
                if (err == LinuxBluetoothNative.EINTR || err == LinuxBluetoothNative.EAGAIN) {
                    continue
                }
                close()
                throw IOException("RFCOMM recv error errno=$err")
            }
            return -1
        }
    }

    override val outputStream: OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed.get()) throw IOException("Socket RFCOMM cerrado")
            var written = 0
            while (written < len) {
                if (closed.get()) throw IOException("Socket RFCOMM cerrado durante escritura")
                val sliceSize = minOf(len - written, 4096)
                val slice = b.copyOfRange(off + written, off + written + sliceSize)
                if (!LinuxBluetoothNative.waitPoll(c, fd, LinuxBluetoothNative.POLLOUT, timeoutMs = 2500L)) {
                    throw IOException("Timeout de escritura RFCOMM")
                }
                val s = c.send(fd, slice, slice.size, 0)
                if (s > 0) {
                    written += s
                } else {
                    val err = Native.getLastError()
                    if (err == LinuxBluetoothNative.EINTR || err == LinuxBluetoothNative.EAGAIN) {
                        continue
                    }
                    throw IOException("RFCOMM send error errno=$err")
                }
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { c.shutdown(fd, LinuxBluetoothNative.SHUT_RDWR) }
            runCatching { c.close(fd) }
        }
    }
}
