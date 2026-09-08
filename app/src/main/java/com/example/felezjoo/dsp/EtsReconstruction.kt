package com.example.felezjoo.dsp

import com.example.felezjoo.models.SamplingConfiguration

/**
 * Defines the ordering of samples in raw USB transport packets.
 */
enum class EtsTransportOrder {
    /**
     * Samples in transport are already ordered chronologically:
     * Index i represents time t = i * dt.
     */
    CHRONOLOGICAL,

    /**
     * Samples in transport are grouped by physical pulse:
     * [P0S0, P0S1, ... P0S(S-1), P1S0, P1S1, ... P(P-1)S(S-1)]
     * where transportIndex = pulseIndex * samplesPerPulse + sampleSlot.
     */
    PULSE_FIRST
}

/**
 * Authoritative Equivalent Time Sampling (ETS) reconstruction layer.
 * Re-orders transport-ordered pulse samples into strict chronological order.
 *
 * Chronological mapping formula:
 * reconstructedIndex = pulseIndex + sampleSlot * pulsesPerFrame
 */
object EtsReconstruction {

    /**
     * Reconstructs raw transport samples into chronological order.
     * Guaranteed to operate only if dimensions match (pulses * samplesPerPulse == total).
     */
    fun toChronological(
        rawTransport: IntArray,
        config: SamplingConfiguration,
        order: EtsTransportOrder = config.transportOrder
    ): IntArray {
        if (order == EtsTransportOrder.CHRONOLOGICAL) {
            return rawTransport.clone()
        }

        val pulses = config.pulsesPerFrame
        val slots = config.samplesPerPulse
        val totalExpected = pulses * slots

        if (rawTransport.size != totalExpected || pulses <= 0 || slots <= 0) {
            // If dimensions do not match ETS configuration, return clone safely
            return rawTransport.clone()
        }

        val chronological = IntArray(totalExpected)
        for (p in 0 until pulses) {
            for (s in 0 until slots) {
                val transportIndex = p * slots + s
                val reconstructedIndex = p + s * pulses
                chronological[reconstructedIndex] = rawTransport[transportIndex]
            }
        }
        return chronological
    }

    /**
     * Converts a chronological waveform back to pulse-first transport order.
     * Useful for hardware simulators and test verification.
     */
    fun toPulseFirst(
        chronological: IntArray,
        config: SamplingConfiguration
    ): IntArray {
        val pulses = config.pulsesPerFrame
        val slots = config.samplesPerPulse
        val totalExpected = pulses * slots

        if (chronological.size != totalExpected || pulses <= 0 || slots <= 0) {
            return chronological.clone()
        }

        val transport = IntArray(totalExpected)
        for (p in 0 until pulses) {
            for (s in 0 until slots) {
                val transportIndex = p * slots + s
                val reconstructedIndex = p + s * pulses
                transport[transportIndex] = chronological[reconstructedIndex]
            }
        }
        return transport
    }

    /**
     * Returns the physical pulse index and sample slot for a reconstructed chronological sample.
     */
    fun getPulseAndSlot(chronologicalIndex: Int, config: SamplingConfiguration): Pair<Int, Int> {
        val pulses = config.pulsesPerFrame.coerceAtLeast(1)
        val pulseIndex = chronologicalIndex % pulses
        val sampleSlot = chronologicalIndex / pulses
        return Pair(pulseIndex, sampleSlot)
    }

    /**
     * Returns the chronological sample index for a given pulse index and sample slot.
     */
    fun getChronologicalIndex(pulseIndex: Int, sampleSlot: Int, config: SamplingConfiguration): Int {
        return pulseIndex + (sampleSlot * config.pulsesPerFrame)
    }
}
