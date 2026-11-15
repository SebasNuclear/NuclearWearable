package com.nuclear.soundconnect.audio

import com.nuclear.soundconnect.util.CommandRunner
import com.nuclear.soundconnect.util.SystemCommandRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot en vivo del códec Bluetooth y estado de perfil de audio en Linux (PipeWire / PulseAudio).
 */
data class LiveCodecHudInfo(
    val codecName: String = "",
    val sampleRateHz: Int = 0,
    val bitsPerSample: Int = 0,
    val channelCount: Int = 0,
    val isA2dpPlaying: Boolean = false,
    val isHfpCallActive: Boolean = false,
    val isLeAudioActive: Boolean = false,
    val isAudioRoutedToDevice: Boolean = false,
    val activeProfile: String = "",
    val backendName: String = "",
    val lastUpdated: Long = 0L
) {
    fun formatShortBadge(): String {
        val parts = mutableListOf<String>()
        if (codecName.isNotBlank()) parts.add(codecName)
        if (sampleRateHz > 0) {
            val khz = if (sampleRateHz % 1000 == 0) {
                "${sampleRateHz / 1000} kHz"
            } else {
                String.format(java.util.Locale.US, "%.1f kHz", sampleRateHz / 1000.0)
            }
            parts.add(khz)
        }
        if (bitsPerSample > 0) parts.add("$bitsPerSample-bit")
        if (isHfpCallActive) parts.add("HFP SCO")
        return parts.joinToString(" • ")
    }
}

data class LinuxAudioMediaState(
    val hud: LiveCodecHudInfo = LiveCodecHudInfo(),
    val isMediaPlaying: Boolean = false,
    val isCallActive: Boolean = false
)

/**
 * Monitor de códec en vivo y estado multimedia/HFP para Linux Desktop (PipeWire `pw-dump` + PulseAudio `pactl` + MPRIS D-Bus).
 */
