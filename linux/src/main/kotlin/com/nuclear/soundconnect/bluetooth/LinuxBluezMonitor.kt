package com.nuclear.soundconnect.bluetooth

import com.nuclear.soundconnect.util.CommandRunner
import com.nuclear.soundconnect.util.SystemCommandRunner
import java.util.UUID

data class BluezSnapshot(
    val isAdapterPowered: Boolean = false,
    val devices: List<SonyBluetoothDevice> = emptyList(),
    val deviceObjectPaths: Map<String, String> = emptyMap()
)

/**
 * Descubrimiento e inspección rootless de dispositivos BlueZ en D-Bus de sistema (`org.bluez`)
 * y resolución de canal RFCOMM SDP (`sdptool` + fallback `bluetoothctl`).
 */
object LinuxBluezMonitor {

    private val MAC_REGEX = Regex("""([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}""")
    private val UUID_REGEX = Regex("""[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}""")

    fun querySnapshot(runner: CommandRunner = SystemCommandRunner): BluezSnapshot {
        // 1. Vía estándar GNOME/freedesktop sin root: gdbus GetManagedObjects
        if (runner.commandExists("gdbus")) {
            val res = runner.run(
                listOf(
                    "gdbus", "call", "--system",
                    "--dest", "org.bluez",
                    "--object-path", "/",
                    "--method", "org.freedesktop.DBus.ObjectManager.GetManagedObjects"
                ),
                timeoutMs = 3500L
            )
            if (res.isSuccess && res.stdout.isNotBlank()) {
                val parsed = parseGdbusManagedObjects(res.stdout)
                if (parsed.devices.isNotEmpty() || parsed.isAdapterPowered) {
                    return parsed
                }
            }
        }

        // 2. Fallback systemd: bluetoothctl devices + info + adapter show
        if (runner.commandExists("bluetoothctl")) {
            return queryViaBluetoothctl(runner)
        }

        return BluezSnapshot()
    }

    /**
     * Parsea la salida GVariant de `gdbus call ... org.freedesktop.DBus.ObjectManager.GetManagedObjects`.
     */
    fun parseGdbusManagedObjects(raw: String): BluezSnapshot {
        var adapterPowered = false
        val devices = mutableListOf<SonyBluetoothDevice>()
        val pathMap = mutableMapOf<String, String>()

        // Localizar cada entrada de objectpath '/org/bluez/...'
        val objPattern = Regex("""objectpath\s+'(/org/bluez/[^']*)'""")
        val matches = objPattern.findAll(raw).toList()

        for (idx in matches.indices) {
            val match = matches[idx]
            val objPath = match.groupValues[1]
            val blockStart = match.range.last + 1
            val blockEnd = if (idx + 1 < matches.size) matches[idx + 1].range.first else raw.length
            val block = raw.substring(blockStart, blockEnd)

            val adapterBlock = extractInterfaceBlock(block, "org.bluez.Adapter1")
            if (adapterBlock != null) {
                val powered = extractBooleanProperty(adapterBlock, "Powered")
                if (powered == true) {
                    adapterPowered = true
                }
            }

            val devBlock = extractInterfaceBlock(block, "org.bluez.Device1")
            if (devBlock != null) {
                val mac = extractStringProperty(devBlock, "Address")
                    ?: MAC_REGEX.find(objPath.replace("_", ":"))?.value
                if (!mac.isNullOrBlank()) {
                    val name = extractStringProperty(devBlock, "Name")
                        ?: extractStringProperty(devBlock, "Alias")
                    val paired = extractBooleanProperty(devBlock, "Paired") ?: false
                    val connected = extractBooleanProperty(devBlock, "Connected") ?: false
                    val uuids = extractUuidListProperty(devBlock, "UUIDs")

                    val batBlock = extractInterfaceBlock(block, "org.bluez.Battery1")
                    val batteryPct = if (batBlock != null) extractIntOrByteProperty(batBlock, "Percentage") else null

                    if (paired || connected) {
                        val normalizedMac = mac.uppercase()
                        devices += SonyBluetoothDevice(
                            address = normalizedMac,
                            name = name,
                            uuids = uuids,
                            isConnected = connected,
                            osBatteryPercent = batteryPct?.coerceIn(0, 100)
                        )
                        pathMap[normalizedMac] = objPath
                    }
                }
            }
        }

        // Si un adaptador tenía dispositivos conectados o listados pero no trajo Powered, revisar flag heurístico
        if (!adapterPowered && devices.any { it.isConnected }) {
            adapterPowered = true
        }

        return BluezSnapshot(
            isAdapterPowered = adapterPowered,
            devices = devices,
            deviceObjectPaths = pathMap
        )
    }

