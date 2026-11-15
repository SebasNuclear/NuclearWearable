package com.nuclear.soundconnect.audio

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid.CLSID
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.nuclear.soundconnect.bluetooth.ConnectionPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Live audio & Bluetooth codec HUD telemetry for Windows desktop.
 * Combines RFCOMM protocol codec metadata with local Windows CoreAudio (WASAPI) endpoint state.
 * Strictly local — zero network, GPS, or process-spy telemetry.
 */
data class LiveCodecHudInfo(
    val codecName: String = "",
    val sampleRateHz: Int = 0,
    val bitsPerSample: Int = 0,
    val channelCount: Int = 2,
    val isA2dpPlaying: Boolean = false,
    val isHfpCallActive: Boolean = false,
    val isLeAudioActive: Boolean = false,
    val isAudioRoutedToDevice: Boolean = false,
    val outputDeviceName: String = "",
    val lastUpdated: Long = 0L
) {
    fun formatShortBadge(): String {
        val base = codecName.ifBlank { return "" }
        val rate = if (sampleRateHz > 0) {
            val khz = sampleRateHz / 1000f
            if (khz == khz.toInt().toFloat()) "${khz.toInt()} kHz"
            else String.format(Locale.US, "%.1f kHz", khz)
        } else ""
        val bits = if (bitsPerSample > 0) "${bitsPerSample}b" else ""
        val detail = listOf(rate, bits).filter { it.isNotEmpty() }.joinToString(" / ")
        return if (detail.isNotEmpty()) "$base · $detail" else base
    }
}

private interface Ole32CoreAudioExt : StdCallLibrary {
    fun PropVariantClear(pvar: Pointer): Int
    fun CoTaskMemFree(pv: Pointer?)

    companion object {
        val INSTANCE: Ole32CoreAudioExt by lazy {
            Native.load("ole32", Ole32CoreAudioExt::class.java)
        }
    }
}

/**
 * Queries local Windows CoreAudio endpoints (MMDeviceEnumerator / IAudioMeterInformation /
 * IAudioSessionManager2) via JNA COM to detect active playback routing, endpoint format,
 * and active Hands-Free (HFP) voice/call state without spawning external shells.
 */
