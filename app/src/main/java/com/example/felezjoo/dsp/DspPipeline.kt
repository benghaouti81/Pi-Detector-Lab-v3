package com.example.felezjoo.dsp

import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.models.FeatureVector
import com.example.felezjoo.models.IntegrationMode
import com.example.felezjoo.models.TargetClassification
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class DspCalculationResult(
    val block: DecayBlock,
    val filteredCurve: DoubleArray,
    val baselineCurve: DoubleArray,
    val groundCurve: DoubleArray,
    val residualCurve: DoubleArray,
    val firstDerivative: DoubleArray,
    val secondDerivative: DoubleArray,
    val featureVector: FeatureVector,
    val targetClassification: TargetClassification
)

class DspPipeline {

    var baselineCurve: DoubleArray = DoubleArray(70) { 0.0 }
        private set

    var groundCurve: DoubleArray = DoubleArray(70) { 0.0 }
        private set

    private var recentScores = mutableListOf<Double>()
    private val maxRecentHistory = 14

    fun resetBaseline(sampleCount: Int = 70) {
        baselineCurve = DoubleArray(sampleCount) { 0.0 }
    }

    fun captureAirBaseline(samples: DoubleArray) {
        baselineCurve = samples.clone()
    }

    fun resetGround(sampleCount: Int = 70) {
        groundCurve = DoubleArray(sampleCount) { 0.0 }
    }

    fun setGroundDirect(curve: DoubleArray) {
        groundCurve = curve.clone()
    }

