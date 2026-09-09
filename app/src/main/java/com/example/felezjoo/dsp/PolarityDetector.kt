package com.example.felezjoo.dsp

import com.example.felezjoo.models.DetectionCalibration
import com.example.felezjoo.models.PolarityDetectionQuality
import com.example.felezjoo.models.PolarityDetectionResult
import com.example.felezjoo.models.WaveformPolarity
import kotlin.math.abs
import kotlin.math.max

/**
 * Objective detector for Analog Front End (AFE) waveform polarity.
 *
 * In PI detectors:
 * - A non-inverting AFE produces a positive residual above baseline/ground (residual > 0).
 * - An inverting AFE produces a negative residual below baseline/ground (residual < 0).
 *
 * Crucial physical principles:
 * 1. The ADC itself never reads negative voltages (< 0V). "Negative" means residual = ADC - baseline < 0.
 * 2. Never relies on simple average (average > 0) alone.
 * 3. Never relies on Target ID, VDI, iron score, or metal classification.
 * 4. Strictly tests both hypothesis candidates (positive and negative candidate waveforms) over the physical
 *    decay integration window after baseline and ground subtraction.
 * 5. Evaluates candidate validity through:
 *    - Number of valid samples above noise threshold.
 *    - Monotonic exponential decay fit quality (R^2).
 *    - Energy and integrated deflection.
 * 6. Returns UNKNOWN when data is noisy or insufficient, never guessing or flipping randomly.
 */
object PolarityDetector {

