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

enum class GroundSpeed(val displayName: String, val defaultAlpha: Double) {
    OFF("Off", 0.0),
    SLOW("Slow Tracking", 0.005),
    NORMAL("Normal", 0.02),
    FAST("Fast Tracking", 0.08),
    CUSTOM("Custom", 0.02);

    val alpha: Double get() = defaultAlpha
}

/**
 * Single source of truth for Ground Tracking configuration.
 * Eliminates discrepancies between GroundSpeed and groundAlpha.
 */
data class GroundTrackingConfig(
    val enabled: Boolean = true,
    val speed: GroundSpeed = GroundSpeed.NORMAL,
    val alpha: Double = 0.02,
    val earlyMultiplier: Double = 0.2,
    val lateMultiplier: Double = 1.0,
    val maxStep: Double = 4.0,
    val freezeScore: Double = 28.0,
    val freezeSnr: Double = 3.5,
    val freezePersistence: Double = 40.0,
    val freezePeakNoiseMultiplier: Double = 2.8,
    val quietFramesRequired: Int = 2
) : Serializable

/**
 * Explicit algorithmic calibration and heuristic scaling references.
 * Separates heuristic scoring constants from hardware physics.
 */
data class DetectionCalibration(
    // HEURISTIC: Fraction of full-scale ADC for target signal normalization (0..100)
    val signalFractionRef: Double = 0.15,
    // HEURISTIC: SNR value corresponding to 100% SNR term score
    val snrReference: Double = 20.0,
    // HEURISTIC: A/B ratio corresponding to 100% shape term score
    val shapeRatioReference: Double = 4.0,
    // HEURISTIC: Scaling denominator for confidence SNR term
    val confidenceSnrScale: Double = 12.0,
    // HEURISTIC: Divisor for noise floor health term in confidence
    val noiseHealthScale: Double = 8.0,
    // Fractional point along decay where noise tail begins (e.g. 70% of samples)
    val tailNoiseFraction: Double = 0.70,
    // Noise multiplier threshold for sample inclusion in Tau estimation
    val noiseThresholdMultiplier: Double = 2.0,
    // Minimum physical tau in microseconds (eddy currents in metals: 1us .. 300us)
    val minTauUs: Double = 1.0,
    // Maximum physical tau in microseconds
    val maxTauUs: Double = 300.0,
    // Minimum R^2 fit quality for valid exponential decay
    val minTauR2: Double = 0.65
) : Serializable

