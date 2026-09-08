package com.example.felezjoo.models

import java.io.Serializable

enum class TargetClassification(val label: String) {
    NO_TARGET("NO TARGET"),
    POSSIBLE_TARGET("POSSIBLE TARGET"),
    FERROUS_LIKELY("FERROUS LIKELY"),
    NON_FERROUS_LIKELY("NON-FERROUS LIKELY"),
    UNKNOWN("UNKNOWN"),
    // Aliases for backward compatibility
    STABLE_TARGET("STABLE TARGET"),
    IRON("IRON"),
    NON_FERROUS("NON-FERROUS"),
    UNCERTAIN("UNCERTAIN")
}

enum class DatasetLabel(val displayName: String) {
    NO_TARGET("No Target"),
    GROUND_ONLY("Ground Only"),
    IRON("Iron"),
    STEEL("Steel"),
    COPPER("Copper"),
    ALUMINUM("Aluminum"),
    BRASS("Brass"),
    GOLD("Gold"),
    SILVER("Silver"),
    OTHER_METAL("Other Metal"),
    UNKNOWN("Unknown")
}

data class FeatureVector(
    val amplitude: Double = 0.0,
    val peak: Double = 0.0,
    val peakNormalized: Double = 0.0,
    val peakSigned: Double = 0.0,
    val peakAbsolute: Double = 0.0,
    val peakIndex: Int = 0,
    val peakTimeUs: Double = 0.0,
    val minimum: Double = 0.0,
    val maximum: Double = 0.0,
    val range: Double = 0.0,
    val mean: Double = 0.0,            // Signed mean: sum(r) / count
    val meanAbsolute: Double = 0.0,    // Absolute mean: sum(|r|) / count
    val rms: Double = 0.0,
    val noise: Double = 0.0,
    val snr: Double = 0.0,
    val area: Double = 0.0,
    val integral: Double = 0.0,
    val a: Double = 0.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val aMinusB: Double = 0.0,
    val bMinusC: Double = 0.0,
    val aMinusC: Double = 0.0,
    val aDivB: Double = 0.0,
    val bDivC: Double = 0.0,
    val aDivC: Double = 0.0,
    val slopeA: Double = 0.0,
    val slopeB: Double = 0.0,
    val slopeC: Double = 0.0,
    val curvature: Double = 0.0,
    val earlyLateRatio: Double = 0.0,
    val residualPeak: Double = 0.0,
    val residualArea: Double = 0.0,
    val groundDifference: Double = 0.0,
    val persistence: Double = 0.0,
    val stability: Double = 0.0,
    val targetScore: Double = 0.0,
    val targetConfidence: Double = 0.0,
    val ironScore: Double = 0.0,
    val targetId: Int = 0, // 0 = UNAVAILABLE / NOT CALIBRATED
    val isTargetIdCalibrated: Boolean = false,
    val classification: TargetClassification = TargetClassification.NO_TARGET,
    val noiseMad: Double = 0.0,
    val areaNorm: Double = 0.0,
    val slope: Double = 0.0,
    val estimatedTauUs: Double = 0.0,
    val earlyTauUs: Double = 0.0,
    val lateTauUs: Double = 0.0,
    val tauRatio: Double = 0.0,
    val tauFitR2: Double = 0.0,
    val tauFitError: Double = 0.0,
    val tauFitSampleCount: Int = 0,
    val isTauValid: Boolean = false,
    val tauFitStartUs: Double = 0.0,
    val tauFitEndUs: Double = 0.0,
    val energyA: Double = 0.0,
    val energyB: Double = 0.0,
    val energyC: Double = 0.0,
    val integralA: Double = 0.0,
    val integralB: Double = 0.0,
    val integralC: Double = 0.0,
    val isGroundFrozen: Boolean = false,
    val groundFreezeReason: String = "",
    val dtUs: Double = 0.0,
    val adcResolution: Int = 10,
    val dspVersion: String = "DSP-3.0-PHYS"
) : Serializable

data class TargetEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startTimeMs: Long = System.currentTimeMillis(),
    var endTimeMs: Long = System.currentTimeMillis(),
    val peakScore: Double = 0.0,
    val peakConfidence: Double = 0.0,
    val ironScore: Double = 0.0,
    val targetId: Int = 0,
    val classification: TargetClassification = TargetClassification.NO_TARGET,
    val bestFeatureVector: FeatureVector? = null,
    val blockCount: Int = 1,
    val notes: String = ""
) : Serializable
