package com.example.felezjoo.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Result of scientific linear regression fit on exponential decay:
 * V(t) = A * exp(-t / tau)  =>  ln(V(t)) = ln(A) - (1/tau) * t
 */
data class TauFitResult(
    val tauUs: Double,           // Estimated decay time constant in microseconds (NaN if unavailable)
    val rSquared: Double,        // Coefficient of determination R^2 in [0..1]
    val fitError: Double,        // Root mean square error (RMSE) of log fit
    val fitSampleCount: Int,     // Number of valid points used
    val fitStartUs: Double,      // Physical start time of regression window
    val fitEndUs: Double,        // Physical end time of regression window
    val isAvailable: Boolean     // True if fit is valid, decaying, and satisfies R^2 threshold
) {
    companion object {
        val UNAVAILABLE = TauFitResult(
            tauUs = Double.NaN,
            rSquared = 0.0,
            fitError = 0.0,
            fitSampleCount = 0,
            fitStartUs = 0.0,
            fitEndUs = 0.0,
            isAvailable = false
        )
    }
}

/**
 * Scientific Tau (decay time constant) estimator using multi-point log-linear regression.
 * Completely eliminates arbitrary slope constants and ungrounded formulas.
 */
object TauEstimator {

    /**
     * Estimates tau by performing linear regression of ln(V(t)) vs t.
     *
     * @param waveform Normalized residual decay waveform (measurement path, positive target deflection)
     * @param sampleSpacingUs Actual time step between consecutive samples in microseconds (from config)
     * @param startIndex Start sample index for regression window
     * @param endIndex End sample index for regression window (exclusive)
     * @param noiseFloor Estimated noise floor (samples below 2 * noiseFloor are excluded)
     * @param minSamples Minimum number of valid samples required for regression (default 4)
     * @param minR2 Minimum R^2 to consider the decay exponential (default 0.65)
     * @param saturationThreshold Maximum valid ADC value (samples >= saturationThreshold are excluded)
     * @param minTauUs Minimum physically plausible tau in microseconds (default 1.0)
     * @param maxTauUs Maximum physically plausible tau in microseconds (default 300.0)
     */
    fun estimateTau(
        waveform: DoubleArray,
        sampleSpacingUs: Double,
        startIndex: Int,
        endIndex: Int,
        noiseFloor: Double,
        minSamples: Int = 4,
        minR2: Double = 0.65,
        saturationThreshold: Double = Double.MAX_VALUE,
        minTauUs: Double = 1.0,
        maxTauUs: Double = 300.0,
        noiseThresholdMultiplier: Double = 2.0
    ): TauFitResult {
        if (waveform.isEmpty() || sampleSpacingUs <= 0.0) {
            return TauFitResult.UNAVAILABLE
        }

        val sIdx = startIndex.coerceIn(0, waveform.size - 1)
        val eIdx = endIndex.coerceIn(sIdx + 1, waveform.size)

        val threshold = (noiseFloor * noiseThresholdMultiplier).coerceAtLeast(1.0)
        val times = mutableListOf<Double>()
        val logVals = mutableListOf<Double>()

        for (i in sIdx until eIdx) {
            val v = waveform[i]
            // Exclude noise floor and saturated samples
            if (v > threshold && v < saturationThreshold) {
                val t = i * sampleSpacingUs
                times.add(t)
                logVals.add(ln(v))
            }
        }

        if (times.size < minSamples) {
            return TauFitResult(
                tauUs = Double.NaN,
                rSquared = 0.0,
                fitError = 0.0,
                fitSampleCount = times.size,
                fitStartUs = sIdx * sampleSpacingUs,
                fitEndUs = eIdx * sampleSpacingUs,
                isAvailable = false
            )
        }

        // Initial regression fit
        val firstFit = fitLogLinear(times, logVals)
        if (!firstFit.isValidSlope) {
            return TauFitResult(Double.NaN, 0.0, 0.0, times.size, times.first(), times.last(), false)
        }

        var finalTimes = times
        var finalLogVals = logVals
        var finalFit = firstFit

        // Robust Regression: If sufficient samples (>= 5), detect and reject isolated spike/glitch outlier
        if (times.size >= 5 && firstFit.rmse > 0.0) {
            var worstIdx = -1
            var maxAbsRes = 0.0
            for (i in times.indices) {
                val predicted = firstFit.intercept + firstFit.slope * times[i]
                val res = abs(logVals[i] - predicted)
                if (res > maxAbsRes) {
                    maxAbsRes = res
                    worstIdx = i
                }
            }

            // Outlier criterion: residual error exceeds 2.5 standard errors
            if (worstIdx != -1 && maxAbsRes > (2.5 * firstFit.rmse)) {
                val prunedTimes = mutableListOf<Double>()
                val prunedLogs = mutableListOf<Double>()
                for (i in times.indices) {
                    if (i != worstIdx) {
                        prunedTimes.add(times[i])
                        prunedLogs.add(logVals[i])
                    }
                }
                val reFit = fitLogLinear(prunedTimes, prunedLogs)
                if (reFit.isValidSlope && reFit.rSquared >= firstFit.rSquared) {
                    finalTimes = prunedTimes
                    finalLogVals = prunedLogs
                    finalFit = reFit
                }
            }
        }

        val tau = -1.0 / finalFit.slope
        val isValid = (finalFit.rSquared >= minR2) && (tau in minTauUs..maxTauUs)

        return TauFitResult(
            tauUs = if (isValid) tau else Double.NaN,
            rSquared = finalFit.rSquared,
            fitError = finalFit.rmse,
            fitSampleCount = finalTimes.size,
            fitStartUs = finalTimes.first(),
            fitEndUs = finalTimes.last(),
            isAvailable = isValid
        )
    }

    private data class IntermediateFit(
        val slope: Double,
        val intercept: Double,
        val rSquared: Double,
        val rmse: Double,
        val isValidSlope: Boolean
    )

    private fun fitLogLinear(times: List<Double>, logVals: List<Double>): IntermediateFit {
        val n = times.size
        val sumT = times.sum()
        val sumLogV = logVals.sum()
        val meanT = sumT / n
        val meanLogV = sumLogV / n

        var ssTt = 0.0
        var ssTLogV = 0.0
        var ssLogV = 0.0

        for (i in 0 until n) {
            val dt = times[i] - meanT
            val dLog = logVals[i] - meanLogV
            ssTt += dt * dt
            ssTLogV += dt * dLog
            ssLogV += dLog * dLog
        }

        if (ssTt <= 1e-12) {
            return IntermediateFit(0.0, 0.0, 0.0, 0.0, false)
        }

        val slope = ssTLogV / ssTt
        val intercept = meanLogV - slope * meanT

        // Physical decay slope must be negative: slope = -1/tau
        if (slope >= -1e-6) {
            return IntermediateFit(slope, intercept, 0.0, 0.0, false)
        }

        var ssRes = 0.0
        for (i in 0 until n) {
            val predictedLog = intercept + slope * times[i]
            val res = logVals[i] - predictedLog
            ssRes += res * res
        }

        val r2 = if (ssLogV > 1e-12) (1.0 - (ssRes / ssLogV)).coerceIn(0.0, 1.0) else 0.0
        val rmse = sqrt(ssRes / n)
        return IntermediateFit(slope, intercept, r2, rmse, true)
    }
}
