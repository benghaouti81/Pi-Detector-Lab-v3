package com.example.felezjoo.dsp

import kotlin.math.abs
import kotlin.math.sqrt

data class NoiseEstimate(
    val noiseRms: Double,        // Root-mean-square difference noise
    val noiseMad: Double,        // True Median Absolute Deviation (MAD) scaled by 1.4826 for Gaussian consistency
    val rawMad: Double,          // Raw Median Absolute Deviation without scaling
    val noiseFloor: Double,      // Effective noise floor for SNR and thresholding
    val sampleCountUsed: Int
)

object NoiseEstimator {

    /**
     * Computes true Median Absolute Deviation (MAD) and difference noise on late tail residuals.
     * For Gaussian noise: sigma = 1.4826 * median(|x_i - median(x)|).
     */
    fun estimateNoise(
        residual: DoubleArray,
        startIndex: Int,
        endIndex: Int
    ): NoiseEstimate {
        val size = residual.size
        if (size < 4) {
            return NoiseEstimate(1.0, 1.0, 1.0, 1.0, 0)
        }

        val s = startIndex.coerceIn(0, size - 2)
        val e = endIndex.coerceIn(s + 2, size)
        val windowSize = e - s

        // 1. Difference noise: Delta = v[i+1] - v[i]
        // RMS_diff = sqrt( sum(Delta^2) / (2 * (N - 1)) )
        var diffSqSum = 0.0
        val diffCount = windowSize - 1
        val differences = DoubleArray(diffCount)

        for (i in 0 until diffCount) {
            val d = residual[s + i + 1] - residual[s + i]
            differences[i] = d
            diffSqSum += d * d
        }

        val diffRms = if (diffCount > 0) sqrt(diffSqSum / (2.0 * diffCount)) else 1.0

        // 2. True Median Absolute Deviation (MAD) of the window samples
        val windowSlice = residual.copyOfRange(s, e)
        windowSlice.sort()
        val medianVal = windowSlice[windowSlice.size / 2]

        val absoluteDeviations = DoubleArray(windowSlice.size) { i ->
            abs(windowSlice[i] - medianVal)
        }
        absoluteDeviations.sort()
        val rawMad = absoluteDeviations[absoluteDeviations.size / 2]
        // Scale by 1.4826 for standard normal distribution consistency
        val scaledMad = rawMad * 1.4826

        // Effective robust noise floor
        val robustFloor = (0.5 * diffRms + 0.5 * scaledMad).coerceAtLeast(0.5)

        return NoiseEstimate(
            noiseRms = diffRms.coerceAtLeast(0.2),
            noiseMad = scaledMad.coerceAtLeast(0.2),
            rawMad = rawMad,
            noiseFloor = robustFloor,
            sampleCountUsed = windowSize
        )
    }
}
