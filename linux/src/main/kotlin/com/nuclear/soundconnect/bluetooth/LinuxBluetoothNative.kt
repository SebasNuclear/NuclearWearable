package com.nuclear.soundconnect.bluetooth

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Bindings JNA directos a `libc` en Linux para sockets Bluetooth Classic
 * (`AF_BLUETOOTH = 31`, `BTPROTO_RFCOMM = 3`, `BTPROTO_L2CAP = 0`), sin requerir privilegios root.
 */
object LinuxBluetoothNative {

    const val AF_BLUETOOTH: Int = 31
    const val SOCK_STREAM: Int = 1
    const val SOCK_SEQPACKET: Int = 5
    const val BTPROTO_L2CAP: Int = 0
    const val BTPROTO_RFCOMM: Int = 3

    const val SOL_SOCKET: Int = 1
    const val SO_ERROR: Int = 4
    const val SHUT_RDWR: Int = 2

    const val F_GETFL: Int = 3
    const val F_SETFL: Int = 4
    const val O_NONBLOCK: Int = 0x800 // 04000 octal en x86_64 y aarch64

    const val POLLIN: Short = 0x0001
    const val POLLOUT: Short = 0x0004
    const val POLLERR: Short = 0x0008
    const val POLLHUP: Short = 0x0010
    const val POLLNVAL: Short = 0x0020

    const val EINTR: Int = 4
    const val EAGAIN: Int = 11
    const val EWOULDBLOCK: Int = 11
    const val EINPROGRESS: Int = 115

    /**
     * `struct sockaddr_rc` de `<bluetooth/rfcomm.h>`:
     * - `sa_family_t rc_family` (2 bytes, offset 0)
     * - `bdaddr_t rc_bdaddr` (6 bytes LE en orden inverso, offset 2..7)
     * - `uint8_t rc_channel` (1 byte, offset 8)
     * + 1 byte padding de alineación natural -> tamaño total = 10 bytes.
     */
    @Structure.FieldOrder("rc_family", "rc_bdaddr", "rc_channel")
    open class SockAddrRc : Structure() {
        @JvmField var rc_family: Short = AF_BLUETOOTH.toShort()
        @JvmField var rc_bdaddr: ByteArray = ByteArray(6)
        @JvmField var rc_channel: Byte = 0
    }

    /**
     * `struct sockaddr_l2` de `<bluetooth/l2cap.h>` para SDP directo sobre L2CAP PSM 0x0001:
     * - `sa_family_t l2_family` (2 bytes, offset 0)
     * - `unsigned short l2_psm` (2 bytes LE, offset 2)
     * - `bdaddr_t l2_bdaddr` (6 bytes LE, offset 4..9)
     * - `unsigned short l2_cid` (2 bytes, offset 10..11)
     * - `uint8_t l2_bdaddr_type` (1 byte, offset 12)
     * + 1 byte padding -> tamaño total = 14 bytes.
     */
    @Structure.FieldOrder("l2_family", "l2_psm", "l2_bdaddr", "l2_cid", "l2_bdaddr_type")
    open class SockAddrL2 : Structure() {
        @JvmField var l2_family: Short = AF_BLUETOOTH.toShort()
        @JvmField var l2_psm: Short = 1
        @JvmField var l2_bdaddr: ByteArray = ByteArray(6)
        @JvmField var l2_cid: Short = 0
        @JvmField var l2_bdaddr_type: Byte = 0
    }

    @Structure.FieldOrder("fd", "events", "revents")
    open class PollFd : Structure() {
        @JvmField var fd: Int = -1
        @JvmField var events: Short = 0
        @JvmField var revents: Short = 0
    }

    interface LibC : Library {
        fun socket(domain: Int, type: Int, protocol: Int): Int
        fun connect(sockfd: Int, addr: Structure, addrlen: Int): Int
        fun send(sockfd: Int, buf: ByteArray, len: Int, flags: Int): Int
        fun recv(sockfd: Int, buf: ByteArray, len: Int, flags: Int): Int
        fun poll(fds: Array<PollFd>, nfds: Int, timeout: Int): Int
        fun fcntl(fd: Int, cmd: Int, arg: Int): Int
        fun getsockopt(sockfd: Int, level: Int, optname: Int, optval: IntByReference, optlen: IntByReference): Int
        fun shutdown(sockfd: Int, how: Int): Int
        fun close(fd: Int): Int
    }