    /**
     * Analyzes a signed residual curve (after air baseline and ground subtraction)
     * to determine whether the AFE is POSITIVE (standard) or NEGATIVE (inverting).
     *
     * @param rawResidual Curve = RAW - BASELINE - GROUND
     * @param dt Physical sampling interval in microseconds
     * @param startIndex Start index of physical decay window
     * @param endIndex End index of physical decay window
     * @param noiseFloor Estimated noise floor (ADC units)
     * @param calibration Detection calibration parameters
     */
    fun detectPolarity(
        rawResidual: DoubleArray,
        dt: Double,
        startIndex: Int,
        endIndex: Int,
        noiseFloor: Double,
        calibration: DetectionCalibration = DetectionCalibration()
    ): PolarityDetectionResult {
        val totalCount = rawResidual.size
        val start = startIndex.coerceIn(0, totalCount)
        val end = endIndex.coerceIn(start, totalCount)
        val windowLen = end - start

        // Need at least 4 samples in decay window for statistical regression
        if (windowLen < 4 || dt <= 0.0) {
            return PolarityDetectionResult.UNKNOWN.copy(reason = "Decay window too short (< 4 samples)")
        }

        val noiseThreshold = max(noiseFloor * calibration.noiseThresholdMultiplier, 1.5)

        // Candidate 1: POSITIVE hypothesis (AFE non-inverting, positive residual represents decay)
        val posCandidate = DoubleArray(windowLen) { i -> rawResidual[start + i] }
        val posFit = TauEstimator.estimateTau(
            waveform = posCandidate,
            sampleSpacingUs = dt,
            startIndex = 0,
            endIndex = windowLen,
            noiseFloor = noiseFloor,
            calibration = calibration,
            minSamples = 4
        )

        // Candidate 2: NEGATIVE hypothesis (AFE inverting, inverted residual represents decay)
        val negCandidate = DoubleArray(windowLen) { i -> -rawResidual[start + i] }
        val negFit = TauEstimator.estimateTau(
            waveform = negCandidate,
            sampleSpacingUs = dt,
            startIndex = 0,
            endIndex = windowLen,
            noiseFloor = noiseFloor,
            calibration = calibration,
            minSamples = 4
        )

        // Calculate candidate metrics: valid samples above threshold & integrated energy
        var posValidCount = 0
        var negValidCount = 0
        var posEnergy = 0.0
        var negEnergy = 0.0
        var posMax = 0.0
        var negMax = 0.0

        for (i in 0 until windowLen) {
            val vPos = posCandidate[i]
            if (vPos > noiseThreshold) {
                posValidCount++
                posEnergy += vPos * vPos * dt
                if (vPos > posMax) posMax = vPos
            }

            val vNeg = negCandidate[i]
            if (vNeg > noiseThreshold) {
                negValidCount++
                negEnergy += vNeg * vNeg * dt
                if (vNeg > negMax) negMax = vNeg
            }
        }

        // Composite scoring for each candidate:
        // Fit quality (R^2), valid sample coverage, and signal amplitude
        val posR2 = if (posFit.isAvailable) posFit.rSquared else 0.0
        val negR2 = if (negFit.isAvailable) negFit.rSquared else 0.0

        val minUsableSamples = max(4, windowLen / 4)
        val hasPosSignal = posValidCount >= minUsableSamples && posMax >= (noiseThreshold * 1.5)
        val hasNegSignal = negValidCount >= minUsableSamples && negMax >= (noiseThreshold * 1.5)

        // If neither candidate has significant signal above noise floor, it is noise only -> UNKNOWN
        if (!hasPosSignal && !hasNegSignal) {
            return PolarityDetectionResult(
                detectedPolarity = null,
                quality = PolarityDetectionQuality.NONE,
                positiveScore = 0.0,
                negativeScore = 0.0,
                positiveR2 = posR2,
                negativeR2 = negR2,
                positiveValidSamples = posValidCount,
                negativeValidSamples = negValidCount,
                reason = "No candidate exceeds noise threshold (posMax=%.1f, negMax=%.1f, threshold=%.1f)"
                    .format(posMax, negMax, noiseThreshold)
            )
        }

        // Compute candidate scores
        val posScore = (if (posFit.isAvailable) posR2 * 60.0 else 0.0) +
                (posValidCount.toDouble() / windowLen) * 25.0 +
                (posMax / (posMax + negMax + 1.0)) * 15.0

        val negScore = (if (negFit.isAvailable) negR2 * 60.0 else 0.0) +
                (negValidCount.toDouble() / windowLen) * 25.0 +
                (negMax / (posMax + negMax + 1.0)) * 15.0

        // Decision logic
        return when {
            posFit.isAvailable && !negFit.isAvailable -> {
                val q = if (posR2 >= 0.85 && posValidCount >= minUsableSamples * 2) {
                    PolarityDetectionQuality.HIGH
                } else {
                    PolarityDetectionQuality.MEDIUM
                }
                PolarityDetectionResult(
                    detectedPolarity = WaveformPolarity.POSITIVE,
                    quality = q,
                    positiveScore = posScore,
                    negativeScore = negScore,
                    positiveR2 = posR2,
                    negativeR2 = negR2,
                    positiveValidSamples = posValidCount,
                    negativeValidSamples = negValidCount,
                    reason = "Positive decay valid (R²=%.2f, Tau=%.1f µs), negative fit failed"
                        .format(posR2, posFit.tauUs)
                )
            }
            negFit.isAvailable && !posFit.isAvailable -> {
                val q = if (negR2 >= 0.85 && negValidCount >= minUsableSamples * 2) {
                    PolarityDetectionQuality.HIGH
                } else {
                    PolarityDetectionQuality.MEDIUM
                }
                PolarityDetectionResult(
                    detectedPolarity = WaveformPolarity.NEGATIVE,
                    quality = q,
                    positiveScore = posScore,
                    negativeScore = negScore,
                    positiveR2 = posR2,
                    negativeR2 = negR2,
                    positiveValidSamples = posValidCount,
                    negativeValidSamples = negValidCount,
                    reason = "Negative/Inverted decay valid (R²=%.2f, Tau=%.1f µs), positive fit failed"
                        .format(negR2, negFit.tauUs)
                )
            }
            posFit.isAvailable && negFit.isAvailable -> {
                // Both fitted; compare scores and R^2
                val scoreDiff = posScore - negScore
                when {
                    scoreDiff > 15.0 && posR2 > negR2 + 0.1 -> {
                        PolarityDetectionResult(
                            detectedPolarity = WaveformPolarity.POSITIVE,
                            quality = if (posR2 >= 0.85) PolarityDetectionQuality.HIGH else PolarityDetectionQuality.MEDIUM,
                            positiveScore = posScore,
                            negativeScore = negScore,
                            positiveR2 = posR2,
                            negativeR2 = negR2,
                            positiveValidSamples = posValidCount,
                            negativeValidSamples = negValidCount,
                            reason = "Positive fit clearly superior (R²: %.2f vs %.2f)"
                                .format(posR2, negR2)
                        )
                    }
                    scoreDiff < -15.0 && negR2 > posR2 + 0.1 -> {
                        PolarityDetectionResult(
                            detectedPolarity = WaveformPolarity.NEGATIVE,
                            quality = if (negR2 >= 0.85) PolarityDetectionQuality.HIGH else PolarityDetectionQuality.MEDIUM,
                            positiveScore = posScore,
                            negativeScore = negScore,
                            positiveR2 = posR2,
                            negativeR2 = negR2,
                            positiveValidSamples = posValidCount,
                            negativeValidSamples = negValidCount,
                            reason = "Negative fit clearly superior (R²: %.2f vs %.2f)"
                                .format(negR2, posR2)
                        )
                    }
                    else -> {
                        PolarityDetectionResult(
                            detectedPolarity = null,
                            quality = PolarityDetectionQuality.LOW,
                            positiveScore = posScore,
                            negativeScore = negScore,
                            positiveR2 = posR2,
                            negativeR2 = negR2,
                            positiveValidSamples = posValidCount,
                            negativeValidSamples = negValidCount,
                            reason = "Ambiguous decay response between positive and negative candidates"
                        )
                    }
                }
            }
            else -> {
                // Neither fitted exponential model cleanly. Check if one side has strong dominant deflection.
                val ratio = (posMax + 0.1) / (negMax + 0.1)
                if (hasPosSignal && ratio > 3.0 && posValidCount >= minUsableSamples * 2) {
                    PolarityDetectionResult(
                        detectedPolarity = WaveformPolarity.POSITIVE,
                        quality = PolarityDetectionQuality.MEDIUM,
                        positiveScore = posScore,
                        negativeScore = negScore,
                        positiveR2 = posR2,
                        negativeR2 = negR2,
                        positiveValidSamples = posValidCount,
                        negativeValidSamples = negValidCount,
                        reason = "Dominant positive amplitude deflection above noise floor"
                    )
                } else if (hasNegSignal && ratio < 0.33 && negValidCount >= minUsableSamples * 2) {
                    PolarityDetectionResult(
                        detectedPolarity = WaveformPolarity.NEGATIVE,
                        quality = PolarityDetectionQuality.MEDIUM,
                        positiveScore = posScore,
                        negativeScore = negScore,
                        positiveR2 = posR2,
                        negativeR2 = negR2,
                        positiveValidSamples = posValidCount,
                        negativeValidSamples = negValidCount,
                        reason = "Dominant negative amplitude deflection below noise floor"
                    )
                } else {
                    PolarityDetectionResult(
                        detectedPolarity = null,
                        quality = PolarityDetectionQuality.NONE,
                        positiveScore = posScore,
                        negativeScore = negScore,
                        positiveR2 = posR2,
                        negativeR2 = negR2,
                        positiveValidSamples = posValidCount,
                        negativeValidSamples = negValidCount,
                        reason = "Data insufficient or decay does not match exponential profile"
                    )
                }
            }
        }
    }
}
