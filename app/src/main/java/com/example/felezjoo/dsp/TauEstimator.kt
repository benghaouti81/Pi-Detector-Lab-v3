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
     * @param waveform Normalized residual decay waveform (positive values represent target decay)
     * @param sampleSpacingUs Actual time step between consecutive samples in microseconds
     * @param startIndex Start sample index for regression window
     * @param endIndex End sample index for regression window (exclusive)
     * @param noiseFloor Estimated noise floor (samples below 2 * noiseFloor are excluded)
     * @param minSamples Minimum number of valid samples required for regression (default 4)
     * @param minR2 Minimum R^2 to consider the decay exponential (default 0.60)
     */
    fun estimateTau(
        waveform: DoubleArray,
        sampleSpacingUs: Double,
        startIndex: Int,
        endIndex: Int,
        noiseFloor: Double,
        minSamples: Int = 4,
        minR2: Double = 0.60
    ): TauFitResult {
        if (waveform.isEmpty() || sampleSpacingUs <= 0.0) {
            return TauFitResult.UNAVAILABLE
        }

        val sIdx = startIndex.coerceIn(0, waveform.size - 1)
        val eIdx = endIndex.coerceIn(sIdx + 1, waveform.size)

        val threshold = (noiseFloor * 2.0).coerceAtLeast(1.0)
        val times = mutableListOf<Double>()
        val logVals = mutableListOf<Double>()

        for (i in sIdx until eIdx) {
            val v = waveform[i]
            if (v > threshold) {
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
            return TauFitResult(Double.NaN, 0.0, 0.0, n, times.first(), times.last(), false)
        }

        val slope = ssTLogV / ssTt
        val intercept = meanLogV - slope * meanT

        // In a true physical decay: V(t) = A * exp(-t / tau)
        // ln(V(t)) = ln(A) - t / tau
        // Therefore, slope = -1 / tau. Slope MUST be strictly negative.
        if (slope >= -1e-6) {
            // Signal is not decaying (either flat or rising)
            return TauFitResult(Double.NaN, 0.0, 0.0, n, times.first(), times.last(), false)
        }

        val tau = -1.0 / slope

        // Compute residuals, R^2, and RMSE
        var ssRes = 0.0
        for (i in 0 until n) {
            val predictedLog = intercept + slope * times[i]
            val res = logVals[i] - predictedLog
            ssRes += res * res
        }

        val r2 = if (ssLogV > 1e-12) (1.0 - (ssRes / ssLogV)).coerceIn(0.0, 1.0) else 0.0
        val rmse = sqrt(ssRes / n)

        val isValid = (r2 >= minR2) && (tau in 0.5..500.0)

        return TauFitResult(
            tauUs = if (isValid) tau else Double.NaN,
            rSquared = r2,
            fitError = rmse,
            fitSampleCount = n,
            fitStartUs = times.first(),
            fitEndUs = times.last(),
            isAvailable = isValid
        )
    }
}