    fun processBlock(block: DecayBlock, profile: DspProfile): DspCalculationResult {
        val raw = block.rawSamples
        val count = block.sampleCount

        // Ensure baseline and ground arrays match sample count
        if (baselineCurve.size != count) baselineCurve = DoubleArray(count) { 0.0 }
        if (groundCurve.size != count) groundCurve = DoubleArray(count) { 0.0 }

        // 1. Preprocessing Filter
        val filtered = DspFilters.applyFilter(raw, profile.filterType)

        // 2. Derivatives
        val d1 = DoubleArray(count)
        val d2 = DoubleArray(count)
        for (i in 0 until count) {
            val prev = if (i > 0) filtered[i - 1] else filtered[i]
            val curr = filtered[i]
            val next = if (i < count - 1) filtered[i + 1] else filtered[i]
            d1[i] = (next - prev) / 2.0
            d2[i] = next - 2.0 * curr + prev
        }

        // 3. Residual Curve (Measured - Baseline - Ground)
        val residual = DoubleArray(count)
        var maxResidual = 0.0
        var minResidual = Double.MAX_VALUE
        var residualSum = 0.0
        var residualRmsSum = 0.0

        for (i in 0 until count) {
            val r = filtered[i] - baselineCurve[i] - groundCurve[i]
            residual[i] = r
            if (r > maxResidual) maxResidual = r
            if (r < minResidual) minResidual = r
            residualSum += abs(r)
            residualRmsSum += r * r
        }
        val residualRms = sqrt(residualRmsSum / count)

        // 4. Noise Estimation (Late tail variation, e.g. last 25% of samples)
        val tailStart = (count * 0.70).toInt().coerceIn(0, count - 2)
        var tailDiffSum = 0.0
        var tailDiffSqSum = 0.0
        var tailCount = 0
        val tailResiduals = mutableListOf<Double>()

        for (i in tailStart until count - 1) {
            val diff = abs(residual[i + 1] - residual[i])
            tailDiffSum += diff
            tailDiffSqSum += diff * diff
            tailResiduals.add(abs(residual[i]))
            tailCount++
        }
        val noiseRms = if (tailCount > 0) sqrt(tailDiffSqSum / tailCount) else 1.0
        val noiseFloor = max(0.5, noiseRms)

        // Median Absolute Deviation (MAD) of noise
        tailResiduals.sort()
        val noiseMad = if (tailResiduals.isNotEmpty()) tailResiduals[tailResiduals.size / 2] else 1.0

        // Signal amplitude (Peak residual in valid integration zone)
        val validStart = profile.integrationStartSample.coerceIn(0, count - 1)
        val validEnd = profile.integrationEndSample.coerceIn(validStart + 1, count)
        var peakSignal = 0.0
        for (i in validStart until validEnd) {
            if (residual[i] > peakSignal) peakSignal = residual[i]
        }
        val snr = peakSignal / (noiseFloor + 0.01)

        // 5. Integration Window
        var integralSum = 0.0
        var windowWeightSum = 0.0
        val windowLen = (validEnd - validStart).coerceAtLeast(1)

        for (i in validStart until validEnd) {
            val weight = when (profile.integrationMode) {
                IntegrationMode.RECTANGULAR -> 1.0
                IntegrationMode.TRIANGULAR -> {
                    val norm = (i - validStart).toDouble() / windowLen
                    1.0 - abs(2.0 * norm - 1.0)
                }
                IntegrationMode.EXPONENTIAL -> {
                    val norm = (i - validStart).toDouble() / windowLen
                    (-2.0 * norm).let { kotlin.math.exp(it) }
                }
            }
            integralSum += residual[i] * weight
            windowWeightSum += weight
        }
        val integrationArea = integralSum
        val integrationMean = if (windowWeightSum > 0) integralSum / windowWeightSum else 0.0

        // 6. A / B / C Region Calculation
        val aStart = profile.aStartSample.coerceIn(0, count - 1)
        val aEnd = profile.aEndSample.coerceIn(aStart + 1, count)
        val bStart = profile.bStartSample.coerceIn(0, count - 1)
        val bEnd = profile.bEndSample.coerceIn(bStart + 1, count)
        val cStart = profile.cStartSample.coerceIn(0, count - 1)
        val cEnd = profile.cEndSample.coerceIn(cStart + 1, count)

        val valA = computeRegionMean(residual, aStart, aEnd)
        val valB = computeRegionMean(residual, bStart, bEnd)
        val valC = computeRegionMean(residual, cStart, cEnd)

        val aMinusB = valA - valB
        val bMinusC = valB - valC
        val aMinusC = valA - valC
        val aDivB = if (abs(valB) > 0.05) valA / valB else 1.0
        val bDivC = if (abs(valC) > 0.05) valB / valC else 1.0
        val aDivC = if (abs(valC) > 0.05) valA / valC else 1.0

        val slopeA = computeRegionSlope(d1, aStart, aEnd)
        val slopeB = computeRegionSlope(d1, bStart, bEnd)
        val slopeC = computeRegionSlope(d1, cStart, cEnd)
        val curvature = computeRegionMean(d2, aStart, bEnd)
        val earlyLateRatio = if (abs(valC) > 0.05) valA / valC else 1.0

        // 7. Persistence & Stability Tracking
        recentScores.add(peakSignal)
        if (recentScores.size > maxRecentHistory) recentScores.removeAt(0)
        val persistence = calculatePersistence(recentScores, noiseFloor)
        val stability = calculateStability(recentScores)

        // 8. Target Score (0-100)
        val signalTerm = (peakSignal / 150.0).coerceIn(0.0, 1.0) * 100.0
        val snrTerm = (snr / 20.0).coerceIn(0.0, 1.0) * 100.0
        val areaTerm = (integrationArea / 500.0).coerceIn(0.0, 1.0) * 100.0
        val shapeTerm = (aDivB / 4.0).coerceIn(0.0, 1.0) * 100.0
        val persistenceTerm = persistence

        val rawScore = (profile.weightSignal * signalTerm +
                profile.weightSnr * snrTerm +
                profile.weightArea * areaTerm +
                profile.weightShape * shapeTerm +
                profile.weightPersistence * persistenceTerm)
        val targetScore = rawScore.coerceIn(0.0, 100.0)

        // 9. Target Confidence (0-100%)
        val confidence = calculateConfidence(snr, persistence, stability, noiseFloor, targetScore)

        // 10. Experimental Iron Score (0-100)
        // High early-to-late ratio, very steep initial slope, high curvature => Ferrous
        // Smooth exponential decay, sustained late response => Non-ferrous
        val ironScore = calculateExperimentalIronScore(aDivB, bDivC, earlyLateRatio, curvature, slopeA)

        // 11. Experimental Target ID (0-99)
        val targetId = calculateTargetId(aDivB, bDivC, valA, valB, valC, peakSignal)

        // 12. Classification
        val classification = determineClassification(targetScore, confidence, ironScore, profile)

        // 13. Ground Adaptation (Slow adaptive tracking ONLY when confidence is low)
        if (confidence < 30.0 && peakSignal < (noiseFloor * 2.5) && profile.groundSpeed.alpha > 0.0) {
            val alpha = profile.groundSpeed.alpha
            for (i in 0 until count) {
                groundCurve[i] += alpha * (filtered[i] - baselineCurve[i] - groundCurve[i])
            }
        }

        val featureVector = FeatureVector(
            amplitude = peakSignal,
            peak = peakSignal,
            minimum = minResidual,
            maximum = maxResidual,
            range = maxResidual - minResidual,
            mean = residualSum / count,
            rms = residualRms,
            noise = noiseRms,
            snr = snr,
            area = integrationArea,
            integral = integrationMean,
            a = valA,
            b = valB,
            c = valC,
            aMinusB = aMinusB,
            bMinusC = bMinusC,
            aMinusC = aMinusC,
            aDivB = aDivB,
            bDivC = bDivC,
            aDivC = aDivC,
            slopeA = slopeA,
            slopeB = slopeB,
            slopeC = slopeC,
            curvature = curvature,
            earlyLateRatio = earlyLateRatio,
            residualPeak = peakSignal,
            residualArea = integrationArea,
            groundDifference = groundCurve.average(),
            persistence = persistence,
            stability = stability,
            targetScore = targetScore,
            targetConfidence = confidence,
            ironScore = ironScore,
            targetId = targetId,
            classification = classification,
            noiseMad = noiseRms * 0.82,
            areaNorm = (integrationArea / count.coerceAtLeast(1)).coerceAtLeast(0.0),
            slope = slopeA,
            estimatedTauUs = (1.0 / (slopeA.coerceAtLeast(0.01) / 100.0)).coerceIn(2.0, 100.0),
            earlyTauUs = (1.0 / (slopeA.coerceAtLeast(0.01) / 100.0)).coerceIn(1.0, 50.0),
            lateTauUs = (1.0 / (slopeB.coerceAtLeast(0.01) / 100.0)).coerceIn(2.0, 100.0),
            tauRatio = earlyLateRatio,
            integralA = valA,
            integralB = valB,
            integralC = valC,
            dspVersion = profile.dspVersion
        )

        return DspCalculationResult(
            block = block,
            filteredCurve = filtered,
            baselineCurve = baselineCurve,
            groundCurve = groundCurve,
            residualCurve = residual,
            firstDerivative = d1,
            secondDerivative = d2,
            featureVector = featureVector,
            targetClassification = classification
        )
    }