class LinuxAudioCodecMonitor(
    private val commandRunner: CommandRunner = SystemCommandRunner
) {

    private val _hudInfo = MutableStateFlow(LiveCodecHudInfo())
    val hudInfo: StateFlow<LiveCodecHudInfo> = _hudInfo.asStateFlow()

    fun refreshNow(targetMac: String, sonyReportedCodec: String = ""): LiveCodecHudInfo {
        val mediaState = inspectSystemAudio(targetMac, sonyReportedCodec, checkMpris = false)
        _hudInfo.value = mediaState.hud
        return mediaState.hud
    }

    fun inspectSystemAudio(
        targetMac: String,
        sonyReportedCodec: String = "",
        checkMpris: Boolean = true
    ): LinuxAudioMediaState {
        var parsed: LiveCodecHudInfo? = null
        var anyBtSinkRunning = false
        var micSourceOutputActive = false

        // 1. PipeWire nativo (pw-dump JSON): máxima precisión en codec, sample rate y formato
        if (commandRunner.commandExists("pw-dump")) {
            val res = commandRunner.run(listOf("pw-dump"), timeoutMs = 2500L)
            if (res.isSuccess && res.stdout.isNotBlank()) {
                val pw = parsePwDump(res.stdout, targetMac)
                if (pw != null) {
                    parsed = pw.hud
                    anyBtSinkRunning = pw.isMediaPlaying
                    micSourceOutputActive = pw.isCallActive
                }
            }
        }

        // 2. Fallback / Enriquecimiento con PulseAudio (`pactl list cards/sinks/source-outputs`, funciona también con pipewire-pulse)
        if ((parsed == null || parsed.codecName.isBlank() || parsed.sampleRateHz == 0) &&
            commandRunner.commandExists("pactl")
        ) {
            val cardsOut = commandRunner.run(listOf("pactl", "list", "cards"), timeoutMs = 2000L).stdout
            val sinksOut = commandRunner.run(listOf("pactl", "list", "sinks"), timeoutMs = 2000L).stdout
            val srcOuts = commandRunner.run(listOf("pactl", "list", "source-outputs"), timeoutMs = 1500L).stdout
            val pa = parsePactlOutputs(cardsOut, sinksOut, srcOuts, targetMac)
            if (pa != null) {
                parsed = mergeHud(parsed, pa.hud)
                anyBtSinkRunning = anyBtSinkRunning || pa.isMediaPlaying
                micSourceOutputActive = micSourceOutputActive || pa.isCallActive
            }
        }

        var hud = (parsed ?: LiveCodecHudInfo()).let { base ->
            val effectiveCodec = base.codecName.ifBlank { normalizeCodecName(sonyReportedCodec) }
            base.copy(
                codecName = effectiveCodec,
                lastUpdated = System.currentTimeMillis()
            )
        }

        // Detección complementaria de reproducción multimedia vía MPRIS D-Bus de sesión (100% offline)
        val mprisPlaying = if (checkMpris && !anyBtSinkRunning) {
            isMprisPlaybackPlaying(commandRunner)
        } else {
            false
        }

        val mediaPlaying = hud.isA2dpPlaying || anyBtSinkRunning || mprisPlaying
        val callActive = hud.isHfpCallActive || micSourceOutputActive
        hud = hud.copy(
            isA2dpPlaying = mediaPlaying && !callActive,
            isHfpCallActive = callActive
        )

        _hudInfo.value = hud
        return LinuxAudioMediaState(
            hud = hud,
            isMediaPlaying = mediaPlaying,
            isCallActive = callActive
        )
    }

    companion object {
        fun normalizeMacUnderscore(mac: String): String =
            mac.trim().uppercase().replace(":", "_")

        fun normalizeCodecName(raw: String): String {
            val clean = raw.trim().lowercase().replace('-', '_')
            if (clean.isBlank()) return ""
            return when {
                clean.contains("ldac") -> "LDAC"
                clean.contains("aptx_hd") || clean.contains("aptxhd") -> "aptX HD"
                clean.contains("aptx_adaptive") -> "aptX Adaptive"
                clean.contains("aptx_ll") -> "aptX LL"
                clean.contains("aptx") -> "aptX"
                clean.contains("lc3") -> "LC3 (LE Audio)"
                clean.contains("msbc") -> "mSBC (HFP)"
                clean.contains("cvsd") -> "CVSD (HFP)"
                clean.contains("sbc_xq") -> "SBC-XQ"
                clean.contains("sbc") -> "SBC"
                clean.contains("aac") -> "AAC"
                else -> raw.trim().uppercase()
            }
        }

        fun bitsFromSampleFormat(format: String): Int {
            val f = format.trim().lowercase()
            return when {
                f.contains("32") && f.contains("24") -> 24 // s24_32le -> 24 bits efectivos
                f.contains("f32") || f.contains("s32") -> 32
                f.contains("s24") || f.contains("24le") || f.contains("24be") -> 24
                f.contains("s16") || f.contains("16le") || f.contains("16be") -> 16
                else -> 0
            }
        }

        fun parsePwDump(jsonText: String, targetMac: String): LinuxAudioMediaState? {
            val arr = runCatching { JSONArray(jsonText.trim()) }.getOrNull() ?: return null
            val targetClean = targetMac.trim().uppercase()
            val targetUnder = normalizeMacUnderscore(targetMac)

            var bestCodec = ""
            var bestProfile = ""
            var bestRate = 0
            var bestBits = 0
            var bestChannels = 0
            var sinkRunning = false
            var hfpActive = false
            var routedToTarget = false

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val type = obj.optString("type", "")
                val info = obj.optJSONObject("info") ?: continue
                val props = info.optJSONObject("props") ?: JSONObject()

                val devApi = props.optString("device.api", "")
                val addr = props.optString("api.bluez5.address", "")
                    .ifBlank { props.optString("device.string", "") }
                val nodeName = props.optString("node.name", "")
                    .ifBlank { props.optString("device.name", "") }

                val isBluez = devApi.equals("bluez5", ignoreCase = true) ||
                    nodeName.contains("bluez", ignoreCase = true)
                if (!isBluez) continue

                val matchesTarget = targetClean.isBlank() ||
                    addr.equals(targetClean, ignoreCase = true) ||
                    nodeName.contains(targetUnder, ignoreCase = true)
                if (!matchesTarget) continue

                routedToTarget = true

                val codecProp = props.optString("api.bluez5.codec", "")
                if (codecProp.isNotBlank()) bestCodec = normalizeCodecName(codecProp)

                val profileProp = props.optString("api.bluez5.profile", "")
                if (profileProp.isNotBlank()) bestProfile = profileProp

                if (type == "PipeWire:Interface:Device") {
                    val params = info.optJSONObject("params")
                    val profiles = params?.optJSONArray("Profile")
                    if (profiles != null && profiles.length() > 0) {
                        val activeProfName = profiles.optJSONObject(0)?.optString("name", "") ?: ""
                        if (activeProfName.isNotBlank()) bestProfile = activeProfName
                    }
                }

                if (type == "PipeWire:Interface:Node") {
                    val mediaClass = props.optString("media.class", "")
                    val state = info.optString("state", "")
                    val rate = props.optInt("audio.rate", 0)
                    val ch = props.optInt("audio.channels", 0)
                    val fmt = props.optString("audio.format", "")

                    if (rate > 0 && (bestRate == 0 || mediaClass.contains("Sink") || mediaClass.contains("Duplex"))) {
                        bestRate = rate
                    }
                    if (ch > 0 && bestChannels == 0) bestChannels = ch
                    if (fmt.isNotBlank() && bestBits == 0) bestBits = bitsFromSampleFormat(fmt)

                    val params = info.optJSONObject("params")
                    val fmtArr = params?.optJSONArray("Format")
                    if (fmtArr != null && fmtArr.length() > 0) {
                        val f0 = fmtArr.optJSONObject(0)
                        if (bestRate == 0) bestRate = f0?.optInt("rate", 0) ?: 0
                        if (bestChannels == 0) bestChannels = f0?.optInt("channels", 0) ?: 0
                        if (bestBits == 0) {
                            bestBits = bitsFromSampleFormat(f0?.optString("format", "") ?: "")
                        }
                    }

                    if (state.equals("running", ignoreCase = true)) {
                        if (mediaClass.contains("Sink", ignoreCase = true) ||
                            mediaClass.contains("Duplex", ignoreCase = true)
                        ) {
                            sinkRunning = true
                        }
                        if (mediaClass.contains("Source", ignoreCase = true) &&
                            (nodeName.contains("headset", ignoreCase = true) ||
                                bestProfile.contains("headset", ignoreCase = true) ||
                                bestProfile.contains("hfp", ignoreCase = true))
                        ) {
                            hfpActive = true
                        }
                    }
                }
            }

            if (!routedToTarget) return null

            val profLower = bestProfile.lowercase()
            val isHfp = hfpActive ||
                profLower.contains("headset") ||
                profLower.contains("hfp") ||
                profLower.contains("hsp") ||
                bestCodec.contains("HFP", ignoreCase = true)

            if (bestCodec.isBlank() && profLower.contains("ldac")) bestCodec = "LDAC"
            if (bestCodec.isBlank() && profLower.contains("aac")) bestCodec = "AAC"
            if (bestCodec.isBlank() && profLower.contains("sbc_xq")) bestCodec = "SBC-XQ"
            if (bestCodec.isBlank() && profLower.contains("sbc")) bestCodec = "SBC"
            if (bestCodec.isBlank() && profLower.contains("aptx_hd")) bestCodec = "aptX HD"
            if (bestCodec.isBlank() && profLower.contains("aptx")) bestCodec = "aptX"
            if (bestCodec.isBlank() && (profLower.contains("lc3") || profLower.contains("bap"))) bestCodec = "LC3 (LE Audio)"

            val isLe = profLower.contains("bap") || bestCodec.startsWith("LC3")

            val hud = LiveCodecHudInfo(
                codecName = bestCodec,
                sampleRateHz = bestRate,
                bitsPerSample = bestBits,
                channelCount = bestChannels,
                isA2dpPlaying = sinkRunning && !isHfp,
                isHfpCallActive = isHfp,
                isLeAudioActive = isLe,
                isAudioRoutedToDevice = routedToTarget,
                activeProfile = bestProfile,
                backendName = "PipeWire"
            )
            return LinuxAudioMediaState(
                hud = hud,
                isMediaPlaying = sinkRunning,
                isCallActive = isHfp
            )
        }

        fun parsePactlOutputs(
            cardsOut: String,
            sinksOut: String,
            sourceOutputsOut: String,
            targetMac: String
        ): LinuxAudioMediaState? {
            val targetClean = targetMac.trim().uppercase()
            val targetUnder = normalizeMacUnderscore(targetMac)

            var found = false
            var codec = ""
            var activeProfile = ""
            var rate = 0
            var bits = 0
            var channels = 0
            var sinkRunning = false

            // Cards: buscar bluez_card.<MAC>
            val cardBlocks = cardsOut.split(Regex("""(?m)^Card #\d+\s*$"""))
            for (block in cardBlocks) {
                if (!block.contains("bluez", ignoreCase = true)) continue
                val matchesTarget = targetClean.isBlank() ||
                    block.contains(targetClean, ignoreCase = true) ||
                    block.contains(targetUnder, ignoreCase = true)
                if (!matchesTarget) continue

                found = true
                Regex("""Active Profile:\s*(.+)""").find(block)?.let {
                    activeProfile = it.groupValues[1].trim()
                }
                val codecMatch = Regex("""(?:api\.bluez5\.codec|bluetooth\.codec)\s*=\s*"([^"]+)"""")
                    .find(block)
                if (codecMatch != null) {
                    codec = normalizeCodecName(codecMatch.groupValues[1])
                } else if (activeProfile.isNotBlank()) {
                    val pLow = activeProfile.lowercase().replace('-', '_')
                    codec = when {
                        pLow.contains("ldac") -> "LDAC"
                        pLow.contains("aptx_hd") -> "aptX HD"
                        pLow.contains("aptx") -> "aptX"
                        pLow.contains("msbc") -> "mSBC (HFP)"
                        pLow.contains("cvsd") -> "CVSD (HFP)"
                        pLow.contains("sbc_xq") -> "SBC-XQ"
                        pLow.contains("aac") -> "AAC"
                        pLow.contains("sbc") -> "SBC"
                        else -> ""
                    }
                }
            }

            // Sinks: buscar bluez_output.<MAC> / bluez_sink.<MAC>
            val sinkBlocks = sinksOut.split(Regex("""(?m)^Sink #\d+\s*$"""))
            for (block in sinkBlocks) {
                if (!block.contains("bluez", ignoreCase = true)) continue
                val matchesTarget = targetClean.isBlank() ||
                    block.contains(targetClean, ignoreCase = true) ||
                    block.contains(targetUnder, ignoreCase = true)
                if (!matchesTarget) continue

                found = true
                val stateMatch = Regex("""State:\s*(\S+)""").find(block)
                if (stateMatch?.groupValues?.get(1).equals("RUNNING", ignoreCase = true)) {
                    sinkRunning = true
                }
                val specMatch = Regex("""Sample Specification:\s*(\S+)\s+(\d+)ch\s+(\d+)Hz""")
                    .find(block)
                if (specMatch != null) {
                    bits = bitsFromSampleFormat(specMatch.groupValues[1])
                    channels = specMatch.groupValues[2].toIntOrNull() ?: 0
                    rate = specMatch.groupValues[3].toIntOrNull() ?: 0
                }
                if (codec.isBlank()) {
                    Regex("""(?:api\.bluez5\.codec|bluetooth\.codec)\s*=\s*"([^"]+)"""")
                        .find(block)?.let { codec = normalizeCodecName(it.groupValues[1]) }
                }
                if (activeProfile.isBlank()) {
                    Regex("""api\.bluez5\.profile\s*=\s*"([^"]+)"""")
                        .find(block)?.let { activeProfile = it.groupValues[1].trim() }
                }
            }

            // Source outputs activos apuntando a microfono BT -> llamada / comunicacion activa
            val hasBtMicStream = sourceOutputsOut.contains("bluez_", ignoreCase = true) &&
                (targetClean.isBlank() ||
                    sourceOutputsOut.contains(targetUnder, ignoreCase = true) ||
                    sourceOutputsOut.contains("headset", ignoreCase = true))

            if (!found) return null

            val profLow = activeProfile.lowercase()
            val isHfp = hasBtMicStream ||
                profLow.contains("headset") ||
                profLow.contains("hfp") ||
                profLow.contains("hsp") ||
                codec.contains("HFP", ignoreCase = true)

            val hud = LiveCodecHudInfo(
                codecName = codec,
                sampleRateHz = rate,
                bitsPerSample = bits,
                channelCount = channels,
                isA2dpPlaying = sinkRunning && !isHfp,
                isHfpCallActive = isHfp,
                isLeAudioActive = profLow.contains("bap") || codec.startsWith("LC3"),
                isAudioRoutedToDevice = true,
                activeProfile = activeProfile,
                backendName = "PulseAudio"
            )
            return LinuxAudioMediaState(
                hud = hud,
                isMediaPlaying = sinkRunning,
                isCallActive = isHfp
            )
        }

        fun isMprisPlaybackPlaying(runner: CommandRunner): Boolean {
            if (runner.commandExists("playerctl")) {
                val res = runner.run(listOf("playerctl", "-a", "status"), timeoutMs = 900L)
                if (res.isSuccess && res.stdout.lines().any { it.trim().equals("Playing", ignoreCase = true) }) {
                    return true
                }
            }
            if (runner.commandExists("gdbus")) {
                val namesRes = runner.run(
                    listOf(
                        "gdbus", "call", "--session",
                        "--dest", "org.freedesktop.DBus",
                        "--object-path", "/org/freedesktop/DBus",
                        "--method", "org.freedesktop.DBus.ListNames"
                    ),
                    timeoutMs = 1000L
                )
                if (namesRes.isSuccess) {
                    val mprisServices = Regex("""'?(org\.mpris\.MediaPlayer2\.[^',)\s]+)'?""")
                        .findAll(namesRes.stdout)
                        .map { it.groupValues[1] }
                        .distinct()
                        .take(6)
                        .toList()
                    for (service in mprisServices) {
                        val st = runner.run(
                            listOf(
                                "gdbus", "call", "--session",
                                "--dest", service,
                                "--object-path", "/org/mpris/MediaPlayer2",
                                "--method", "org.freedesktop.DBus.Properties.Get",
                                "org.mpris.MediaPlayer2.Player", "PlaybackStatus"
                            ),
                            timeoutMs = 800L
                        )
                        if (st.isSuccess && st.stdout.contains("'Playing'", ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        private fun mergeHud(primary: LiveCodecHudInfo?, fallback: LiveCodecHudInfo): LiveCodecHudInfo {
            if (primary == null) return fallback
            return primary.copy(
                codecName = primary.codecName.ifBlank { fallback.codecName },
                sampleRateHz = if (primary.sampleRateHz > 0) primary.sampleRateHz else fallback.sampleRateHz,
                bitsPerSample = if (primary.bitsPerSample > 0) primary.bitsPerSample else fallback.bitsPerSample,
                channelCount = if (primary.channelCount > 0) primary.channelCount else fallback.channelCount,
                isA2dpPlaying = primary.isA2dpPlaying || fallback.isA2dpPlaying,
                isHfpCallActive = primary.isHfpCallActive || fallback.isHfpCallActive,
                isLeAudioActive = primary.isLeAudioActive || fallback.isLeAudioActive,
                isAudioRoutedToDevice = primary.isAudioRoutedToDevice || fallback.isAudioRoutedToDevice,
                activeProfile = primary.activeProfile.ifBlank { fallback.activeProfile },
                backendName = primary.backendName.ifBlank { fallback.backendName }
            )
        }
    }
}
