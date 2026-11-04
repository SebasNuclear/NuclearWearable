package com.nuclear.soundconnect.bluetooth

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinNT.HANDLE
import java.util.UUID

/**
 * Mapeo JNA nativo para Bluetooth Classic en Windows 10 / 11.
 * Utiliza BluetoothAPIs.dll para enumeración y ws2_32.dll (Winsock2 AF_BTH) para sockets RFCOMM.
 */
object WindowsBluetoothNative {

    const val AF_BTH = 32
    const val SOCK_STREAM = 1
    const val BTHPROTO_RFCOMM = 3
    const val SOL_SOCKET = 0xFFFF
    const val SO_RCVTIMEO = 0x1006
    const val SO_SNDTIMEO = 0x1005
    const val FIONREAD = 0x4004667F

    // UUID Oficial Sony MDR Protocol V2 (Tándem / Modernos WF-1000XM & WH-1000XM)
    val SONY_MDR_UUID: UUID = UUID.fromString("956c7b26-d49a-4ba8-b03f-b17d393cb6e2")
    // UUID Alternativo / Fallback Sony
    val SONY_FALLBACK_UUID: UUID = UUID.fromString("96cc203e-5068-46ad-b32d-e316f5e069ba")
    // UUID SPP Estándar Fallback
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    @Structure.FieldOrder(
        "dwSize", "fReturnAuthenticated", "fReturnRemembered",
        "fReturnUnknown", "fReturnConnected", "fIssueInquiry",
        "cTimeoutMultiplier", "hRadio"
    )
    open class BLUETOOTH_DEVICE_SEARCH_PARAMS : Structure() {
        @JvmField var dwSize: Int = 0
        @JvmField var fReturnAuthenticated: Int = 1
        @JvmField var fReturnRemembered: Int = 1
        @JvmField var fReturnUnknown: Int = 0
        @JvmField var fReturnConnected: Int = 1
        @JvmField var fIssueInquiry: Int = 0
        @JvmField var cTimeoutMultiplier: Byte = 0
        @JvmField var hRadio: HANDLE? = null

        init {
            dwSize = size()
        }
    }

    @Structure.FieldOrder(
        "wYear", "wMonth", "wDayOfWeek", "wDay",
        "wHour", "wMinute", "wSecond", "wMilliseconds"
    )
    open class SYSTEMTIME : Structure() {
        @JvmField var wYear: Short = 0
        @JvmField var wMonth: Short = 0
        @JvmField var wDayOfWeek: Short = 0
        @JvmField var wDay: Short = 0
        @JvmField var wHour: Short = 0
        @JvmField var wMinute: Short = 0
        @JvmField var wSecond: Short = 0
        @JvmField var wMilliseconds: Short = 0
    }

    @Structure.FieldOrder(
        "dwSize", "Address", "ulClassofDevice",
        "fConnected", "fRemembered", "fAuthenticated",
        "stLastSeen", "stLastUsed", "szName"
    )
    open class BLUETOOTH_DEVICE_INFO : Structure() {
        @JvmField var dwSize: Int = 0
        @JvmField var Address: Long = 0L
        @JvmField var ulClassofDevice: Int = 0
        @JvmField var fConnected: Int = 0
        @JvmField var fRemembered: Int = 0
        @JvmField var fAuthenticated: Int = 0
        @JvmField var stLastSeen: SYSTEMTIME = SYSTEMTIME()
        @JvmField var stLastUsed: SYSTEMTIME = SYSTEMTIME()
        @JvmField var szName: CharArray = CharArray(248)

        init {
            dwSize = size()
        }

        fun getName(): String {
            val end = szName.indexOf('\u0000')
            return if (end >= 0) String(szName, 0, end).trim() else String(szName).trim()
        }

        fun getMacAddress(): String {
            val parts = mutableListOf<String>()
            for (i in 5 downTo 0) {
                val b = ((Address ushr (i * 8)) and 0xFF).toInt()
                parts.add(String.format("%02X", b))
            }
            return parts.joinToString(":")
        }
    }

    interface BluetoothAPIsLibrary : Library {
        fun BluetoothFindFirstDevice(
            pServerParams: BLUETOOTH_DEVICE_SEARCH_PARAMS,
            pbdi: BLUETOOTH_DEVICE_INFO
        ): HANDLE?

        fun BluetoothFindNextDevice(
            hFind: HANDLE,
            pbdi: BLUETOOTH_DEVICE_INFO
        ): Boolean

        fun BluetoothFindDeviceClose(hFind: HANDLE): Boolean

        fun BluetoothGetDeviceInfo(
            hRadio: HANDLE?,
            pbdi: BLUETOOTH_DEVICE_INFO
        ): Int
    }

    val bthApi: BluetoothAPIsLibrary by lazy {
        Native.load("BluetoothAPIs", BluetoothAPIsLibrary::class.java)
    }

    @Structure.FieldOrder("data1", "data2", "data3", "data4")
    open class GUID : Structure {
        @JvmField var data1: Int = 0
        @JvmField var data2: Short = 0
        @JvmField var data3: Short = 0
        @JvmField var data4: ByteArray = ByteArray(8)

        constructor() : super(ALIGN_NONE)

        companion object {
            fun fromUUID(uuid: UUID): GUID {
                val g = GUID()
                val msb = uuid.mostSignificantBits
                val lsb = uuid.leastSignificantBits

                // Windows GUID: Data1 (DWORD, Little-Endian en memoria de x86/x64), Data2 (WORD), Data3 (WORD)
                g.data1 = (msb ushr 32).toInt()
                g.data2 = ((msb ushr 16) and 0xFFFF).toShort()
                g.data3 = (msb and 0xFFFF).toShort()

                // Data4: 8 bytes en Big-Endian (orden de red)
                for (i in 0..7) {
                    g.data4[i] = ((lsb ushr ((7 - i) * 8)) and 0xFF).toByte()
                }
                g.write()
                return g
            }
        }
    }

