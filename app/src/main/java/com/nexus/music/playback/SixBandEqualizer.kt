package com.nexus.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** App-owned six-band EQ. Gains are applied to decoded PCM by Media3, not Android's global EQ. */
@OptIn(UnstableApi::class)
class SixBandEqualizer {
    @Volatile
    private var gains = BASS_BOOST.toFloatArray()

    @Volatile
    var enabled: Boolean = true
        private set

    val audioProcessor: AudioProcessor = EqualizerProcessor(this)

    fun newProcessor(): AudioProcessor = EqualizerProcessor(this)

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun setGains(values: List<Float>) {
        if (values.size != BAND_COUNT) return
        gains = values.map { it.coerceIn(-12f, 12f) }.toFloatArray()
    }

    internal fun coefficients(sampleRate: Int): Array<Coefficients> {
        val current = gains
        return Array(BAND_COUNT) { index ->
            val frequency = FREQUENCIES[index].coerceAtMost(sampleRate * 0.44f)
            peakingCoefficients(sampleRate.toDouble(), frequency.toDouble(), 1.0, current[index].toDouble())
        }
    }

    private fun peakingCoefficients(sampleRate: Double, frequency: Double, q: Double, gainDb: Double): Coefficients {
        if (kotlin.math.abs(gainDb) < 0.001) return Coefficients.IDENTITY
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * frequency / sampleRate
        val alpha = sin(omega) / (2.0 * q)
        val cosOmega = cos(omega)
        val a0 = 1.0 + alpha / a
        return Coefficients(
            b0 = (1.0 + alpha * a) / a0,
            b1 = (-2.0 * cosOmega) / a0,
            b2 = (1.0 - alpha * a) / a0,
            a1 = (-2.0 * cosOmega) / a0,
            a2 = (1.0 - alpha / a) / a0
        )
    }

    internal data class Coefficients(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    ) {
        companion object {
            val IDENTITY = Coefficients(1.0, 0.0, 0.0, 0.0, 0.0)
        }
    }

    companion object {
        const val BAND_COUNT = 6
        val FREQUENCIES = floatArrayOf(60f, 150f, 400f, 1_000f, 2_400f, 15_000f)
        val BASS_BOOST = listOf(5f, 3f, 0f, 1f, 0f, 2f)
        val DEFAULT = listOf(0f, 0f, 0f, 0f, 0f, 0f)
        val TREBLE_BOOST = listOf(-1f, 0f, 0f, 0f, 2f, 4f)
        val VOCAL = listOf(-2f, -1f, 1.5f, 3f, 2f, 0f)
    }
}

@UnstableApi
private class EqualizerProcessor(
    private val equalizer: SixBandEqualizer
) : BaseAudioProcessor() {
    private var sampleRate = 48_000
    private var channels = 2
    private var states: Array<Array<FilterState>> = emptyArray()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        states = Array(SixBandEqualizer.BAND_COUNT) { Array(channels) { FilterState() } }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val encoding = inputAudioFormat.encoding
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(input.remaining()).order(ByteOrder.nativeOrder())
        if (!equalizer.enabled) {
            output.put(input)
            output.flip()
            return
        }
        val filters = equalizer.coefficients(sampleRate)
        var sampleIndex = 0

        while (input.remaining() >= bytesPerSample) {
            val source = if (encoding == C.ENCODING_PCM_FLOAT) {
                input.float.toDouble()
            } else {
                input.short.toDouble() / 32768.0
            }
            var value = source
            if (equalizer.enabled && states.isNotEmpty()) {
                val channel = sampleIndex % channels
                for (band in filters.indices) {
                    value = states[band][channel].process(value, filters[band])
                }
            }
            val safe = value.coerceIn(-0.999969, 0.999969)
            if (encoding == C.ENCODING_PCM_FLOAT) {
                output.putFloat(safe.toFloat())
            } else {
                output.putShort((safe * 32767.0).toInt().toShort())
            }
            sampleIndex++
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        resetFilterState()
    }

    override fun onReset() {
        resetFilterState()
        states = emptyArray()
    }

    private fun resetFilterState() {
        states.forEach { row -> row.forEach { it.reset() } }
    }

    private class FilterState {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x: Double, c: SixBandEqualizer.Coefficients): Double {
            val y = c.b0 * x + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        fun reset() {
            x1 = 0.0
            x2 = 0.0
            y1 = 0.0
            y2 = 0.0
        }
    }
}