    private fun computeRegionMean(data: DoubleArray, start: Int, end: Int): Double {
        if (start >= end || start >= data.size) return 0.0
        var sum = 0.0
        val actualEnd = min(end, data.size)
        for (i in start until actualEnd) {
            sum += data[i]
        }
        return sum / (actualEnd - start)
    }

    private fun computeRegionSlope(data: DoubleArray, start: Int, end: Int): Double {
        if (start >= end || start >= data.size) return 0.0
        val actualEnd = min(end, data.size)
        return data[start] - data[actualEnd - 1]
    }

    private fun calculatePersistence(recent: List<Double>, noiseFloor: Double): Double {
        if (recent.isEmpty()) return 0.0
        val countAboveNoise = recent.count { it > noiseFloor * 2.0 }
        return (countAboveNoise.toDouble() / recent.size) * 100.0
    }

    private fun calculateStability(recent: List<Double>): Double {
        if (recent.size < 2) return 100.0
        val avg = recent.average()
        if (avg < 0.01) return 100.0
        var varianceSum = 0.0
        for (s in recent) {
            varianceSum += (s - avg).pow(2)
        }
        val stdDev = sqrt(varianceSum / recent.size)
        val cv = stdDev / avg
        return (100.0 - (cv * 100.0)).coerceIn(0.0, 100.0)
    }

