package com.nuclear.soundconnect.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.sin

enum class EarSide {
    LEFT, RIGHT
}

data class FindBudsPlaybackState(
    val activeSide: EarSide? = null,
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = 20
) {
    val isPlaying: Boolean get() = activeSide != null
}

/**
 * Baliza acústica estéreo local para localizar auriculares extraviados en Linux Desktop
 * usando `javax.sound.sampled` (ALSA / PipeWire / PulseAudio sin bloqueos ni red):
 * - Aislamiento estricto 100% por canal L o R.
 * - Tono dual de alta localización (2100 + 2800 Hz pulsado 350ms ON / 200ms OFF).
 * - Rampa progresiva segura de 4 segundos (12% -> 72% pico).
 * - Auto-stop obligatorio a los 20 segundos.
 */
class FindMyBudsTonePlayer {

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val DURATION_SECONDS = 20
        private const val RAMP_SECONDS = 4.0
        private const val MIN_AMPLITUDE = 0.12
        private const val MAX_AMPLITUDE = 0.72
        private const val CHUNK_FRAMES = 2_205 // ~50 ms por escritura
    }

    private val _state = MutableStateFlow(FindBudsPlaybackState())
    val state: StateFlow<FindBudsPlaybackState> = _state.asStateFlow()

    @Volatile
    private var playingThread: Thread? = null

    @Volatile
    private var activeLine: SourceDataLine? = null

    @Volatile
    private var stopRequested = false

    fun start(side: EarSide): Result<Unit> {
        stop()
        return runCatching {
            val format = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE.toFloat(),
                16,
                2,
                4,
                SAMPLE_RATE.toFloat(),
                false // little-endian
            )
            val info = DataLine.Info(SourceDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) {
                error("Audio line PCM 44100Hz estéreo no soportada")
            }
            val line = (AudioSystem.getLine(info) as SourceDataLine).apply {
                open(format, SAMPLE_RATE * 4 / 5) // buffer ~200 ms
                start()
            }
            activeLine = line
            stopRequested = false
            _state.value = FindBudsPlaybackState(activeSide = side, elapsedSeconds = 0, totalSeconds = DURATION_SECONDS)

            val thread = Thread({
                try {
                    val totalFrames = SAMPLE_RATE * DURATION_SECONDS
                    val pcmBytes = ByteArray(CHUNK_FRAMES * 4)
                    var frameIndex = 0
                    val twoPiF1 = 2.0 * PI * 2100.0
                    val twoPiF2 = 2.0 * PI * 2800.0

                    while (!stopRequested && frameIndex < totalFrames) {
                        val framesThisBatch = minOf(CHUNK_FRAMES, totalFrames - frameIndex)
                        var bytePos = 0

                        for (i in 0 until framesThisBatch) {
                            val globalFrame = frameIndex + i
                            val t = globalFrame.toDouble() / SAMPLE_RATE

                            val cycle = t % 0.55
                            val env = when {
                                cycle < 0.015 -> cycle / 0.015
                                cycle < 0.335 -> 1.0
                                cycle < 0.35 -> (0.35 - cycle) / 0.015
                                else -> 0.0
                            }

                            val ramp = ((t / RAMP_SECONDS).coerceIn(0.0, 1.0))
                            val gain = MIN_AMPLITUDE + (MAX_AMPLITUDE - MIN_AMPLITUDE) * ramp
                            val wave = 0.60 * sin(twoPiF1 * t) + 0.40 * sin(twoPiF2 * t)
                            val sample = (wave * env * gain * 32_767.0)
                                .toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()

                            val left: Short = if (side == EarSide.LEFT) sample else 0
                            val right: Short = if (side == EarSide.RIGHT) sample else 0

                            val lInt = left.toInt()
                            val rInt = right.toInt()
                            pcmBytes[bytePos++] = (lInt and 0xFF).toByte()
                            pcmBytes[bytePos++] = ((lInt ushr 8) and 0xFF).toByte()
                            pcmBytes[bytePos++] = (rInt and 0xFF).toByte()
                            pcmBytes[bytePos++] = ((rInt ushr 8) and 0xFF).toByte()
                        }

                        line.write(pcmBytes, 0, framesThisBatch * 4)
                        frameIndex += framesThisBatch
                        val elapsed = (frameIndex / SAMPLE_RATE).coerceAtMost(DURATION_SECONDS)
                        if (_state.value.elapsedSeconds != elapsed && !stopRequested) {
                            _state.value = FindBudsPlaybackState(
                                activeSide = side,
                                elapsedSeconds = elapsed,
                                totalSeconds = DURATION_SECONDS
                            )
                        }
                    }

                    if (!stopRequested) {
                        runCatching { line.drain() }
                    }
                } finally {
                    runCatching {
                        line.stop()
                        line.flush()
                        line.close()
                    }
                    if (activeLine === line) activeLine = null
                    _state.value = FindBudsPlaybackState()
                }
            }, "FindMyBuds-AudioPlayer").apply {
                isDaemon = true
                start()
            }
            playingThread = thread
        }
    }

    fun stop() {
        stopRequested = true
        val line = activeLine
        activeLine = null
        runCatching {
            line?.stop()
            line?.flush()
            line?.close()
        }
        val t = playingThread
        playingThread = null
        runCatching { t?.join(250) }
        _state.value = FindBudsPlaybackState()
    }
}