    private fun queryViaBluetoothctl(runner: CommandRunner): BluezSnapshot {
        val showRes = runner.run(listOf("bluetoothctl", "show"), timeoutMs = 2000L)
        val powered = showRes.stdout.contains("Powered: yes", ignoreCase = true)
        if (!powered && showRes.stdout.contains("No default controller", ignoreCase = true)) {
            return BluezSnapshot(isAdapterPowered = false)
        }

        val devListRes = runner.run(listOf("bluetoothctl", "devices", "Paired"), timeoutMs = 2000L)
            .let { if (it.isSuccess && it.stdout.isNotBlank()) it else runner.run(listOf("bluetoothctl", "devices"), timeoutMs = 2000L) }

        val lineRegex = Regex("""Device\s+([0-9A-Fa-f:]{17})\s*(.*)""")
        val devices = mutableListOf<SonyBluetoothDevice>()
        val pathMap = mutableMapOf<String, String>()

        for (line in devListRes.stdout.lines()) {
            val m = lineRegex.find(line) ?: continue
            val mac = m.groupValues[1].uppercase()
            val fallbackName = m.groupValues[2].trim().takeIf { it.isNotBlank() }

            val infoRes = runner.run(listOf("bluetoothctl", "info", mac), timeoutMs = 2000L)
            val info = infoRes.stdout
            val paired = info.contains("Paired: yes", ignoreCase = true) || devListRes.stdout.isNotBlank()
            if (!paired) continue

            val connected = info.contains("Connected: yes", ignoreCase = true)
            val nameLine = Regex("""^\s*(?:Name|Alias):\s*(.+)$""", RegexOption.MULTILINE).find(info)
            val name = nameLine?.groupValues?.get(1)?.trim() ?: fallbackName

            val uuids = UUID_REGEX.findAll(info).mapNotNull {
                runCatching { UUID.fromString(it.value.lowercase()) }.getOrNull()
            }.distinct().toList()

            val batMatch = Regex("""Battery Percentage:\s*(?:0x([0-9A-Fa-f]+)\s*)?\(?(\d{1,3})?\)?""").find(info)
            val batteryPct = batMatch?.let { bm ->
                bm.groupValues[2].toIntOrNull()
                    ?: bm.groupValues[1].toIntOrNull(16)
            }?.coerceIn(0, 100)

            devices += SonyBluetoothDevice(
                address = mac,
                name = name,
                uuids = uuids,
                isConnected = connected,
                osBatteryPercent = batteryPct
            )
            pathMap[mac] = "/org/bluez/hci0/dev_${mac.replace(':', '_')}"
        }

        return BluezSnapshot(
            isAdapterPowered = powered || devices.any { it.isConnected },
            devices = devices,
            deviceObjectPaths = pathMap
        )
    }

    /**
     * Solicita conexión o desconexión de perfil Bluetooth al daemon `org.bluez` sin privilegios root.
     */
    fun triggerConnectDevice(mac: String, runner: CommandRunner = SystemCommandRunner): Boolean {
        val snap = querySnapshot(runner)
        val path = snap.deviceObjectPaths[mac.uppercase()] ?: "/org/bluez/hci0/dev_${mac.uppercase().replace(':', '_')}"
        if (runner.commandExists("gdbus")) {
            val res = runner.run(
                listOf(
                    "gdbus", "call", "--system",
                    "--dest", "org.bluez",
                    "--object-path", path,
                    "--method", "org.bluez.Device1.Connect"
                ),
                timeoutMs = 6000L
            )
            if (res.isSuccess) return true
        }
        if (runner.commandExists("bluetoothctl")) {
            val res = runner.run(listOf("bluetoothctl", "connect", mac), timeoutMs = 6000L)
            return res.isSuccess
        }
        return false
    }

    /**
     * Resuelve el canal RFCOMM para un UUID Sony dado intentando:
     * 1. Consulta L2CAP SDP nativa vía JNA (`BTPROTO_L2CAP`, PSM `0x0001`).
     * 2. Fallback CLI `sdptool browse <MAC>` / `sdptool records <MAC>`.
     */
    fun resolveRfcommChannel(
        mac: String,
        uuid: UUID,
        runner: CommandRunner = SystemCommandRunner
    ): Int? {
        // 1. Nativo L2CAP SDP (sin dependencias externas ni bluetoothd --compat)
        val l2capChannel = LinuxBluetoothNative.queryRfcommChannelViaL2capSdp(mac, uuid)
        if (l2capChannel != null) return l2capChannel

        // 2. Fallback CLI sdptool
        if (runner.commandExists("sdptool")) {
            val res = runner.run(listOf("sdptool", "browse", mac), timeoutMs = 3500L)
            if (res.isSuccess && res.stdout.isNotBlank()) {
                parseSdptoolOutputForUuid(res.stdout, uuid)?.let { return it }
            }
            val recRes = runner.run(listOf("sdptool", "records", mac), timeoutMs = 3500L)
            if (recRes.isSuccess && recRes.stdout.isNotBlank()) {
                parseSdptoolOutputForUuid(recRes.stdout, uuid)?.let { return it }
            }
        }
        return null
    }