    private fun calculateConfidence(
        snr: Double,
        persistence: Double,
        stability: Double,
        noiseFloor: Double,
        targetScore: Double
    ): Double {
        if (targetScore < 10.0) return 0.0
        val snrFactor = (snr / 15.0).coerceIn(0.0, 1.0)
        val persistenceFactor = (persistence / 100.0).coerceIn(0.0, 1.0)
        val stabilityFactor = (stability / 100.0).coerceIn(0.0, 1.0)
        val noiseHealth = (1.0 / (1.0 + (noiseFloor / 10.0))).coerceIn(0.0, 1.0)

        val conf = (0.35 * snrFactor + 0.30 * persistenceFactor + 0.20 * stabilityFactor + 0.15 * noiseHealth) * 100.0
        return conf.coerceIn(0.0, 100.0)
    }

    private fun calculateExperimentalIronScore(
        aDivB: Double,
        bDivC: Double,
        earlyLateRatio: Double,
        curvature: Double,
        slopeA: Double
    ): Double {
        // High early dissipation + rapid decay + negligible late component -> Ferrous (Iron)
        // Sustained eddy current -> Non-ferrous
        var score = 0.0
        if (aDivB > 2.8) score += 30.0
        else if (aDivB > 1.8) score += 15.0

        if (bDivC > 3.0) score += 25.0
        else if (bDivC > 2.0) score += 15.0

        if (earlyLateRatio > 6.0) score += 25.0
        else if (earlyLateRatio > 3.5) score += 15.0

        if (abs(curvature) > 4.0) score += 20.0
        return score.coerceIn(0.0, 100.0)
    }

    private fun calculateTargetId(
        aDivB: Double,
        bDivC: Double,
        valA: Double,
        valB: Double,
        valC: Double,
        amplitude: Double
    ): Int {
        if (amplitude < 2.0) return 0
        // Conductive targets (copper/silver) have low decay rates (tau high, aDivB small)
        // Small gold / foil / nickel have medium decay rates
        // Iron has fast decay (aDivB very high)
        val decayRatio = (aDivB + bDivC) / 2.0
        val seed = (decayRatio * 1000).toLong()
        val id = when {
            decayRatio > 3.5 -> (10..28).random(kotlin.random.Random(seed)) // Low ID: Iron / nails
            decayRatio in 2.5..3.5 -> (30..48).random(kotlin.random.Random(seed)) // Foil / nickel / small gold
            decayRatio in 1.8..2.5 -> (50..68).random(kotlin.random.Random(seed)) // Brass / bronze / jewelry
            decayRatio in 1.2..1.8 -> (70..88).random(kotlin.random.Random(seed)) // Copper / zinc
            else -> (90..98).random(kotlin.random.Random(seed)) // Silver / large copper
        }
        return id.coerceIn(1, 99)
    }

    private fun determineClassification(
        score: Double,
        confidence: Double,
        ironScore: Double,
        profile: DspProfile
    ): TargetClassification {
        return when {
            score < profile.targetThreshold || confidence < 20.0 -> TargetClassification.NO_TARGET
            confidence < profile.confidenceThreshold -> TargetClassification.POSSIBLE_TARGET
            ironScore >= profile.ironRejectThreshold -> TargetClassification.IRON
            ironScore <= 30.0 && confidence >= profile.confidenceThreshold -> TargetClassification.NON_FERROUS
            score >= profile.targetThreshold && confidence >= profile.confidenceThreshold -> TargetClassification.STABLE_TARGET
            else -> TargetClassification.UNCERTAIN
        }
    }

    /**
     * Experimental Auto Delay analysis.
     * Analyzes early samples to detect coil flyback dissipation and return recommended delay index.
     */
    fun findAutoDelay(raw: IntArray): Triple<Int, Double, Double> {
        val count = raw.size
        if (count < 10) return Triple(8, 12.8, 50.0)

        // Find inflection point where early steep flyback slope flattens out
        var bestIndex = 8
        var bestConfidence = 75.0

        for (i in 3 until min(25, count - 2)) {
            val slope = abs(raw[i] - raw[i + 1])
            val nextSlope = abs(raw[i + 1] - raw[i + 2])
            // Look for slope drop below threshold indicating coil discharge completion
            if (slope < 12 && nextSlope < 10) {
                bestIndex = i + 1
                bestConfidence = 85.0
                break
            }
        }
        val delayUs = bestIndex * 1.6
        return Triple(bestIndex, delayUs, bestConfidence)
    }
}