    val libc: LibC? by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("linux")) {
            null
        } else {
            runCatching { Native.load("c", LibC::class.java) }.getOrNull()
        }
    }

    /**
     * Convierte MAC `"AA:BB:CC:DD:EE:FF"` en `bdaddr_t` de BlueZ (orden de bytes little-endian invertido).
     */
    fun parseMacToBdAddr(mac: String): ByteArray {
        val clean = mac.trim().replace("-", ":")
        val parts = clean.split(":")
        require(parts.size == 6) { "Dirección MAC Bluetooth inválida: $mac" }
        val out = ByteArray(6)
        for (i in 0 until 6) {
            val octet = parts[5 - i].toInt(16)
            require(octet in 0..255) { "Octeto MAC inválido en: $mac" }
            out[i] = octet.toByte()
        }
        return out
    }

    fun buildSockAddrRc(mac: String, channel: Int): SockAddrRc {
        require(channel in 1..30) { "Canal RFCOMM inválido: $channel" }
        return SockAddrRc().apply {
            rc_family = AF_BLUETOOTH.toShort()
            rc_bdaddr = parseMacToBdAddr(mac)
            rc_channel = channel.toByte()
            write()
        }
    }

    fun buildSockAddrL2Sdp(mac: String): SockAddrL2 {
        return SockAddrL2().apply {
            l2_family = AF_BLUETOOTH.toShort()
            l2_psm = 1 // SDP PSM 0x0001 (LE en struct sockaddr_l2)
            l2_bdaddr = parseMacToBdAddr(mac)
            l2_cid = 0
            l2_bdaddr_type = 0 // BDADDR_BREDR
            write()
        }
    }

    /**
     * Construye una petición SDP estándar `SDP_SVC_SEARCH_ATTR_REQ` (PDU 0x06) sobre L2CAP PSM 0x0001
     * solicitando el atributo `0x0004` (`ProtocolDescriptorList`) para un UUID de 128 bits.
     */
    fun buildSdpServiceSearchAttrRequest(uuid: UUID, txnId: Int = 0x0001): ByteArray {
        val uuidBytes = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()

        // Param payload:
        // ServiceSearchPattern: 0x35 0x11 [0x1C + 16B UUID] (19 bytes)
        // MaximumAttributeByteCount: 0x04 0x00 (1024 bytes) (2 bytes)
        // AttributeIDList: 0x35 0x03 [0x09 0x00 0x04] (5 bytes)
        // ContinuationState: 0x00 (1 byte)
        val paramLen = 19 + 2 + 5 + 1
        val buf = ByteBuffer.allocate(5 + paramLen).order(ByteOrder.BIG_ENDIAN)
        buf.put(0x06.toByte()) // SDP_SVC_SEARCH_ATTR_REQ
        buf.putShort(txnId.toShort())
        buf.putShort(paramLen.toShort())

        // ServiceSearchPattern DES (len 17 = 1 + 16)
        buf.put(0x35.toByte())
        buf.put(0x11.toByte())
        buf.put(0x1C.toByte())
        buf.put(uuidBytes)

        // MaximumAttributeByteCount
        buf.putShort(1024.toShort())

        // AttributeIDList DES (len 3 = uint16 0x0004)
        buf.put(0x35.toByte())
        buf.put(0x03.toByte())
        buf.put(0x09.toByte())
        buf.putShort(0x0004.toShort())

        // ContinuationState = 0
        buf.put(0x00.toByte())
        return buf.array()
    }

    /**
     * Parsea una respuesta SDP `SDP_SVC_SEARCH_ATTR_RSP` (PDU 0x07) extrayendo el canal RFCOMM (`UUID16 0x0003` -> uint8).
     */
    fun parseSdpRfcommChannelFromResponse(response: ByteArray): Int? {
        if (response.size < 8) return null
        val pduId = response[0].toInt() and 0xFF
        if (pduId != 0x07) return null

        val paramLen = ((response[3].toInt() and 0xFF) shl 8) or (response[4].toInt() and 0xFF)
        if (5 + paramLen > response.size || paramLen < 2) return null

        val attrByteCount = ((response[5].toInt() and 0xFF) shl 8) or (response[6].toInt() and 0xFF)
        val attrStart = 7
        val attrEnd = minOf(attrStart + attrByteCount, response.size)
        if (attrStart >= attrEnd) return null

        return findRfcommChannelInDataElement(response, attrStart, attrEnd)
    }

    private fun findRfcommChannelInDataElement(buf: ByteArray, start: Int, end: Int): Int? {
        var pos = start
        while (pos < end) {
            val elem = readDataElement(buf, pos, end) ?: break
            when (elem.type) {
                6, 7 -> { // DES (6) / DEA (7): buscar par [UUID 0x0003, UINT channel]
                    val nested = parseProtocolDescriptorSeq(buf, elem.payloadStart, elem.payloadEnd)
                    if (nested != null) return nested
                    val deep = findRfcommChannelInDataElement(buf, elem.payloadStart, elem.payloadEnd)
                    if (deep != null) return deep
                }
            }
            pos = elem.nextPos
        }
        return null
    }

    private fun parseProtocolDescriptorSeq(buf: ByteArray, start: Int, end: Int): Int? {
        var pos = start
        var foundRfcommUuid = false
        while (pos < end) {
            val elem = readDataElement(buf, pos, end) ?: break
            if (elem.type == 3) { // UUID
                val uuidLen = elem.payloadEnd - elem.payloadStart
                val isRfcomm = when (uuidLen) {
                    2 -> {
                        val v = ((buf[elem.payloadStart].toInt() and 0xFF) shl 8) or
                            (buf[elem.payloadStart + 1].toInt() and 0xFF)
                        v == 0x0003
                    }
                    4 -> {
                        val v = ByteBuffer.wrap(buf, elem.payloadStart, 4).order(ByteOrder.BIG_ENDIAN).int
                        v == 0x00000003
                    }
                    16 -> {
                        val msb = ByteBuffer.wrap(buf, elem.payloadStart, 8).order(ByteOrder.BIG_ENDIAN).long
                        val lsb = ByteBuffer.wrap(buf, elem.payloadStart + 8, 8).order(ByteOrder.BIG_ENDIAN).long
                        msb == 0x0000000300001000L && lsb == -0x7fffff7fa064cb05L
                    }
                    else -> false
                }
                if (isRfcomm) {
                    foundRfcommUuid = true
                }
            } else if (foundRfcommUuid && elem.type == 1) { // UNSIGNED INT
                val size = elem.payloadEnd - elem.payloadStart
                if (size == 1) {
                    val ch = buf[elem.payloadStart].toInt() and 0xFF
                    if (ch in 1..30) return ch
                } else if (size == 2) {
                    val ch = ((buf[elem.payloadStart].toInt() and 0xFF) shl 8) or
                        (buf[elem.payloadStart + 1].toInt() and 0xFF)
                    if (ch in 1..30) return ch
                }
                foundRfcommUuid = false
            } else if (elem.type == 6 || elem.type == 7) {
                val sub = findRfcommChannelInDataElement(buf, elem.payloadStart, elem.payloadEnd)
                if (sub != null) return sub
                foundRfcommUuid = false
            } else {
                foundRfcommUuid = false
            }
            pos = elem.nextPos
        }
        return null
    }

    private data class SdpDataElement(
        val type: Int,
        val payloadStart: Int,
        val payloadEnd: Int,
        val nextPos: Int
    )

    private fun readDataElement(buf: ByteArray, pos: Int, limit: Int): SdpDataElement? {
        if (pos >= limit) return null
        val header = buf[pos].toInt() and 0xFF
        val type = (header ushr 3) and 0x1F
        val sizeDesc = header and 0x07

        var cursor = pos + 1
        val payloadLen: Int = if (type == 0) {
            0
        } else {
            when (sizeDesc) {
                0 -> 1
                1 -> 2
                2 -> 4
                3 -> 8
                4 -> 16
                5 -> {
                    if (cursor >= limit) return null
                    val len = buf[cursor].toInt() and 0xFF
                    cursor += 1
                    len
                }
                6 -> {
                    if (cursor + 1 >= limit) return null
                    val len = ((buf[cursor].toInt() and 0xFF) shl 8) or (buf[cursor + 1].toInt() and 0xFF)
                    cursor += 2
                    len
                }
                7 -> {
                    if (cursor + 3 >= limit) return null
                    val len = ByteBuffer.wrap(buf, cursor, 4).order(ByteOrder.BIG_ENDIAN).int
                    cursor += 4
                    len
                }
                else -> return null
            }
        }
        if (payloadLen < 0 || cursor + payloadLen > limit) return null
        return SdpDataElement(
            type = type,
            payloadStart = cursor,
            payloadEnd = cursor + payloadLen,
            nextPos = cursor + payloadLen
        )
    }

    /**
     * Consulta SDP directo al dispositivo remoto vía socket L2CAP (PSM 0x0001) sin usar root ni `sdptool`.
     */
    fun queryRfcommChannelViaL2capSdp(mac: String, uuid: UUID, timeoutMs: Long = 2200L): Int? {
        val c = libc ?: return null
        var fd = -1
        return try {
            fd = c.socket(AF_BLUETOOTH, SOCK_SEQPACKET, BTPROTO_L2CAP)
            if (fd < 0) return null
            setNonBlocking(c, fd, true)
            val addr = buildSockAddrL2Sdp(mac)
            val rc = c.connect(fd, addr, addr.size())
            if (rc != 0) {
                val err = Native.getLastError()
                if (err != EINPROGRESS) return null
                if (!waitPoll(c, fd, POLLOUT, timeoutMs)) return null
                if (getSocketError(c, fd) != 0) return null
            }
            val req = buildSdpServiceSearchAttrRequest(uuid)
            if (!waitPoll(c, fd, POLLOUT, timeoutMs)) return null
            val sent = c.send(fd, req, req.size, 0)
            if (sent <= 0) return null

            if (!waitPoll(c, fd, POLLIN, timeoutMs)) return null
            val buf = ByteArray(1536)
            val read = c.recv(fd, buf, buf.size, 0)
            if (read <= 0) return null
            parseSdpRfcommChannelFromResponse(buf.copyOf(read))
        } catch (_: Exception) {
            null
        } finally {
            if (fd >= 0) {
                runCatching { c.shutdown(fd, SHUT_RDWR) }
                runCatching { c.close(fd) }
            }
        }
    }

    internal fun setNonBlocking(c: LibC, fd: Int, nonBlocking: Boolean) {
        val flags = c.fcntl(fd, F_GETFL, 0)
        if (flags >= 0) {
            val next = if (nonBlocking) (flags or O_NONBLOCK) else (flags and O_NONBLOCK.inv())
            c.fcntl(fd, F_SETFL, next)
        }
    }

    internal fun getSocketError(c: LibC, fd: Int): Int {
        val errRef = IntByReference(0)
        val lenRef = IntByReference(4)
        val r = c.getsockopt(fd, SOL_SOCKET, SO_ERROR, errRef, lenRef)
        return if (r == 0) errRef.value else -1
    }

    internal fun waitPoll(c: LibC, fd: Int, events: Short, timeoutMs: Long): Boolean {
        val pfd = PollFd().apply {
            this.fd = fd
            this.events = events
            this.revents = 0
        }
        val fds = arrayOf(pfd)
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(1L)
        while (true) {
            val rem = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
            val n = c.poll(fds, 1, rem)
            if (n > 0) {
                val rev = pfd.revents.toInt()
                if ((rev and (POLLERR.toInt() or POLLHUP.toInt() or POLLNVAL.toInt())) != 0) {
                    return (rev and POLLIN.toInt()) != 0
                }
                return (rev and events.toInt()) != 0
            }
            if (n == 0) return false
            val err = Native.getLastError()
            if (err != EINTR) return false
            if (System.currentTimeMillis() >= deadline) return false
        }
    }

    fun pointerNote(@Suppress("UNUSED_PARAMETER") unused: Pointer? = null) = Unit
}
