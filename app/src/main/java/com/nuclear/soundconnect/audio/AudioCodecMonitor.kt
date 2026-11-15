package com.nuclear.soundconnect.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.nuclear.soundconnect.bluetooth.HeadsetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class LiveCodecHudInfo(
    val codecName: String = "",
    val sampleRateHz: Int = 0,
    val bitsPerSample: Int = 0,
    val channelCount: Int = 2,
    val isA2dpPlaying: Boolean = false,
    val isHfpCallActive: Boolean = false,
    val isLeAudioActive: Boolean = false,
    val isAudioRoutedToDevice: Boolean = false,
    val lastUpdated: Long = 0L
) {
    fun formatShortBadge(): String {
        val parts = mutableListOf<String>()
        if (codecName.isNotBlank()) parts.add(codecName)
        if (sampleRateHz > 0) {
            val khz = if (sampleRateHz % 1000 == 0) {
                "${sampleRateHz / 1000} kHz"
            } else {
                String.format(Locale.US, "%.1f kHz", sampleRateHz / 1000.0)
            }
            parts.add(khz)
        }
        if (bitsPerSample > 0) {
            parts.add("${bitsPerSample}-bit")
        }
        return parts.joinToString(" · ")
    }
}

/**
 * Monitor local y sin permisos extra para el HUD de audio/códec en tiempo real (Fase 3.2).
 * Combina telemetría del chip Sony (codecName vía RFCOMM), AudioDeviceInfo/AudioProfile
 * del sistema y estado HFP/SCO de AudioManager.
 */