class WindowsAudioMonitor(
    private val onAudioStateChanged: ((isA2dpPlaying: Boolean, isHfpCallActive: Boolean) -> Unit)? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    private val _liveInfo = MutableStateFlow(LiveCodecHudInfo())
    val liveInfo: StateFlow<LiveCodecHudInfo> = _liveInfo.asStateFlow()

    @Volatile private var protocolCodecName: String = ""
    @Volatile private var targetDeviceName: String = ""
    @Volatile private var protocolConnPriority: ConnectionPriority? = null
    @Volatile private var lastPlayingPeakTimeMs: Long = 0L
    @Volatile private var lastDispatchedPlaying: Boolean? = null
    @Volatile private var lastDispatchedHfp: Boolean? = null

    fun startMonitoring(pollIntervalMs: Long = POLL_INTERVAL_MS) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                refreshNow()
                delay(pollIntervalMs)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun updateFromProtocol(
        codecName: String,
        deviceName: String,
        connectionPriority: ConnectionPriority?
    ) {
        protocolCodecName = codecName
        targetDeviceName = deviceName
        protocolConnPriority = connectionPriority
        refreshNow()
    }

    fun clearDevice() {
        protocolCodecName = ""
        targetDeviceName = ""
        protocolConnPriority = null
        lastPlayingPeakTimeMs = 0L
        _liveInfo.value = LiveCodecHudInfo()
    }

    fun refreshNow() {
        val devName = targetDeviceName
        val codec = protocolCodecName
        val priority = protocolConnPriority
        val snapshot = queryCoreAudioSnapshot(devName)

        val now = System.currentTimeMillis()
        if (snapshot.isRenderPlaying) {
            lastPlayingPeakTimeMs = now
        }
        // Hold playback state briefly across track transitions so automation doesn't chatter
        val smoothedPlaying = snapshot.isRenderPlaying ||
            (lastPlayingPeakTimeMs > 0L && now - lastPlayingPeakTimeMs <= PLAYBACK_HOLD_MS)

        val isLeAudio = priority == ConnectionPriority.LOW_LATENCY ||
            codec.contains("LC3", ignoreCase = true) ||
            snapshot.defaultRenderName.contains("LE Audio", ignoreCase = true) ||
            snapshot.defaultRenderName.contains("LC3", ignoreCase = true)

        val routedA2dpPlaying = smoothedPlaying &&
            (snapshot.isRoutedToTarget || snapshot.defaultRenderName.isBluetoothMatch(devName))

        val resolvedSampleRate = when {
            snapshot.isRoutedToTarget && snapshot.sampleRateHz > 0 ->
                mergeCodecSampleRate(codec, snapshot.sampleRateHz)
            codec.isNotBlank() -> inferSampleRate(codec)
            snapshot.sampleRateHz > 0 -> snapshot.sampleRateHz
            else -> 0
        }

        val resolvedBits = when {
            snapshot.isRoutedToTarget && snapshot.bitsPerSample in listOf(16, 24) ->
                mergeCodecBitDepth(codec, snapshot.bitsPerSample)
            codec.isNotBlank() -> inferBitDepth(codec)
            snapshot.bitsPerSample in listOf(16, 24) -> snapshot.bitsPerSample
            else -> 16
        }

        val updated = LiveCodecHudInfo(
            codecName = codec,
            sampleRateHz = resolvedSampleRate,
            bitsPerSample = resolvedBits,
            channelCount = snapshot.channelCount.coerceIn(1, 8),
            isA2dpPlaying = routedA2dpPlaying,
            isHfpCallActive = snapshot.isHfpCallActive,
            isLeAudioActive = isLeAudio,
            isAudioRoutedToDevice = snapshot.isRoutedToTarget,
            outputDeviceName = snapshot.defaultRenderName,
            lastUpdated = now
        )
        _liveInfo.value = updated

        val prevPlaying = lastDispatchedPlaying
        val prevHfp = lastDispatchedHfp
        if (prevPlaying != updated.isA2dpPlaying || prevHfp != updated.isHfpCallActive) {
            lastDispatchedPlaying = updated.isA2dpPlaying
            lastDispatchedHfp = updated.isHfpCallActive
            if (prevPlaying != null || prevHfp != null || updated.isHfpCallActive) {
                onAudioStateChanged?.invoke(updated.isA2dpPlaying, updated.isHfpCallActive)
            }
        }
    }

    fun shutdown() {
        stopMonitoring()
    }

    internal data class CoreAudioSnapshot(
        val defaultRenderName: String = "",
        val sampleRateHz: Int = 0,
        val bitsPerSample: Int = 0,
        val channelCount: Int = 2,
        val isRenderPlaying: Boolean = false,
        val isRoutedToTarget: Boolean = false,
        val isHfpCallActive: Boolean = false
    )

    internal fun queryCoreAudioSnapshot(targetDevice: String): CoreAudioSnapshot {
        var didInitCom = false
        try {
            val hrInit = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
            val code = hrInit.toInt()
            if (code == S_OK || code == S_FALSE) {
                didInitCom = true
            } else if (code != RPC_E_CHANGED_MODE) {
                return CoreAudioSnapshot()
            }

            val enumPtr = PointerByReference()
            val hrCreate = Ole32.INSTANCE.CoCreateInstance(
                CLSID_MM_DEVICE_ENUMERATOR,
                null,
                WTypes.CLSCTX_ALL,
                IID_IMM_DEVICE_ENUMERATOR,
                enumPtr
            )
            if (COMUtils.FAILED(hrCreate) || enumPtr.value == null) {
                return CoreAudioSnapshot()
            }

            val enumerator = Unknown(enumPtr.value)
            try {
                val defaultRender = queryDefaultEndpoint(enumerator, DATA_FLOW_RENDER, ROLE_MULTIMEDIA)
                val commRender = queryDefaultEndpoint(enumerator, DATA_FLOW_RENDER, ROLE_COMMUNICATIONS)
                val commCapture = queryDefaultEndpoint(enumerator, DATA_FLOW_CAPTURE, ROLE_COMMUNICATIONS)

                val routedToTarget = defaultRender.friendlyName.isBluetoothMatch(targetDevice) ||
                    commRender.friendlyName.isBluetoothMatch(targetDevice)

                val renderPlaying = defaultRender.peakValue > PEAK_ACTIVITY_THRESHOLD ||
                    defaultRender.hasActiveSession

                val activeHfp = checkAnyActiveHfpEndpoint(enumerator, targetDevice) ||
                    (commCapture.hasActiveSession && commCapture.friendlyName.isHfpName()) ||
                    (commRender.hasActiveSession && commRender.friendlyName.isHfpName())

                return CoreAudioSnapshot(
                    defaultRenderName = defaultRender.friendlyName,
                    sampleRateHz = defaultRender.sampleRateHz,
                    bitsPerSample = defaultRender.bitsPerSample,
                    channelCount = defaultRender.channelCount,
                    isRenderPlaying = renderPlaying,
                    isRoutedToTarget = routedToTarget,
                    isHfpCallActive = activeHfp
                )
            } finally {
                enumerator.Release()
            }
        } catch (_: Throwable) {
            return CoreAudioSnapshot()
        } finally {
            if (didInitCom) {
                try {
                    Ole32.INSTANCE.CoUninitialize()
                } catch (_: Throwable) {}
            }
        }
    }

    private data class EndpointDetails(
        val friendlyName: String = "",
        val sampleRateHz: Int = 0,
        val bitsPerSample: Int = 0,
        val channelCount: Int = 2,
        val peakValue: Float = 0f,
        val hasActiveSession: Boolean = false
    )

    private fun queryDefaultEndpoint(
        enumerator: Unknown,
        dataFlow: Int,
        role: Int
    ): EndpointDetails {
        val devRef = PointerByReference()
        val hr = invokeCom(
            enumerator,
            VTABLE_ENUMERATOR_GET_DEFAULT_ENDPOINT,
            arrayOf(enumerator.pointer, dataFlow, role, devRef)
        )
        if (hr != S_OK || devRef.value == null) return EndpointDetails()
        val device = Unknown(devRef.value)
        return try {
            readDeviceDetails(device)
        } finally {
            device.Release()
        }
    }

    private fun checkAnyActiveHfpEndpoint(enumerator: Unknown, targetDevice: String): Boolean {
        return checkCollectionForActiveHfp(enumerator, DATA_FLOW_CAPTURE, targetDevice) ||
            checkCollectionForActiveHfp(enumerator, DATA_FLOW_RENDER, targetDevice)
    }

    private fun checkCollectionForActiveHfp(
        enumerator: Unknown,
        dataFlow: Int,
        targetDevice: String
    ): Boolean {
        val colRef = PointerByReference()
        val hr = invokeCom(
            enumerator,
            VTABLE_ENUMERATOR_ENUM_ENDPOINTS,
            arrayOf(enumerator.pointer, dataFlow, DEVICE_STATE_ACTIVE, colRef)
        )
        if (hr != S_OK || colRef.value == null) return false
        val collection = Unknown(colRef.value)
        try {
            val countRef = IntByReference()
            if (invokeCom(collection, VTABLE_COLLECTION_GET_COUNT, arrayOf(collection.pointer, countRef)) != S_OK) {
                return false
            }
            val count = countRef.value.coerceIn(0, 64)
            for (i in 0 until count) {
                val itemRef = PointerByReference()
                if (invokeCom(collection, VTABLE_COLLECTION_ITEM, arrayOf(collection.pointer, i, itemRef)) == S_OK &&
                    itemRef.value != null
                ) {
                    val device = Unknown(itemRef.value)
                    try {
                        val name = readFriendlyName(device)
                        if (name.isHfpName() &&
                            (targetDevice.isBlank() || name.isBluetoothMatch(targetDevice))
                        ) {
                            if (hasActiveAudioSession(device) || readMeterPeak(device) > PEAK_ACTIVITY_THRESHOLD) {
                                return true
                            }
                        }
                    } finally {
                        device.Release()
                    }
                }
            }
        } finally {
            collection.Release()
        }
        return false
    }

    private fun readDeviceDetails(device: Unknown): EndpointDetails {
        val friendlyName = readFriendlyName(device)
        val (rate, bits, ch) = readDeviceFormat(device)
        val peak = readMeterPeak(device)
        val activeSession = hasActiveAudioSession(device)
        return EndpointDetails(
            friendlyName = friendlyName,
            sampleRateHz = rate,
            bitsPerSample = bits,
            channelCount = ch,
            peakValue = peak,
            hasActiveSession = activeSession
        )
    }

    private fun readFriendlyName(device: Unknown): String {
        val storeRef = PointerByReference()
        val hr = invokeCom(
            device,
            VTABLE_DEVICE_OPEN_PROPERTY_STORE,
            arrayOf(device.pointer, STGM_READ, storeRef)
        )
        if (hr != S_OK || storeRef.value == null) return ""
        val store = Unknown(storeRef.value)
        try {
            val pkey = Memory(20) // PROPERTYKEY = 16-byte GUID + 4-byte DWORD pid
            PKEY_FRIENDLY_NAME_GUID.writeToMemory(pkey)
            pkey.setInt(16, 14)

            val propVar = Memory(24).apply { clear() }
            val hrVal = invokeCom(store, VTABLE_PROPSTORE_GET_VALUE, arrayOf(store.pointer, pkey, propVar))
            if (hrVal != S_OK) return ""
            try {
                val vt = propVar.getShort(0).toInt() and 0xFFFF
                if (vt == VT_LPWSTR) {
                    val strPtr = propVar.getPointer(8)
                    return strPtr?.getWideString(0)?.trim() ?: ""
                }
            } finally {
                Ole32CoreAudioExt.INSTANCE.PropVariantClear(propVar)
            }
        } finally {
            store.Release()
        }
        return ""
    }

    private fun readDeviceFormat(device: Unknown): Triple<Int, Int, Int> {
        val storeRef = PointerByReference()
        val hr = invokeCom(
            device,
            VTABLE_DEVICE_OPEN_PROPERTY_STORE,
            arrayOf(device.pointer, STGM_READ, storeRef)
        )
        if (hr != S_OK || storeRef.value == null) return Triple(0, 0, 2)
        val store = Unknown(storeRef.value)
        try {
            val pkey = Memory(20)
            PKEY_DEVICE_FORMAT_GUID.writeToMemory(pkey)
            pkey.setInt(16, 0)

            val propVar = Memory(24).apply { clear() }
            val hrVal = invokeCom(store, VTABLE_PROPSTORE_GET_VALUE, arrayOf(store.pointer, pkey, propVar))
            if (hrVal != S_OK) return Triple(0, 0, 2)
            try {
                val vt = propVar.getShort(0).toInt() and 0xFFFF
                if (vt == VT_BLOB) {
                    // On 64-bit Windows, PROPVARIANT BLOB layout: offset 8 = cbSize (ULONG), offset 16 = pBlobData
                    val ptrOffset = if (com.sun.jna.Native.POINTER_SIZE == 8) 16L else 12L
                    val cbSize = propVar.getInt(8)
                    val blobPtr = propVar.getPointer(ptrOffset)
                    if (blobPtr != null && cbSize >= 18) {
                        val wFormatTag = blobPtr.getShort(0).toInt() and 0xFFFF
                        val nChannels = blobPtr.getShort(2).toInt() and 0xFFFF
                        val nSamplesPerSec = blobPtr.getInt(4)
                        val wBitsPerSample = blobPtr.getShort(14).toInt() and 0xFFFF
                        val validBits = if (wFormatTag == WAVE_FORMAT_EXTENSIBLE && cbSize >= 22) {
                            blobPtr.getShort(18).toInt() and 0xFFFF
                        } else {
                            wBitsPerSample
                        }
                        val reportedBits = when {
                            validBits in listOf(16, 24) -> validBits
                            wBitsPerSample == 32 -> 24
                            wBitsPerSample > 0 -> wBitsPerSample
                            else -> 16
                        }
                        return Triple(
                            if (nSamplesPerSec in 8000..384000) nSamplesPerSec else 0,
                            reportedBits,
                            if (nChannels in 1..8) nChannels else 2
                        )
                    }
                }
            } finally {
                Ole32CoreAudioExt.INSTANCE.PropVariantClear(propVar)
            }
        } finally {
            store.Release()
        }
        return Triple(0, 0, 2)
    }

    private fun readMeterPeak(device: Unknown): Float {
        val meterRef = PointerByReference()
        val iidMem = Memory(16)
        IID_IAUDIO_METER_INFO.writeToMemory(iidMem)
        val hr = invokeCom(
            device,
            VTABLE_DEVICE_ACTIVATE,
            arrayOf(device.pointer, iidMem, WTypes.CLSCTX_ALL, null, meterRef)
        )
        if (hr != S_OK || meterRef.value == null) return 0f
        val meter = Unknown(meterRef.value)
        return try {
            val peakRef = FloatByReference()
            if (invokeCom(meter, VTABLE_METER_GET_PEAK, arrayOf(meter.pointer, peakRef)) == S_OK) {
                peakRef.value
            } else 0f
        } finally {
            meter.Release()
        }
    }

    private fun hasActiveAudioSession(device: Unknown): Boolean {
        val mgrRef = PointerByReference()
        val iidMem = Memory(16)
        IID_IAUDIO_SESSION_MANAGER2.writeToMemory(iidMem)
        val hr = invokeCom(
            device,
            VTABLE_DEVICE_ACTIVATE,
            arrayOf(device.pointer, iidMem, WTypes.CLSCTX_ALL, null, mgrRef)
        )
        if (hr != S_OK || mgrRef.value == null) return false
        val mgr = Unknown(mgrRef.value)
        try {
            val enumRef = PointerByReference()
            if (invokeCom(mgr, VTABLE_SESSION_MGR2_GET_ENUMERATOR, arrayOf(mgr.pointer, enumRef)) != S_OK ||
                enumRef.value == null
            ) {
                return false
            }
            val sessEnum = Unknown(enumRef.value)
            try {
                val countRef = IntByReference()
                if (invokeCom(sessEnum, VTABLE_SESSION_ENUM_GET_COUNT, arrayOf(sessEnum.pointer, countRef)) != S_OK) {
                    return false
                }
                val count = countRef.value.coerceIn(0, 64)
                for (i in 0 until count) {
                    val ctrlRef = PointerByReference()
                    if (invokeCom(sessEnum, VTABLE_SESSION_ENUM_GET_SESSION, arrayOf(sessEnum.pointer, i, ctrlRef)) == S_OK &&
                        ctrlRef.value != null
                    ) {
                        val ctrl = Unknown(ctrlRef.value)
                        try {
                            val stateRef = IntByReference()
                            if (invokeCom(ctrl, VTABLE_SESSION_CTRL_GET_STATE, arrayOf(ctrl.pointer, stateRef)) == S_OK &&
                                stateRef.value == AUDIO_SESSION_STATE_ACTIVE
                            ) {
                                return true
                            }
                        } finally {
                            ctrl.Release()
                        }
                    }
                }
            } finally {
                sessEnum.Release()
            }
        } finally {
            mgr.Release()
        }
        return false
    }

    private fun invokeCom(obj: Unknown, vtableId: Int, args: Array<Any?>): Int {
        val vtbl = obj.pointer.getPointer(0)
        val fn = com.sun.jna.Function.getFunction(vtbl.getPointer(vtableId.toLong() * com.sun.jna.Native.POINTER_SIZE))
        return fn.invokeInt(args)
    }

    private fun GUID.writeToMemory(mem: Memory) {
        mem.setInt(0, Data1)
        mem.setShort(4, Data2)
        mem.setShort(6, Data3)
        mem.write(8, Data4, 0, 8)
    }

    private fun mergeCodecSampleRate(codecName: String, windowsRate: Int): Int {
        val upper = codecName.uppercase(Locale.US)
        return when {
            "LDAC" in upper -> maxOf(windowsRate, 96000)
            else -> windowsRate
        }
    }

    private fun mergeCodecBitDepth(codecName: String, windowsBits: Int): Int {
        val upper = codecName.uppercase(Locale.US)
        return when {
            "LDAC" in upper || "APTX HD" in upper -> 24
            else -> windowsBits
        }
    }

    private fun inferSampleRate(codec: String): Int = when {
        codec.contains("LDAC", ignoreCase = true) -> 96000
        codec.contains("aptX", ignoreCase = true) -> 48000
        codec.contains("LC3", ignoreCase = true) -> 48000
        codec.contains("AAC", ignoreCase = true) -> 44100
        codec.contains("SBC", ignoreCase = true) -> 44100
        else -> 48000
    }

    private fun inferBitDepth(codec: String): Int = when {
        codec.contains("LDAC", ignoreCase = true) -> 24
        codec.contains("aptX HD", ignoreCase = true) -> 24
        codec.contains("AAC", ignoreCase = true) -> 16
        codec.contains("SBC", ignoreCase = true) -> 16
        codec.contains("LC3", ignoreCase = true) -> 16
        else -> 16
    }

    private fun String.isBluetoothMatch(targetDevice: String): Boolean {
        if (this.isBlank() || targetDevice.isBlank()) return false
        val cleanTarget = targetDevice.trim()
        if (this.contains(cleanTarget, ignoreCase = true)) return true
        // Also match core model tokens like "WH-1000XM5", "WF-1000XM4", "LinkBuds"
        val tokens = cleanTarget.split(" ").filter { it.length >= 4 }
        return tokens.any { this.contains(it, ignoreCase = true) }
    }

    private fun String.isHfpName(): Boolean =
        contains("Hands-Free", ignoreCase = true) ||
            contains("HFP", ignoreCase = true) ||
            contains("AG Audio", ignoreCase = true)

    companion object {
        private const val POLL_INTERVAL_MS = 2500L
        private const val PLAYBACK_HOLD_MS = 2500L
        private const val PEAK_ACTIVITY_THRESHOLD = 0.0005f

        private const val S_OK = 0
        private const val S_FALSE = 1
        private const val RPC_E_CHANGED_MODE = -2147417850 // 0x80010106

        private const val DATA_FLOW_RENDER = 0
        private const val DATA_FLOW_CAPTURE = 1
        private const val ROLE_MULTIMEDIA = 1
        private const val ROLE_COMMUNICATIONS = 2
        private const val DEVICE_STATE_ACTIVE = 1
        private const val STGM_READ = 0
        private const val VT_LPWSTR = 31
        private const val VT_BLOB = 65
        private const val WAVE_FORMAT_EXTENSIBLE = 0xFFFE
        private const val AUDIO_SESSION_STATE_ACTIVE = 1

        // COM VTable indices (0..2 = IUnknown)
        private const val VTABLE_ENUMERATOR_ENUM_ENDPOINTS = 3
        private const val VTABLE_ENUMERATOR_GET_DEFAULT_ENDPOINT = 4
        private const val VTABLE_COLLECTION_GET_COUNT = 3
        private const val VTABLE_COLLECTION_ITEM = 4
        private const val VTABLE_DEVICE_ACTIVATE = 3
        private const val VTABLE_DEVICE_OPEN_PROPERTY_STORE = 4
        private const val VTABLE_PROPSTORE_GET_VALUE = 5
        private const val VTABLE_METER_GET_PEAK = 3
        private const val VTABLE_SESSION_MGR2_GET_ENUMERATOR = 5
        private const val VTABLE_SESSION_ENUM_GET_COUNT = 3
        private const val VTABLE_SESSION_ENUM_GET_SESSION = 4
        private const val VTABLE_SESSION_CTRL_GET_STATE = 3

        private val CLSID_MM_DEVICE_ENUMERATOR = CLSID("BCDE0395-E52F-467C-8E3D-C4579291692E")
        private val IID_IMM_DEVICE_ENUMERATOR = IID("A95664D2-9614-4F35-A746-DE8DB63617E6")
        private val IID_IAUDIO_METER_INFO = IID("C02216F6-8C67-4B5B-9D00-D008E73E0064")
        private val IID_IAUDIO_SESSION_MANAGER2 = IID("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F")
        private val PKEY_FRIENDLY_NAME_GUID = GUID("A45C254E-DF1C-4EFD-8020-67D146A850E0")
        private val PKEY_DEVICE_FORMAT_GUID = GUID("F19F064D-082C-4E27-BC73-6882A1BB8E4C")
    }
}
