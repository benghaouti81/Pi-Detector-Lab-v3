package com.example.felezjoo.models

import java.io.Serializable

/**
 * Immutable decay acquisition block.
 * Raw samples are preserved unconditionally.
 */
data class DecayBlock(
    val sequenceNumber: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val pulseRate: Int = 200,          // Hz
    val pulseWidthUs: Int = 150,       // us
    val delayTicks: Int = 10,
    val delayUs: Double = 16.0,
    val sampleSpacingUs: Double = 1.6,
    val sampleCount: Int = 70,
    val rawSamples: IntArray = IntArray(sampleCount),
    val firmwareVersion: String = "1.0",
    val protocolVersion: String = "1.0",
    val flags: Int = 0,
    val samplingConfiguration: SamplingConfiguration = SamplingConfiguration(sampleCount = sampleCount, sampleSpacingUs = sampleSpacingUs),
    val polarity: WaveformPolarity = samplingConfiguration.polarity,
    val polarityMode: PolarityMode = samplingConfiguration.polarityMode
) : Serializable {

    val isSaturated: Boolean
        get() {
            val maxVal = samplingConfiguration.maxAdcValue
            return rawSamples.any { it >= maxVal - 2 || it <= 2 }
        }

    /**
     * Physical time axis in microseconds relative to TX-off instant: t[i] = delayUs + i * dt
     */
    fun getAbsoluteTimeAxisUs(): DoubleArray {
        return DoubleArray(sampleCount) { i -> delayUs + (i * sampleSpacingUs) }
    }

    /**
     * Relative time axis in microseconds from start of acquisition: t[i] = i * dt
     */
    fun getRelativeTimeAxisUs(): DoubleArray {
        return DoubleArray(sampleCount) { i -> i * sampleSpacingUs }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecayBlock
        if (sequenceNumber != other.sequenceNumber) return false
        if (timestamp != other.timestamp) return false
        if (!rawSamples.contentEquals(other.rawSamples)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + rawSamples.contentHashCode()
        return result
    }
}