class AudioCodecMonitor(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private val _hudInfo = MutableStateFlow(LiveCodecHudInfo())
    val hudInfo: StateFlow<LiveCodecHudInfo> = _hudInfo.asStateFlow()

    @Volatile
    private var a2dpProxy: BluetoothA2dp? = null

    @Volatile
    private var lastHeadsetState: HeadsetState = HeadsetState()

    private val started = AtomicBoolean(false)

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.A2DP && proxy is BluetoothA2dp) {
                a2dpProxy = proxy
                refresh(lastHeadsetState)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = null
                refresh(lastHeadsetState)
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refresh(lastHeadsetState)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refresh(lastHeadsetState)
        }
    }

    private val modeChangedListener = AudioManager.OnModeChangedListener {
        refresh(lastHeadsetState)
    }

    private val commDeviceListener = AudioManager.OnCommunicationDeviceChangedListener {
        refresh(lastHeadsetState)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        runCatching {
            audioManager?.registerAudioDeviceCallback(deviceCallback, mainHandler)
            audioManager?.addOnModeChangedListener(mainExecutor, modeChangedListener)
            audioManager?.addOnCommunicationDeviceChangedListener(mainExecutor, commDeviceListener)
        }
        runCatching {
            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        }
        refresh(lastHeadsetState)
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching {
            audioManager?.unregisterAudioDeviceCallback(deviceCallback)
            audioManager?.removeOnModeChangedListener(modeChangedListener)
            audioManager?.removeOnCommunicationDeviceChangedListener(commDeviceListener)
        }
        runCatching {
            a2dpProxy?.let { proxy ->
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, proxy)
            }
        }
        a2dpProxy = null
    }

    @SuppressLint("MissingPermission")
    fun refresh(state: HeadsetState = lastHeadsetState) {
        lastHeadsetState = state
        val am = audioManager
        val targetAddr = state.deviceAddress.trim().uppercase(Locale.US)

        val audioMode = runCatching { am?.mode ?: AudioManager.MODE_NORMAL }.getOrDefault(AudioManager.MODE_NORMAL)
        val commDev = runCatching { am?.communicationDevice }.getOrNull()
        @Suppress("DEPRECATION")
        val legacyScoOn = runCatching { am?.isBluetoothScoOn == true }.getOrDefault(false)

        val outputs = runCatching {
            am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
        }.getOrDefault(emptyList())

        val matchingBtOutputs = outputs.filter { info ->
            val typeOk = info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            if (!typeOk) return@filter false
            if (targetAddr.isBlank()) true
            else info.address.equals(targetAddr, ignoreCase = true) ||
                info.productName.toString().equals(state.deviceName, ignoreCase = true)
        }

        val a2dpInfo = matchingBtOutputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: matchingBtOutputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }

        val commScoActive = commDev?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val inCallMode = audioMode == AudioManager.MODE_IN_CALL ||
            audioMode == AudioManager.MODE_IN_COMMUNICATION ||
            audioMode == AudioManager.MODE_RINGTONE
        val isHfpCallActive = (inCallMode && (commScoActive || legacyScoOn || state.isConnected)) ||
            commDev?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO

        val isLeAudioActive = a2dpInfo?.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            commDev?.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            state.codecName.equals("LC3", ignoreCase = true)

        var maxRate = 0
        var bits = 0
        var channels = 2

        if (a2dpInfo != null) {
            val profileRates = a2dpInfo.audioProfiles.flatMap { it.sampleRates.toList() }
            val devRates = a2dpInfo.sampleRates.toList()
            val candidateRates = (profileRates + devRates).filter { it > 0 }
            maxRate = candidateRates.maxOrNull() ?: 0

            val profileFormats = a2dpInfo.audioProfiles.map { it.format } + a2dpInfo.encodings.toList()
            bits = resolveBestBitsPerSample(profileFormats, state.codecName)
            channels = a2dpInfo.channelCounts.maxOrNull()?.coerceAtLeast(1) ?: 2

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && am != null) {
                runCatching {
                    am.getPreferredMixerAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                        a2dpInfo
                    )?.format?.let { mixerFmt ->
                        if (mixerFmt.sampleRate > 0) maxRate = mixerFmt.sampleRate
                        val fmtBits = encodingToBits(mixerFmt.encoding)
                        if (fmtBits > 0) bits = fmtBits
                    }
                }
            }
        }

        if (maxRate == 0 && state.codecName.isNotBlank()) {
            maxRate = when {
                state.codecName.equals("LDAC", ignoreCase = true) -> 96000
                state.codecName.equals("aptX HD", ignoreCase = true) -> 48000
                state.codecName.equals("AAC", ignoreCase = true) ||
                    state.codecName.equals("SBC", ignoreCase = true) -> 44100
                else -> 48000
            }
        }
        if (bits == 0 && state.codecName.isNotBlank()) {
            bits = when {
                state.codecName.equals("LDAC", ignoreCase = true) ||
                    state.codecName.equals("aptX HD", ignoreCase = true) -> 24
                else -> 16
            }
        }

        var isPlaying = false
        val hasBtPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        if (hasBtPerm) {
            val proxy = a2dpProxy
            if (proxy != null) {
                runCatching {
                    val connectedA2dp = proxy.connectedDevices
                    val btDevice: BluetoothDevice? = if (targetAddr.isNotBlank()) {
                        connectedA2dp.firstOrNull { it.address.equals(targetAddr, ignoreCase = true) }
                    } else {
                        connectedA2dp.firstOrNull()
                    }
                    if (btDevice != null) {
                        isPlaying = proxy.isA2dpPlaying(btDevice)
                    }
                }
            }
        }

        val resolvedCodec = when {
            state.codecName.isNotBlank() -> state.codecName
            isLeAudioActive -> "LC3"
            else -> ""
        }

        _hudInfo.value = LiveCodecHudInfo(
            codecName = resolvedCodec,
            sampleRateHz = maxRate,
            bitsPerSample = bits,
            channelCount = channels,
            isA2dpPlaying = isPlaying,
            isHfpCallActive = isHfpCallActive,
            isLeAudioActive = isLeAudioActive,
            isAudioRoutedToDevice = a2dpInfo != null,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun resolveBestBitsPerSample(encodings: List<Int>, codecName: String): Int {
        val mapped = encodings.map { encodingToBits(it) }.maxOrNull() ?: 0
        if (mapped > 0) return mapped
        return if (codecName.equals("LDAC", ignoreCase = true) || codecName.equals("aptX HD", ignoreCase = true)) 24 else 16
    }

    private fun encodingToBits(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_16BIT -> 16
        else -> 0
    }
}
