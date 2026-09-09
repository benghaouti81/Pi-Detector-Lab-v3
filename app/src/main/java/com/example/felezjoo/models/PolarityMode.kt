package com.example.felezjoo.models

import java.io.Serializable

/**
 * Operating mode for AFE waveform polarity selection.
 *
 * AUTO: Automatically evaluates residual decay quality to select POSITIVE or NEGATIVE.
 * POSITIVE: User explicitly forces standard non-inverting AFE (+decay, polaritySign = +1.0).
 * NEGATIVE: User explicitly forces inverting AFE (-decay, polaritySign = -1.0).
 */
enum class PolarityMode(val displayName: String) : Serializable {
    AUTO("Auto"),
    POSITIVE("Normal (+)"),
    NEGATIVE("Inverted (-)")
}

/**
 * Assessment quality / confidence category for automatic polarity detection.
 */
enum class PolarityDetectionQuality(val displayName: String) : Serializable {
    HIGH("HIGH"),
    MEDIUM("MEDIUM"),
    LOW("LOW"),
    NONE("NONE")
}

/**
 * Result of automatic polarity detection on a residual decay waveform.
 */
data class PolarityDetectionResult(
    val detectedPolarity: WaveformPolarity?,
    val quality: PolarityDetectionQuality,
    val positiveScore: Double,
    val negativeScore: Double,
    val positiveR2: Double,
    val negativeR2: Double,
    val positiveValidSamples: Int,
    val negativeValidSamples: Int,
    val reason: String
) : Serializable {
    val isReliable: Boolean
        get() = detectedPolarity != null && (quality == PolarityDetectionQuality.HIGH || quality == PolarityDetectionQuality.MEDIUM)

    companion object {
        val UNKNOWN = PolarityDetectionResult(
            detectedPolarity = null,
            quality = PolarityDetectionQuality.NONE,
            positiveScore = 0.0,
            negativeScore = 0.0,
            positiveR2 = 0.0,
            negativeR2 = 0.0,
            positiveValidSamples = 0,
            negativeValidSamples = 0,
            reason = "Insufficient data or noise floor dominant"
        )
    }
}
