package com.example.felezjoo.models

import java.io.Serializable

/**
 * Polarity of the analog front end (AFE) detector signal.
 * In a standard AFE, eddy current decays produce positive ADC values above baseline.
 * In an inverting AFE, eddy current decays deflect downward toward zero or negative.
 */
enum class WaveformPolarity(val displayName: String, val sign: Double) : Serializable {
    POSITIVE("Positive (Standard)", 1.0),
    NEGATIVE("Negative (Inverted AFE)", -1.0)
}
