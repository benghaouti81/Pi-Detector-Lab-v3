package com.example.felezjoo.models

import java.io.Serializable

enum class FilterType(val displayName: String) {
    NONE("None (Pure Raw)"),
    MOVING_AVERAGE_3("Moving Average (3-pt)"),
    MOVING_AVERAGE_5("Moving Average (5-pt)"),
    MEDIAN_3("Median (3-pt)"),
    MEDIAN_5("Median (5-pt)"),
    EXPONENTIAL_IIR("Exponential IIR (α=0.3)"),
    SAVITZKY_GOLAY("Savitzky-Golay (5-pt)")
}

enum class IntegrationMode(val displayName: String) {
    RECTANGULAR("Rectangular"),
    TRIANGULAR("Triangular"),
    EXPONENTIAL("Exponential")
}

enum class GroundSpeed(val displayName: String, val alpha: Double) {
    OFF("Off", 0.0),
    SLOW("Slow Tracking", 0.005),
    NORMAL("Normal", 0.02),
    FAST("Fast Tracking", 0.08),
    CUSTOM("Custom", 0.02)
}

data class DspProfile(
    val id: String = "profile_stable",
    val name: String = "Stable",
    val schemaVersion: Int = 2,
    val isBuiltIn: Boolean = true,
    val filterType: FilterType = FilterType.MOVING_AVERAGE_3,
    val integrationMode: IntegrationMode = IntegrationMode.RECTANGULAR,
    val integrationStartSample: Int = 8,
    val integrationEndSample: Int = 30,
    val groundSpeed: GroundSpeed = GroundSpeed.NORMAL,
    val groundAlpha: Double = 0.02,
    val aStartSample: Int = 5,
    val aEndSample: Int = 15,
    val bStartSample: Int = 16,
    val bEndSample: Int = 32,
    val cStartSample: Int = 33,
    val cEndSample: Int = 60,
    // Configurable Target Score Weights w1..w5
    val weightSignal: Double = 0.25,
    val weightSnr: Double = 0.25,
    val weightArea: Double = 0.20,
    val weightShape: Double = 0.15,
    val weightPersistence: Double = 0.15,
    // Thresholds
    val targetThreshold: Double = 35.0,
    val confidenceThreshold: Double = 45.0,
    val audioThreshold: Double = 25.0,
    val ironRejectThreshold: Double = 60.0,
    val dspVersion: String = "DSP-2.0"
) : Serializable {

    val targetScoreThreshold: Double get() = targetThreshold
    val wSignal: Double get() = weightSignal
    val wSnr: Double get() = weightSnr
    val wArea: Double get() = weightArea
    val wShape: Double get() = weightShape
    val wPersistence: Double get() = weightPersistence
    val groundTrackingSpeed: GroundSpeed get() = groundSpeed

    companion object {
        val ORIGINAL_LIKE = DspProfile(
            id = "profile_original_like",
            name = "Original-Like",
            filterType = FilterType.NONE,
            integrationStartSample = 6,
            integrationEndSample = 24,
            groundSpeed = GroundSpeed.NORMAL,
            weightSignal = 0.35,
            weightSnr = 0.20,
            weightArea = 0.25,
            weightShape = 0.10,
            weightPersistence = 0.10
        )

        val STABLE = DspProfile(
            id = "profile_stable",
            name = "Stable",
            filterType = FilterType.MOVING_AVERAGE_3,
            integrationStartSample = 8,
            integrationEndSample = 30,
            groundSpeed = GroundSpeed.NORMAL
        )

        val MAXIMUM_DEPTH = DspProfile(
            id = "profile_max_depth",
            name = "Maximum Depth",
            filterType = FilterType.EXPONENTIAL_IIR,
            integrationStartSample = 10,
            integrationEndSample = 38,
            groundSpeed = GroundSpeed.SLOW,
            targetThreshold = 22.0,
            confidenceThreshold = 35.0,
            weightSignal = 0.20,
            weightSnr = 0.35,
            weightArea = 0.25,
            weightShape = 0.10,
            weightPersistence = 0.10
        )

        val FAST_RESPONSE = DspProfile(
            id = "profile_fast_response",
            name = "Fast Response",
            filterType = FilterType.NONE,
            integrationStartSample = 6,
            integrationEndSample = 20,
            groundSpeed = GroundSpeed.FAST,
            weightSignal = 0.40,
            weightSnr = 0.20,
            weightArea = 0.20,
            weightShape = 0.10,
            weightPersistence = 0.10
        )

        val MINERALIZED_GROUND = DspProfile(
            id = "profile_mineralized",
            name = "Mineralized Ground",
            filterType = FilterType.MEDIAN_3,
            integrationStartSample = 12,
            integrationEndSample = 35,
            groundSpeed = GroundSpeed.FAST,
            groundAlpha = 0.05,
            targetThreshold = 40.0,
            confidenceThreshold = 55.0,
            weightSignal = 0.15,
            weightSnr = 0.30,
            weightArea = 0.25,
            weightShape = 0.20,
            weightPersistence = 0.10
        )

        val EXPERIMENTAL_A = DspProfile(
            id = "profile_exp_a",
            name = "Experimental A (Curvature Focus)",
            filterType = FilterType.SAVITZKY_GOLAY,
            integrationStartSample = 7,
            integrationEndSample = 28,
            weightSignal = 0.20,
            weightSnr = 0.20,
            weightArea = 0.20,
            weightShape = 0.30,
            weightPersistence = 0.10
        )

        val EXPERIMENTAL_B = DspProfile(
            id = "profile_exp_b",
            name = "Experimental B (Multi-Zone Integration)",
            filterType = FilterType.MOVING_AVERAGE_5,
            integrationMode = IntegrationMode.TRIANGULAR,
            integrationStartSample = 8,
            integrationEndSample = 34,
            weightSignal = 0.20,
            weightSnr = 0.25,
            weightArea = 0.35,
            weightShape = 0.10,
            weightPersistence = 0.10
        )

        val BUILT_IN_PROFILES = listOf(
            STABLE,
            ORIGINAL_LIKE,
            MAXIMUM_DEPTH,
            FAST_RESPONSE,
            MINERALIZED_GROUND,
            EXPERIMENTAL_A,
            EXPERIMENTAL_B
        )
    }
}