    /**
     * Parsea salidas de `sdptool browse <MAC>` / `sdptool records <MAC>` buscando el registro que contiene
     * el UUID solicitado (formato completo 128-bit o short UUID `1101` para SPP) y devuelve su `Channel: N`.
     */
    fun parseSdptoolOutputForUuid(output: String, uuid: UUID): Int? {
        val targetFull = uuid.toString().lowercase()
        val targetShort = if (targetFull.endsWith("-0000-1000-8000-00805f9b34fb") && targetFull.startsWith("0000")) {
            targetFull.substring(4, 8).lowercase()
        } else {
            null
        }

        // Dividir por bloques de servicio ("Service Name:" o "Service RecHandle:")
        val sections = output.split(Regex("""(?m)(?=^(?:Service Name:|Service RecHandle:))"""))
        val channelRegex = Regex("""Channel:\s*(\d{1,2})""")

        for (section in sections) {
            val lower = section.lowercase()
            val matchesUuid = lower.contains(targetFull) ||
                (targetShort != null && Regex("""\b0x0*$targetShort\b|\($targetShort\)|\b$targetShort\b""").containsMatchIn(lower))
            if (!matchesUuid) continue

            val chMatch = channelRegex.find(section)
            val ch = chMatch?.groupValues?.get(1)?.toIntOrNull()
            if (ch != null && ch in 1..30) return ch
        }
        return null
    }

    private fun extractInterfaceBlock(objectBlock: String, interfaceName: String): String? {
        val marker = "'$interfaceName':"
        val idx = objectBlock.indexOf(marker)
        if (idx < 0) return null
        val braceStart = objectBlock.indexOf('{', idx + marker.length)
        if (braceStart < 0) return null
        return extractBalancedBraces(objectBlock, braceStart)
    }

    private fun extractBalancedBraces(text: String, openIndex: Int): String? {
        var depth = 0
        var inSingleQuote = false
        var escaped = false
        for (i in openIndex until text.length) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (ch == '\'') {
                inSingleQuote = !inSingleQuote
                continue
            }
            if (inSingleQuote) continue
            if (ch == '{') {
                depth++
            } else if (ch == '}') {
                depth--
                if (depth == 0) {
                    return text.substring(openIndex, i + 1)
                }
            }
        }
        return null
    }

    private fun extractStringProperty(ifaceBlock: String, propName: String): String? {
        val regex = Regex("""'$propName':\s*<'((?:[^'\\]|\\.)*)'>""")
        val m = regex.find(ifaceBlock) ?: return null
        return m.groupValues[1].replace("\\'", "'").replace("\\\\", "\\")
    }

    private fun extractBooleanProperty(ifaceBlock: String, propName: String): Boolean? {
        val regex = Regex("""'$propName':\s*<(?:boolean\s+)?(true|false)>""")
        val m = regex.find(ifaceBlock) ?: return null
        return m.groupValues[1] == "true"
    }

    private fun extractIntOrByteProperty(ifaceBlock: String, propName: String): Int? {
        val regex = Regex("""'$propName':\s*<(?:(?:byte|uint\d*|int\d*)\s+)?(0x[0-9A-Fa-f]+|\d+)>""")
        val m = regex.find(ifaceBlock) ?: return null
        val token = m.groupValues[1]
        return if (token.startsWith("0x", ignoreCase = true)) {
            token.substring(2).toIntOrNull(16)
        } else {
            token.toIntOrNull()
        }
    }

    private fun extractUuidListProperty(ifaceBlock: String, propName: String): List<UUID> {
        val regex = Regex("""'$propName':\s*<([^>]*)>""")
        val m = regex.find(ifaceBlock) ?: return emptyList()
        return UUID_REGEX.findAll(m.groupValues[1]).mapNotNull {
            runCatching { UUID.fromString(it.value.lowercase()) }.getOrNull()
        }.distinct().toList()
    }
}
