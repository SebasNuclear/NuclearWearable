package com.nuclear.soundconnect.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

enum class EarSide { LEFT, RIGHT }

data class FindBudsPlaybackState(
    val activeSide: EarSide? = null,
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = 20
) {
    val isPlaying: Boolean get() = activeSide != null
}

/**
 * Reproductor estéreo local de baliza sonora progresiva para "Find My Buds" (Fase 3.4).
 * Emite señal 100% aislada al canal L o R con rampa suave de ganancia y apagado automático a los 20s.
 */
class FindMyBudsTonePlayer {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val AUTO_STOP_SECONDS = 20
        private const val RAMP_SECONDS = 4.0
        private const val MAX_AMPLITUDE = 0.72
        private const val MIN_AMPLITUDE = 0.12
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var playbackJob: Job? = null

    private val _playbackState = MutableStateFlow(FindBudsPlaybackState())
    val playbackState: StateFlow<FindBudsPlaybackState> = _playbackState.asStateFlow()

    fun start(side: EarSide) {
        stop()
        _playbackState.value = FindBudsPlaybackState(activeSide = side, elapsedSeconds = 0, totalSeconds = AUTO_STOP_SECONDS)

        playbackJob = scope.launch {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(SAMPLE_RATE / 5)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val chunkFrames = SAMPLE_RATE / 20 // 50 ms por iteración
            val pcm = ShortArray(chunkFrames * 2)
            val totalFrames = SAMPLE_RATE * AUTO_STOP_SECONDS
            var frameCursor = 0

            try {
                track.play()
                while (isActive && frameCursor < totalFrames) {
                    val elapsedSec = frameCursor / SAMPLE_RATE
                    if (_playbackState.value.elapsedSeconds != elapsedSec) {
                        _playbackState.value = FindBudsPlaybackState(
                            activeSide = side,
                            elapsedSeconds = elapsedSec,
                            totalSeconds = AUTO_STOP_SECONDS
                        )
                    }

                    for (i in 0 until chunkFrames) {
                        val tSec = (frameCursor + i).toDouble() / SAMPLE_RATE
                        // Patrón pulsado de localización: 350ms ON / 200ms OFF para mayor audibilidad sin fatiga
                        val cyclePos = tSec % 0.55
                        val pulseGate = if (cyclePos < 0.35) {
                            // Envolvente anti-click de 15ms en bordes del pulso
                            val edgeIn = (cyclePos / 0.015).coerceIn(0.0, 1.0)
                            val edgeOut = ((0.35 - cyclePos) / 0.015).coerceIn(0.0, 1.0)
                            minOf(edgeIn, edgeOut)
                        } else {
                            0.0
                        }

                        val ramp = (tSec / RAMP_SECONDS).coerceIn(0.0, 1.0)
                        val gain = MIN_AMPLITUDE + (MAX_AMPLITUDE - MIN_AMPLITUDE) * ramp
                        // Doble tono agudo claro (2100 Hz + 2800 Hz) fácilmente audible fuera del oído
                        val wave = 0.65 * sin(2.0 * PI * 2100.0 * tSec) +
                            0.35 * sin(2.0 * PI * 2800.0 * tSec)
                        val sample = (wave * gain * pulseGate * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()

                        val idx = i * 2
                        if (side == EarSide.LEFT) {
                            pcm[idx] = sample
                            pcm[idx + 1] = 0
                        } else {
                            pcm[idx] = 0
                            pcm[idx + 1] = sample
                        }
                    }

                    track.write(pcm, 0, pcm.size)
                    frameCursor += chunkFrames
                }
            } finally {
                runCatching {
                    track.pause()
                    track.flush()
                    track.stop()
                }
                runCatching { track.release() }
                _playbackState.value = FindBudsPlaybackState()
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _playbackState.value = FindBudsPlaybackState()
    }
}
