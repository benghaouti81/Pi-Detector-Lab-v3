package com.example.felezjoo.models

import java.io.Serializable

/**
 * Hardware-independent configuration for Equivalent-Time Sampling (ETS)
 * or real-time sampling Pulse Induction acquisition.
 */
data class SamplingConfiguration(
    val id: String = "leonardo_16mhz_ets",
    val sampleCount: Int = 70,
    val sampleSpacingUs: Double = 1.6,
    val samplingMode: String = "ETS",
    val pulsesPerFrame: Int = 14,
    val samplesPerPulse: Int = 5,
    val clockFrequencyHz: Long = 16_000_000L,
    val adcResolution: Int = 10,
    val delayUnitUs: Double = 1.6,
    val minDelayTicks: Int = 1,
    val maxDelayTicks: Int = 100,
    val integrationStartUs: Double = 10.0,
    val integrationWidthUs: Double = 30.0,
    val integrationEndUs: Double = 45.0,
    val delayUs: Double = 10.0,
    val regionAStartUs: Double = 8.0,
    val regionAEndUs: Double = 18.0,
    val regionBStartUs: Double = 18.0,
    val regionBEndUs: Double = 35.0,
    val regionCStartUs: Double = 35.0,
    val regionCEndUs: Double = 65.0,
    val polarity: WaveformPolarity = WaveformPolarity.POSITIVE,
    val transportOrder: com.example.felezjoo.dsp.EtsTransportOrder = com.example.felezjoo.dsp.EtsTransportOrder.CHRONOLOGICAL,
    val timeOrigin: String = "TX_OFF_PLUS_DELAY"
) : Serializable {

    val maxAdcValue: Int
        get() = (1 shl adcResolution) - 1

    val adcFullScale: Double
        get() = maxAdcValue.toDouble()

    /**
     * Physical time elapsed relative to the start of acquisition (sample index 0 = 0.0 us).
     */
    fun sampleTimeUs(index: Int): Double {
        return index * sampleSpacingUs
    }

    /**
     * Absolute physical time elapsed from the transmitter coil turn-off instant (TX-off).
     * At sample index 0, physical time is blockDelayUs (or config.delayUs).
     */
    fun absoluteSampleTimeUs(index: Int, blockDelayUs: Double = delayUs): Double {
        return blockDelayUs + (index * sampleSpacingUs)
    }

    /**
     * Maps physical microsecond timestamp relative to acquisition start to sample index.
     */
    fun timeUsToSampleIndex(timeUs: Double): Int {
        if (sampleSpacingUs <= 0.0) return 0
        return (timeUs / sampleSpacingUs).toInt().coerceIn(0, sampleCount - 1)
    }

    /**
     * Maps physical microsecond start/end times to a pair of sample indices.
     */
    fun physicalRangeToIndices(startUs: Double, endUs: Double): Pair<Int, Int> {
        val s = timeUsToSampleIndex(startUs)
        val e = timeUsToSampleIndex(endUs).coerceAtLeast(s + 1)
        return Pair(s, e)
    }

    /**
     * Map a sample index back to the ETS physical pulse and slot.
     * index = pulseIndex + sampleSlot * pulsesPerFrame
     */
    fun getEtsPulseAndSlot(index: Int): Pair<Int, Int> {
        val pulseIndex = index % pulsesPerFrame
        val sampleSlot = index / pulsesPerFrame
        return Pair(pulseIndex, sampleSlot)
    }
}
