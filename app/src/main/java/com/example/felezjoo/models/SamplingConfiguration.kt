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
    val delayUs: Double = 10.0
) : Serializable {

    val maxAdcValue: Int
        get() = (1 shl adcResolution) - 1

    /**
     * Equivalent time in microseconds for a given sample index.
     */
    fun sampleTimeUs(index: Int): Double {
        return index * sampleSpacingUs
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
