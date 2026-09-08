package com.example.felezjoo.dsp

import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.models.TargetClassification
import kotlin.math.abs
import kotlin.math.min

/**
 * Result of ground adaptation evaluation for a frame.
 */
data class GroundAdaptationStatus(
    val isFrozen: Boolean,
    val freezeReason: String,
    val quietFramesCount: Int,
    val maxDeltaApplied: Double
)

/**
 * Centralized, multi-zone safe ground tracker.
 * Mathematically:
 * X(t) = RAW(t) - AIR(t)
 * RESIDUAL(t) = X(t) - GROUND(t)
 *
 * Updates GROUND(t) from X(t) only when confident that the frame is pure soil
 * and does not contain a metal target, transient saturation, or unstable coil recovery.
 */
class SafeGroundTracker {

    var quietFramesCount: Int = 0
        private set

    var isFrozen: Boolean = false
        private set

    var lastFreezeReason: String = "INITIAL"
        private set

    fun reset() {
        quietFramesCount = 0
        isFrozen = false
        lastFreezeReason = "RESET"
    }

    /**
     * Evaluates whether ground adaptation should be frozen or updated for the current frame.
     * When [applyUpdate] is true, mutates [groundCurve] in-place.
     * When [applyUpdate] is false, calculates status with zero mutation.
     */
    fun processFrame(
        groundCurve: DoubleArray,
        airCompensated: DoubleArray, // X(t) = RAW(t) - AIR(t)
        targetScore: Double,
        snr: Double,
        persistence: Double,
        classification: TargetClassification,
        noiseFloor: Double,
        peakSignal: Double,
        isSaturated: Boolean,
        profile: DspProfile,
        applyUpdate: Boolean
    ): GroundAdaptationStatus {
        val size = groundCurve.size

        val gConfig = profile.groundConfig

        // 1. Check freeze conditions
        var frozen = false
        var reason = "ADAPTING"

        if (!gConfig.enabled || gConfig.alpha <= 0.0) {
            frozen = true
            reason = "GROUND TRACKING OFF"
        } else if (isSaturated) {
            frozen = true
            reason = "ADC SATURATED"
        } else if (targetScore >= gConfig.freezeScore) {
            frozen = true
            reason = "TARGET SCORE HIGH (%.1f)".format(targetScore)
        } else if (snr >= gConfig.freezeSnr) {
            frozen = true
            reason = "SNR HIGH (%.1f)".format(snr)
        } else if (persistence >= gConfig.freezePersistence) {
            frozen = true
            reason = "TARGET PERSISTENCE (%.1f%%)".format(persistence)
        } else if (classification == TargetClassification.FERROUS_LIKELY ||
            classification == TargetClassification.NON_FERROUS_LIKELY ||
            classification == TargetClassification.IRON ||
            classification == TargetClassification.NON_FERROUS ||
            classification == TargetClassification.STABLE_TARGET
        ) {
            frozen = true
            reason = "TARGET DETECTED (${classification.label})"
        } else if (peakSignal > (kotlin.math.max(noiseFloor, 1.0) * gConfig.freezePeakNoiseMultiplier)) {
            frozen = true
            reason = "PEAK SIGNAL EXCEEDS GROUND NOISE"
        }

        var newQuietFrames = quietFramesCount
        if (frozen) {
            newQuietFrames = 0
        } else {
            newQuietFrames++
            // Require configured consecutive quiet frames before unfreezing
            if (newQuietFrames < gConfig.quietFramesRequired) {
                frozen = true
                reason = "AWAITING QUIET FRAMES ($newQuietFrames/${gConfig.quietFramesRequired})"
            }
        }

        var maxDelta = 0.0

        if (!frozen && applyUpdate) {
            val baseAlpha = gConfig.alpha.coerceIn(0.0, 0.2)
            val earlyMult = gConfig.earlyMultiplier
            val lateMult = gConfig.lateMultiplier
            val maxStep = gConfig.maxStep

            val earlyCutoff = (size * 0.30).toInt().coerceIn(1, size)

            for (i in 0 until size) {
                // Zone-dependent alpha: early region adapts much slower to preserve early target transient
                val zoneAlpha = if (i < earlyCutoff) {
                    val progress = i.toDouble() / earlyCutoff
                    baseAlpha * (earlyMult + progress * (lateMult - earlyMult))
                } else {
                    baseAlpha * lateMult
                }

                // Error between air-compensated signal X(t) and current GROUND(t)
                val error = airCompensated[i] - groundCurve[i]
                val step = (zoneAlpha * error).coerceIn(-maxStep, maxStep)
                groundCurve[i] += step
                if (abs(step) > maxDelta) maxDelta = abs(step)
            }
        }

        if (applyUpdate) {
            isFrozen = frozen
            lastFreezeReason = reason
            quietFramesCount = newQuietFrames
        }

        return GroundAdaptationStatus(
            isFrozen = frozen,
            freezeReason = reason,
            quietFramesCount = if (applyUpdate) quietFramesCount else newQuietFrames,
            maxDeltaApplied = maxDelta
        )
    }
}
