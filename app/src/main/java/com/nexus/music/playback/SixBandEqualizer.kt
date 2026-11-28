package com.nexus.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
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

    fun newProcessor(boost: () -> Float = { 1f }): AudioProcessor = EqualizerProcessor(this, boost)

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun setGains(values: List<Float>) {
        if (values.size != BAND_COUNT) return
        val updated = values.map { if (it.isFinite()) it.coerceIn(-12f, 12f) else 0f }.toFloatArray()
        if (!gains.contentEquals(updated)) gains = updated
    }

    internal fun currentGains(): FloatArray = gains

    internal fun coefficients(sampleRate: Int, current: FloatArray): Array<Coefficients> {
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
        val TREBLE_BOOST = listOf(0f, 0f, 0f, 0f, 2f, 4f)
        val VOCAL = listOf(-2f, -1f, 1.5f, 3f, 2f, 0f)
    }
}

@UnstableApi
private class EqualizerProcessor(
    private val equalizer: SixBandEqualizer,
    private val boost: () -> Float = { 1f }
) : BaseAudioProcessor() {
    private var sampleRate = 48_000
    private var channels = 2
    private var states: Array<Array<FilterState>> = emptyArray()
    private var configuredGains: FloatArray? = null
    private var filters: Array<SixBandEqualizer.Coefficients> = emptyArray()
    private var preamp = 1.0

    private fun updateFilters(gains: FloatArray) {
        resetFilterState()
        filters = equalizer.coefficients(sampleRate, gains)
        configuredGains = gains
        if (gains.none { it > 0f }) {
            preamp = 1.0
            return
        }
        // Measure the combined response. Overlapping boosts can exceed the largest band gain.
        val peak = (0..256).maxOf { step ->
            val omega = PI * (20.0 / (sampleRate / 2.0)).pow(1.0 - step / 256.0)
            val c1 = cos(omega)
            val c2 = cos(omega * 2.0)
            val s1 = sin(omega)
            val s2 = sin(omega * 2.0)
            filters.fold(1.0) { power, f ->
                fun magnitudeSquared(a: Double, b: Double, c: Double): Double {
                    val real = a + b * c1 + c * c2
                    val imaginary = b * s1 + c * s2
                    return real * real + imaginary * imaginary
                }
                power * magnitudeSquared(f.b0, f.b1, f.b2) / magnitudeSquared(1.0, f.a1, f.a2)
            }
        }
        preamp = 0.94 / kotlin.math.sqrt(peak.coerceAtLeast(1.0))
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        configuredGains = null
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
        val gains = equalizer.currentGains()
        val filtering = equalizer.enabled && gains.any { it != 0f }
        val trackBoost = boost()
        if (!filtering) {
            if (configuredGains != null) {
                resetFilterState()
                configuredGains = null
            }
            if (trackBoost == 1f) {
                output.put(input)
                output.flip()
                return
            }
        }
        if (filtering && configuredGains !== gains) updateFilters(gains)
        var sampleIndex = 0

        while (input.remaining() >= bytesPerSample) {
            val source = if (encoding == C.ENCODING_PCM_FLOAT) {
                input.float.toDouble()
            } else {
                input.short.toDouble() / 32768.0
            }
            var value = source * if (filtering) preamp else 1.0
            if (filtering) {
                val channel = sampleIndex % channels
                for (band in filters.indices) {
                    value = states[band][channel].process(value, filters[band])
                }
            }
            // Smoothly absorb rare transients above the headroom ceiling.
            value *= trackBoost
            val knee = if (trackBoost > 1f) 0.72 else 0.95
            val headroom = 0.9999 - knee
            val safe = if (abs(value) <= knee) value else
                sign(value) * (knee + headroom * (1.0 - kotlin.math.exp(-(abs(value) - knee) / headroom)))
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
