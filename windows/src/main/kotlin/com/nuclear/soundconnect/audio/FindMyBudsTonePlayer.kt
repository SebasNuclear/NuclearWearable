package com.nuclear.soundconnect.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.sin

enum class EarSide {
    LEFT,
    RIGHT
}

data class FindBudsPlaybackState(
    val activeSide: EarSide? = null,
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = DEFAULT_DURATION_SECONDS
) {
    val isPlaying: Boolean get() = activeSide != null

    companion object {
        const val DEFAULT_DURATION_SECONDS = 20
    }
}

/**
 * Generates an acoustic beacon locally on Windows via javax.sound.sampled SourceDataLine
 * (stereo 44.1 kHz 16-bit PCM) strictly isolated to LEFT or RIGHT channel with a 4s gentle
 * volume ramp and 20s auto-stop. No external dependencies or audio assets required.
 */
class FindMyBudsTonePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _playbackState = MutableStateFlow(FindBudsPlaybackState())
    val playbackState: StateFlow<FindBudsPlaybackState> = _playbackState.asStateFlow()

    @Volatile private var playbackJob: Job? = null
    @Volatile private var activeLine: SourceDataLine? = null

    fun startTone(side: EarSide, durationSeconds: Int = FindBudsPlaybackState.DEFAULT_DURATION_SECONDS) {
        stop()
        val safeDuration = durationSeconds.coerceIn(5, 30)
        _playbackState.value = FindBudsPlaybackState(
            activeSide = side,
            elapsedSeconds = 0,
            totalSeconds = safeDuration
        )

        playbackJob = scope.launch {
            var line: SourceDataLine? = null
            try {
                val format = AudioFormat(
                    SAMPLE_RATE.toFloat(),
                    16,
                    2,
                    true,  // signed
                    false  // little-endian
                )
                val info = DataLine.Info(SourceDataLine::class.java, format)
                line = AudioSystem.getLine(info) as SourceDataLine
                val chunkBytes = CHUNK_FRAMES * 4 // 2 channels * 2 bytes/sample
                line.open(format, chunkBytes * 4)
                line.start()
                activeLine = line

                val pcmBytes = ByteArray(chunkBytes)
                var globalFrame = 0L
                val totalFrames = safeDuration.toLong() * SAMPLE_RATE

                while (isActive && globalFrame < totalFrames) {
                    val framesThisChunk = minOf(CHUNK_FRAMES.toLong(), totalFrames - globalFrame).toInt()
                    for (i in 0 until framesThisChunk) {
                        val tSec = (globalFrame + i).toDouble() / SAMPLE_RATE.toDouble()
                        val sample = computePulseSample(globalFrame + i, tSec)
                        val byteOffset = i * 4

                        val leftVal: Short = if (side == EarSide.LEFT) sample else 0
                        val rightVal: Short = if (side == EarSide.RIGHT) sample else 0

                        // Little-endian signed 16-bit stereo: [L_lsb, L_msb, R_lsb, R_msb]
                        pcmBytes[byteOffset] = (leftVal.toInt() and 0xFF).toByte()
                        pcmBytes[byteOffset + 1] = ((leftVal.toInt() ushr 8) and 0xFF).toByte()
                        pcmBytes[byteOffset + 2] = (rightVal.toInt() and 0xFF).toByte()
                        pcmBytes[byteOffset + 3] = ((rightVal.toInt() ushr 8) and 0xFF).toByte()
                    }
                    line.write(pcmBytes, 0, framesThisChunk * 4)
                    globalFrame += framesThisChunk

                    val elapsed = (globalFrame / SAMPLE_RATE).toInt().coerceAtMost(safeDuration)
                    if (_playbackState.value.elapsedSeconds != elapsed) {
                        _playbackState.value = FindBudsPlaybackState(
                            activeSide = side,
                            elapsedSeconds = elapsed,
                            totalSeconds = safeDuration
                        )
                    }
                }
                if (isActive) {
                    line.drain()
                }
            } catch (_: Throwable) {
                // Swallow audio hardware exceptions cleanly if output line becomes unavailable
            } finally {
                try {
                    line?.stop()
                    line?.flush()
                    line?.close()
                } catch (_: Throwable) {}
                if (activeLine === line) {
                    activeLine = null
                }
                _playbackState.value = FindBudsPlaybackState()
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        val line = activeLine
        activeLine = null
        if (line != null) {
            try {
                line.stop()
                line.flush()
                line.close()
            } catch (_: Throwable) {}
        }
        _playbackState.value = FindBudsPlaybackState()
    }

    fun shutdown() {
        stop()
    }

    private fun computePulseSample(frameIndex: Long, tSec: Double): Short {
        val posInCycleMs = ((frameIndex * 1000.0) / SAMPLE_RATE.toDouble()) % PULSE_CYCLE_MS
        if (posInCycleMs >= PULSE_ON_MS) return 0

        // Gentle envelope within each pulse prevents clicking (15 ms fade in/out)
        val env = when {
            posInCycleMs < EDGE_FADE_MS -> posInCycleMs / EDGE_FADE_MS
            posInCycleMs > PULSE_ON_MS - EDGE_FADE_MS -> (PULSE_ON_MS - posInCycleMs) / EDGE_FADE_MS
            else -> 1.0
        }.coerceIn(0.0, 1.0)

        // Progressive safety ramp: starts very gentle (12%) -> peaks at 72% after 4 seconds
        val safetyRamp = (0.12 + 0.60 * (tSec / RAMP_SECONDS).coerceIn(0.0, 1.0))

        // Dual high-penetration beacon (2100 Hz + 2800 Hz harmonic)
        val wave = 0.65 * sin(2.0 * PI * FREQ_PRIMARY_HZ * tSec) +
            0.35 * sin(2.0 * PI * FREQ_SECONDARY_HZ * tSec)

        return (wave * env * safetyRamp * Short.MAX_VALUE * 0.90).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val CHUNK_FRAMES = 4_410 // 100 ms chunks for responsive stop
        private const val FREQ_PRIMARY_HZ = 2100.0
        private const val FREQ_SECONDARY_HZ = 2800.0
        private const val PULSE_ON_MS = 350.0
        private const val PULSE_CYCLE_MS = 550.0 // 350 ms on / 200 ms off
        private const val EDGE_FADE_MS = 15.0
        private const val RAMP_SECONDS = 4.0
    }
}