data class DspProfile(
    val id: String = "profile_stable",
    val name: String = "Stable",
    val schemaVersion: Int = 3,
    val isBuiltIn: Boolean = true,
    val filterType: FilterType = FilterType.MOVING_AVERAGE_3,
    val integrationMode: IntegrationMode = IntegrationMode.RECTANGULAR,
    // Physical time windows in microseconds (relative to acquisition start: t = index * dt)
    // Physical time is the SINGLE SOURCE OF TRUTH.
    val integrationStartUs: Double = 12.8,
    val integrationEndUs: Double = 48.0,
    val regionAStartUs: Double = 8.0,
    val regionAEndUs: Double = 24.0,
    val regionBStartUs: Double = 24.0,
    val regionBEndUs: Double = 51.2,
    val regionCStartUs: Double = 51.2,
    val regionCEndUs: Double = 96.0,
    // Single source of truth for Ground Adaptation
    val groundConfig: GroundTrackingConfig = GroundTrackingConfig(alpha = 0.02),
    // Configurable Target Score Weights w1..w5 (sum = 1.0)
    val weightSignal: Double = 0.25,
    val weightSnr: Double = 0.25,
    val weightArea: Double = 0.20,
    val weightShape: Double = 0.15,
    val weightPersistence: Double = 0.15,
    // Detection Thresholds
    val targetThreshold: Double = 35.0,
    val confidenceThreshold: Double = 45.0,
    val audioThreshold: Double = 25.0,
    val ironRejectThreshold: Double = 60.0,
    // Algorithmic and heuristic calibration reference
    val calibration: DetectionCalibration = DetectionCalibration(),
    val dspVersion: String = "DSP-3.0-PHYS"
) : Serializable {

    fun getIntegrationIndices(config: SamplingConfiguration): Pair<Int, Int> =
        config.physicalRangeToIndices(integrationStartUs, integrationEndUs)

    fun getRegionAIndices(config: SamplingConfiguration): Pair<Int, Int> =
        config.physicalRangeToIndices(regionAStartUs, regionAEndUs)

    fun getRegionBIndices(config: SamplingConfiguration): Pair<Int, Int> =
        config.physicalRangeToIndices(regionBStartUs, regionBEndUs)

    fun getRegionCIndices(config: SamplingConfiguration): Pair<Int, Int> =
        config.physicalRangeToIndices(regionCStartUs, regionCEndUs)

    // Derived sample-index helpers (evaluated from physical time and active sampling configuration)
    fun integrationStartSample(config: SamplingConfiguration): Int =
        getIntegrationIndices(config).first

    fun integrationEndSample(config: SamplingConfiguration): Int =
        getIntegrationIndices(config).second

    fun integrationStartSample(spacingUs: Double): Int =
        if (spacingUs > 0.0) (integrationStartUs / spacingUs).toInt() else 0

    fun integrationEndSample(spacingUs: Double): Int =
        if (spacingUs > 0.0) (integrationEndUs / spacingUs).toInt() else 0

    // Backward-compatibility properties (derived from physical time and groundConfig)
    val groundAlpha: Double get() = groundConfig.alpha
    val groundSpeed: GroundSpeed get() = groundConfig.speed
    val groundTrackingSpeed: GroundSpeed get() = groundSpeed

    val groundFreezeScore: Double get() = groundConfig.freezeScore
    val groundFreezeSnr: Double get() = groundConfig.freezeSnr
    val groundFreezePersistence: Double get() = groundConfig.freezePersistence
    val groundEarlyAlphaMultiplier: Double get() = groundConfig.earlyMultiplier
    val groundLateAlphaMultiplier: Double get() = groundConfig.lateMultiplier
    val groundMaxStepPerFrame: Double get() = groundConfig.maxStep

    val targetScoreThreshold: Double get() = targetThreshold
    val wSignal: Double get() = weightSignal
    val wSnr: Double get() = weightSnr
    val wArea: Double get() = weightArea
    val wShape: Double get() = weightShape
    val wPersistence: Double get() = weightPersistence

    val aStartUs: Double get() = regionAStartUs
    val aEndUs: Double get() = regionAEndUs
    val bStartUs: Double get() = regionBStartUs
    val bEndUs: Double get() = regionBEndUs
    val cStartUs: Double get() = regionCStartUs
    val cEndUs: Double get() = regionCEndUs

    fun copyWithGroundSpeed(speed: GroundSpeed): DspProfile {
        return copy(
            groundConfig = groundConfig.copy(
                enabled = (speed != GroundSpeed.OFF),
                speed = speed,
                alpha = speed.defaultAlpha
            )
        )
    }

    fun copyWithGroundAlpha(alpha: Double): DspProfile {
        val spd = when {
            alpha <= 0.0 -> GroundSpeed.OFF
            kotlin.math.abs(alpha - GroundSpeed.SLOW.defaultAlpha) < 1e-4 -> GroundSpeed.SLOW
            kotlin.math.abs(alpha - GroundSpeed.NORMAL.defaultAlpha) < 1e-4 -> GroundSpeed.NORMAL
            kotlin.math.abs(alpha - GroundSpeed.FAST.defaultAlpha) < 1e-4 -> GroundSpeed.FAST
            else -> GroundSpeed.CUSTOM
        }
        return copy(
            groundConfig = groundConfig.copy(
                enabled = alpha > 0.0,
                speed = spd,
                alpha = alpha
            )
        )
    }

    companion object {
        val ORIGINAL_LIKE = DspProfile(
            id = "profile_original_like",
            name = "Original-Like",
            filterType = FilterType.NONE,
            integrationStartUs = 9.6,
            integrationEndUs = 38.4,
            regionAStartUs = 8.0,
            regionAEndUs = 20.0,
            regionBStartUs = 20.0,
            regionBEndUs = 38.4,
            regionCStartUs = 38.4,
            regionCEndUs = 70.0,
            groundConfig = GroundTrackingConfig(alpha = 0.02),
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
            integrationStartUs = 12.8,
            integrationEndUs = 48.0,
            regionAStartUs = 8.0,
            regionAEndUs = 24.0,
            regionBStartUs = 24.0,
            regionBEndUs = 51.2,
            regionCStartUs = 51.2,
            regionCEndUs = 96.0,
            groundConfig = GroundTrackingConfig(alpha = 0.02)
        )

        val MAXIMUM_DEPTH = DspProfile(
            id = "profile_max_depth",
            name = "Maximum Depth",
            filterType = FilterType.EXPONENTIAL_IIR,
            integrationStartUs = 16.0,
            integrationEndUs = 60.8,
            regionAStartUs = 12.0,
            regionAEndUs = 28.0,
            regionBStartUs = 28.0,
            regionBEndUs = 60.8,
            regionCStartUs = 60.8,
            regionCEndUs = 105.0,
            groundConfig = GroundTrackingConfig(speed = GroundSpeed.SLOW, alpha = 0.005),
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
            integrationStartUs = 9.6,
            integrationEndUs = 32.0,
            regionAStartUs = 6.4,
            regionAEndUs = 16.0,
            regionBStartUs = 16.0,
            regionBEndUs = 32.0,
            regionCStartUs = 32.0,
            regionCEndUs = 60.0,
            groundConfig = GroundTrackingConfig(speed = GroundSpeed.FAST, alpha = 0.08),
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
            integrationStartUs = 19.2,
            integrationEndUs = 56.0,
            regionAStartUs = 16.0,
            regionAEndUs = 32.0,
            regionBStartUs = 32.0,
            regionBEndUs = 56.0,
            regionCStartUs = 56.0,
            regionCEndUs = 96.0,
            groundConfig = GroundTrackingConfig(speed = GroundSpeed.CUSTOM, alpha = 0.05, earlyMultiplier = 0.15, freezeScore = 32.0),
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
            integrationStartUs = 11.2,
            integrationEndUs = 44.8,
            regionAStartUs = 8.0,
            regionAEndUs = 22.0,
            regionBStartUs = 22.0,
            regionBEndUs = 44.8,
            regionCStartUs = 44.8,
            regionCEndUs = 85.0,
            groundConfig = GroundTrackingConfig(alpha = 0.02),
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
            integrationStartUs = 12.8,
            integrationEndUs = 54.4,
            regionAStartUs = 10.0,
            regionAEndUs = 25.0,
            regionBStartUs = 25.0,
            regionBEndUs = 54.4,
            regionCStartUs = 54.4,
            regionCEndUs = 95.0,
            groundConfig = GroundTrackingConfig(alpha = 0.02),
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