    /**
     * Estructura SOCKADDR_BTH de Windows SDK (ws2bth.h).
     * REQUIERE empaquetado de 1 byte estricto (#include <pshpack1.h>).
     * Tamaño total exacto: 2 + 8 + 16 + 4 = 30 bytes.
     * Con ALIGN_DEFAULT causaría WSAEADDRNOTAVAIL (10049).
     */
    @Structure.FieldOrder("addressFamily", "btAddr", "serviceClassId", "port")
    open class SOCKADDR_BTH : Structure {
        @JvmField var addressFamily: Short = AF_BTH.toShort()
        @JvmField var btAddr: Long = 0L
        @JvmField var serviceClassId: GUID = GUID()
        @JvmField var port: Int = 0

        constructor() : super(ALIGN_NONE)

        constructor(macLong: Long, uuid: UUID, portNumber: Int = 0) : super(ALIGN_NONE) {
            addressFamily = AF_BTH.toShort()
            btAddr = macLong
            serviceClassId = GUID.fromUUID(uuid)
            port = portNumber
            write()
        }
    }

    @Structure.FieldOrder(
        "wVersion", "wHighVersion", "szDescription",
        "szSystemStatus", "iMaxSockets", "iMaxUdpDg", "lpVendorInfo"
    )
    open class WSADATA : Structure() {
        @JvmField var wVersion: Short = 0
        @JvmField var wHighVersion: Short = 0
        @JvmField var szDescription: ByteArray = ByteArray(257)
        @JvmField var szSystemStatus: ByteArray = ByteArray(129)
        @JvmField var iMaxSockets: Short = 0
        @JvmField var iMaxUdpDg: Short = 0
        @JvmField var lpVendorInfo: Pointer? = null
    }

    interface WinSockLibrary : Library {
        fun WSAStartup(wVersionRequired: Short, lpWSAData: WSADATA): Int
        fun WSACleanup(): Int
        fun WSAGetLastError(): Int
        fun socket(af: Int, type: Int, protocol: Int): Long
        fun connect(s: Long, name: SOCKADDR_BTH, namelen: Int): Int
        fun send(s: Long, buf: ByteArray, len: Int, flags: Int): Int
        fun recv(s: Long, buf: ByteArray, len: Int, flags: Int): Int
        fun closesocket(s: Long): Int
        fun setsockopt(s: Long, level: Int, optname: Int, optval: Pointer, optlen: Int): Int
        fun ioctlsocket(s: Long, cmd: Int, argp: Pointer): Int
    }

    val winSock: WinSockLibrary by lazy {
        val lib = Native.load("ws2_32", WinSockLibrary::class.java)
        val wsa = WSADATA()
        lib.WSAStartup(0x0202.toShort(), wsa)
        lib
    }

    data class PairedBluetoothDevice(
        val name: String,
        val macAddress: String,
        val macLong: Long,
        val isConnected: Boolean,
        val isSony: Boolean = isSonyDeviceName(name)
    )

    fun isSonyDeviceName(name: String?): Boolean {
        val n = (name ?: "").uppercase().trim()
        if (n.isBlank()) return false
        return n.contains("SONY") ||
               n.startsWith("WF-") ||
               n.startsWith("WH-") ||
               n.startsWith("WI-") ||
               n.startsWith("MDR-") ||
               n.contains("1000XM") ||
               n.contains("LINKBUDS") ||
               n.contains("ULT WEAR") ||
               n.contains("INZONE")
    }

    /**
     * Enumera todos los dispositivos Bluetooth emparejados en Windows.
     */
    fun getPairedDevices(): List<PairedBluetoothDevice> {
        val list = mutableListOf<PairedBluetoothDevice>()
        val search = BLUETOOTH_DEVICE_SEARCH_PARAMS()
        search.fReturnAuthenticated = 1
        search.fReturnRemembered = 1
        search.fReturnConnected = 1
        search.fReturnUnknown = 0
        search.fIssueInquiry = 0

        val info = BLUETOOTH_DEVICE_INFO()
        val hFind = bthApi.BluetoothFindFirstDevice(search, info) ?: return list

        try {
            do {
                val name = info.getName()
                val mac = info.getMacAddress()
                val connected = info.fConnected != 0
                if (name.isNotBlank()) {
                    list.add(PairedBluetoothDevice(name, mac, info.Address, connected, isSonyDeviceName(name)))
                }
                info.dwSize = info.size()
            } while (bthApi.BluetoothFindNextDevice(hFind, info))
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            bthApi.BluetoothFindDeviceClose(hFind)
        }

        return list
    }

    fun macStringToLong(mac: String): Long {
        val clean = mac.replace(":", "").replace("-", "").trim()
        return clean.toLong(16)
    }

    /**
     * Consulta el estado de conexión del dispositivo a nivel de kernel de Windows (< 1ms).
     * Evita aperturas de sockets RFCOMM o esperas de timeout si el dispositivo está apagado o en el estuche.
     */
    fun isDeviceConnected(macAddress: String): Boolean {
        if (macAddress.isBlank()) return false
        return try {
            val macLong = macStringToLong(macAddress)
            val info = BLUETOOTH_DEVICE_INFO()
            info.Address = macLong
            info.dwSize = info.size()
            val res = bthApi.BluetoothGetDeviceInfo(null, info)
            if (res == 0) {
                info.fConnected != 0
            } else {
                getPairedDevices().firstOrNull { it.macAddress.equals(macAddress, ignoreCase = true) }?.isConnected == true
            }
        } catch (_: Throwable) {
            false
        }
    }
}
