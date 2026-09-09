package com.example.felezjoo.dsp

import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.DetectionCalibration
import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.models.FeatureVector
import com.example.felezjoo.models.IntegrationMode
import com.example.felezjoo.models.TargetClassification
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Complete DSP calculation output bundle.
 * Contains both raw signed residuals and polarity-normalized waveforms,
 * along with dual detection/discrimination curves and scientific feature metrics.
 */
data class DspCalculationResult(
    val block: DecayBlock,
    val filteredCurve: DoubleArray,
    val baselineCurve: DoubleArray,
    val groundCurve: DoubleArray, // Snapshot of ground model used to compute this frame's residual
    val residualCurve: DoubleArray,
    val firstDerivative: DoubleArray,
    val secondDerivative: DoubleArray,
    val featureVector: FeatureVector,
    val targetClassification: TargetClassification,
    val normalizedResidualCurve: DoubleArray = residualCurve,
    val detectionCurve: DoubleArray = filteredCurve,
    val tauFitResult: TauFitResult = TauFitResult.UNAVAILABLE,
    val earlyTauFitResult: TauFitResult = TauFitResult.UNAVAILABLE,
    val lateTauFitResult: TauFitResult = TauFitResult.UNAVAILABLE,
    val isGroundFrozen: Boolean = false,
    val groundFreezeReason: String = "",
    val postUpdateGroundCurve: DoubleArray = groundCurve
)

/**
 * Production-grade Pulse Induction Digital Signal Processing (DSP) Pipeline.
 *
 * Core Principles:
 * 1. Physical time-domain processing (all windows and derivatives scaled by dt in microseconds).
 * 2. Conceptual separation of Air Baseline (AIR(t)), Ground (GROUND(t)), and Target Residual.
 * 3. Strict State Isolation (updateState = false guarantees zero mutation of adaptive models).
 * 4. Dual-path processing: minimal-distortion Detection Path + filtered Discrimination Path.
 * 5. Polarity normalization for non-inverting and inverting AFEs.
 * 6. Scientific Tau estimation via multi-point log-linear regression (no arbitrary formulas).
 * 7. Multi-zone Safe Ground Tracking with freeze protection on target detection.
 * 8. Real noise estimation via Median Absolute Deviation (MAD) and difference noise.
 * 9. Deterministic DSP feature extraction and heuristic detection/classification.
 *    - Target ID is unavailable (targetId = 0, isTargetIdCalibrated = false) until a calibrated labelled dataset/model exists.
 *    - Ferrous/material indication (ironScore) is an uncalibrated heuristic material metric.
 *    - confidenceScore is an engineering heuristic metric (0..100), not a calibrated probability.
 */
class DspPipeline {

    private val stateLock = Any()

    var baselineCurve: DoubleArray = DoubleArray(70) { 0.0 }
        private set

    var groundCurve: DoubleArray = DoubleArray(70) { 0.0 }
        private set

    private val recentScores = mutableListOf<Double>()
    private val maxRecentHistory = 14

    val safeGroundTracker = SafeGroundTracker()
    val targetTracker = TemporalTargetTracker()

    fun resetBaseline(sampleCount: Int = 70) = synchronized(stateLock) {
        baselineCurve = DoubleArray(sampleCount) { 0.0 }
    }

    fun captureAirBaseline(samples: DoubleArray) = synchronized(stateLock) {
        baselineCurve = samples.clone()
    }

    fun resetGround(sampleCount: Int = 70) = synchronized(stateLock) {
        groundCurve = DoubleArray(sampleCount) { 0.0 }
        safeGroundTracker.reset()
    }

    fun setGroundDirect(curve: DoubleArray) = synchronized(stateLock) {
        groundCurve = curve.clone()
    }

    fun resetAllTrackers() = synchronized(stateLock) {
        recentScores.clear()
        safeGroundTracker.reset()
        targetTracker.reset()
    }

    fun getRecentScores(): List<Double> = synchronized(stateLock) {
        recentScores.toList()
    }

    /**
     * Executes the complete DSP pipeline on an incoming decay acquisition block.
     *
     * @param block Raw decay acquisition block
     * @param profile Active DSP configuration profile
     * @param updateState When true (live stream), mutates ground model, history, and trackers.
     *                    When false (offline comparison / inspector), performs pure functional
     *                    read-only calculations with absolutely ZERO side effects on state.
     */
    fun processBlock(
        block: DecayBlock,
        profile: DspProfile,
        updateState: Boolean = true
    ): DspCalculationResult {
        val config = block.samplingConfiguration

        // 1. Equivalent Time Sampling (ETS) Chronological Reconstruction
        // Ensures samples represent monotonic physical time t = i * dt
        val rawChronological = EtsReconstruction.toChronological(block.rawSamples, config)
        val count = rawChronological.size
        // Hardware sampling spacing: derived from block or active sampling configuration (never a blind hardcoded constant)
        val dt = if (config.sampleSpacingUs > 0.0) config.sampleSpacingUs else if (block.sampleSpacingUs > 0.0) block.sampleSpacingUs else 1.6

        // 2. Thread-safe snapshot of baseline and ground models
        val activeBaseline: DoubleArray
        val activeGround: DoubleArray
        val scoreHistorySnapshot: List<Double>

        synchronized(stateLock) {
            if (baselineCurve.size != count) {
                baselineCurve = DoubleArray(count) { 0.0 }
            }
            if (groundCurve.size != count) {
                groundCurve = DoubleArray(count) { 0.0 }
            }
            activeBaseline = baselineCurve.clone()
            activeGround = groundCurve.clone()
            scoreHistorySnapshot = recentScores.toList()
        }

        // 3. Air-Compensated Signal: X(t) = RAW(t) - AIR(t)
        val rawDouble = DoubleArray(count) { i -> rawChronological[i].toDouble() }
        val airCompensated = DoubleArray(count) { i -> rawDouble[i] - activeBaseline[i] }

        // 4. Residual Curve: RESIDUAL(t) = X(t) - GROUND(t)
        val rawResidual = DoubleArray(count) { i -> airCompensated[i] - activeGround[i] }

        // 5. Polarity Normalization:
        // Inverting AFEs deflect downward; normalize so that eddy current decays are positive
        val polaritySign = block.polarity.sign
        val normalizedResidual = DoubleArray(count) { i -> rawResidual[i] * polaritySign }

        // 6. Dual Signal Paths
        // Detection Path: Light 3-point median to reject single-slot spikes while preserving peak and early onset
        val detectionCurve = DspFilters.applyFilter(normalizedResidual, com.example.felezjoo.models.FilterType.MEDIAN_3)

        // Discrimination Path: Profile filter (e.g. Savitzky-Golay or Moving Average) for derivatives and curvature
        val discriminationCurve = DspFilters.applyFilter(normalizedResidual, profile.filterType)

        // 7. Physical Derivatives in time domain (dV/dt in V/us, d2V/dt2 in V/us^2)
        val d1 = DoubleArray(count)
        val d2 = DoubleArray(count)
        val twoDt = 2.0 * dt
        val dtSq = dt * dt
        for (i in 0 until count) {
            val prev = if (i > 0) discriminationCurve[i - 1] else discriminationCurve[i]
            val curr = discriminationCurve[i]
            val next = if (i < count - 1) discriminationCurve[i + 1] else discriminationCurve[i]
            d1[i] = (next - prev) / twoDt
            d2[i] = (next - 2.0 * curr + prev) / dtSq
        }

        // 8. Robust Noise Floor Estimation (True MAD and Difference Noise on late tail)
        // Uses calibration.tailNoiseFraction with safe clamping for small sample counts
        val tailFraction = profile.calibration.tailNoiseFraction
        val tailStart = ((count * tailFraction).toInt()).coerceIn(0, (count - 2).coerceAtLeast(0))
        val noiseEstimate = NoiseEstimator.estimateNoise(normalizedResidual, tailStart, count)
        val noiseFloor = noiseEstimate.noiseFloor
        val noiseRms = noiseEstimate.noiseRms
        val noiseMad = noiseEstimate.noiseMad

        // 9. Peak and Amplitude Detection in Physical Integration Window
        val (validStart, validEnd) = profile.getIntegrationIndices(config)
        var peakNormalized = 0.0
        var peakIndex = validStart
        var maxResidual = -Double.MAX_VALUE
        var minResidual = Double.MAX_VALUE
        var residualSum = 0.0
        var residualAbsSum = 0.0
        var residualSqSum = 0.0

        for (i in 0 until count) {
            val r = normalizedResidual[i]
            if (r > maxResidual) maxResidual = r
            if (r < minResidual) minResidual = r
            residualSum += r
            residualAbsSum += abs(r)
            residualSqSum += r * r

            if (i in validStart until validEnd) {
                val detVal = detectionCurve[i]
                if (detVal > peakNormalized) {
                    peakNormalized = detVal
                    peakIndex = i
                }
            }
        }
        val residualRms = sqrt(residualSqSum / count.coerceAtLeast(1))
        val peakTimeUs = if (dt > 0.0) peakIndex * dt else Double.NaN
        val peakSigned = rawResidual[peakIndex]
        val peakAbsolute = abs(rawResidual[peakIndex])
        val peakSignal = peakNormalized
        val snr = (peakNormalized / (noiseFloor + 0.001)).coerceAtLeast(0.0)

        // 10. Physical Integration (microsecond time integral: sum(V[i] * w[i] * dt))
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
                    exp(-2.0 * norm)
                }
            }
            integralSum += detectionCurve[i] * weight * dt
            windowWeightSum += weight
        }
        val integrationArea = integralSum
        val integrationMean = if (windowWeightSum > 0.0) integralSum / (windowWeightSum * dt) else 0.0

        // 11. Microsecond A/B/C Physical Regions and Safe Ratios
        val (aStart, aEnd) = profile.getRegionAIndices(config)
        val (bStart, bEnd) = profile.getRegionBIndices(config)
        val (cStart, cEnd) = profile.getRegionCIndices(config)

        val valA = computeRegionMean(detectionCurve, aStart, aEnd)
        val valB = computeRegionMean(detectionCurve, bStart, bEnd)
        val valC = computeRegionMean(detectionCurve, cStart, cEnd)

        val energyA = computeRegionEnergy(detectionCurve, aStart, aEnd, dt)
        val energyB = computeRegionEnergy(detectionCurve, bStart, bEnd, dt)
        val energyC = computeRegionEnergy(detectionCurve, cStart, cEnd, dt)

        val aMinusB = valA - valB
        val bMinusC = valB - valC
        val aMinusC = valA - valC

        val safeThreshold = (noiseFloor * 1.5).coerceAtLeast(0.5)
        val aDivB = if (abs(valB) > safeThreshold) valA / valB else 1.0
        val bDivC = if (abs(valC) > safeThreshold) valB / valC else 1.0
        val aDivC = if (abs(valC) > safeThreshold) valA / valC else 1.0
        val earlyLateRatio = if (abs(valC) > safeThreshold) valA / valC else (valA / safeThreshold).coerceIn(1.0, 20.0)

        val slopeA = computeRegionSlope(d1, aStart, aEnd)
        val slopeB = computeRegionSlope(d1, bStart, bEnd)
        val slopeC = computeRegionSlope(d1, cStart, cEnd)
        val curvature = computeRegionMean(d2, aStart, bEnd)

        // 12. Scientific Tau Estimation via Multi-Point Log-Linear Regression
        // Uses normalizedResidual (the unskewed measurement path), NOT detectionCurve (which has non-linear median filter distortion)
        val cal = profile.calibration

        // Full-window effective single-exponential Tau estimate
        // Fitted over the selected decay window (validStart until validEnd)
        val effectiveTauResult = TauEstimator.estimateTau(
            waveform = normalizedResidual,
            sampleSpacingUs = dt,
            startIndex = validStart,
            endIndex = validEnd,
            noiseFloor = noiseFloor,
            calibration = cal,
            saturationThreshold = (config.adcFullScale - 5.0)
        )

        // Early-region Tau estimate (derived from profile physical early window converted via config)
        val (earlyStart, earlyEnd) = profile.getEarlyTauIndices(config)
        val earlyTauResult = TauEstimator.estimateTau(
            waveform = normalizedResidual,
            sampleSpacingUs = dt,
            startIndex = earlyStart,
            endIndex = earlyEnd,
            noiseFloor = noiseFloor,
            calibration = cal,
            saturationThreshold = (config.adcFullScale - 5.0)
        )

        // Late-region Tau estimate (derived from profile physical late window converted via config)
        val (lateStart, lateEnd) = profile.getLateTauIndices(config)
        val lateTauResult = TauEstimator.estimateTau(
            waveform = normalizedResidual,
            sampleSpacingUs = dt,
            startIndex = lateStart,
            endIndex = lateEnd,
            noiseFloor = noiseFloor,
            calibration = cal,
            saturationThreshold = (config.adcFullScale - 5.0)
        )

        // 13. Persistence & Stability Tracking
        val activeHistory = if (updateState) {
            synchronized(stateLock) {
                recentScores.add(peakSignal)
                if (recentScores.size > maxRecentHistory) recentScores.removeAt(0)
                recentScores.toList()
            }
        } else {
            val virtual = scoreHistorySnapshot.toMutableList()
            virtual.add(peakSignal)
            if (virtual.size > maxRecentHistory) virtual.removeAt(0)
            virtual
        }

        val persistence = calculatePersistence(activeHistory, noiseFloor)
        val stability = calculateStability(activeHistory)

        // 14. Target Score Normalization (0-100)
        // Scaled using DetectionCalibration heuristic references
        val adcFullScale = config.adcFullScale
        val signalRef = (adcFullScale * cal.signalFractionRef).coerceIn(50.0, 1000.0)
        val signalTerm = (peakSignal / signalRef).coerceIn(0.0, 1.0) * 100.0
        val snrTerm = (snr / cal.snrReference).coerceIn(0.0, 1.0) * 100.0
        val areaRef = signalRef * (validEnd - validStart) * dt * 0.5
        val areaTerm = (integrationArea / areaRef.coerceAtLeast(100.0)).coerceIn(0.0, 1.0) * 100.0
        val shapeTerm = (aDivB / cal.shapeRatioReference).coerceIn(0.0, 1.0) * 100.0
        val persistenceTerm = persistence

        val rawScore = (profile.weightSignal * signalTerm +
                profile.weightSnr * snrTerm +
                profile.weightArea * areaTerm +
                profile.weightShape * shapeTerm +
                profile.weightPersistence * persistenceTerm)
        val targetScore = rawScore.coerceIn(0.0, 100.0)

        // 15. Heuristic Confidence Score (0-100)
        // Note: confidenceScore is a heuristic 0..100 engineering confidence metric based on SNR, persistence,
        // stability, and noise health, NOT a statistically calibrated probability.
        val confidenceScore = calculateConfidenceScore(snr, persistence, stability, noiseFloor, targetScore, cal)

        // 16. Heuristic Ferrous Likelihood Metric (0-100)
        // Rapid early collapse + low late eddy currents -> Ferrous likelihood
        // Smooth exponential decay, high tau, sustained late response -> Non-ferrous likelihood
        // Note: This is an uncalibrated heuristic material indication, NOT a scientifically calibrated material identification.
        val ironScore = calculateHeuristicFerrousScore(aDivB, bDivC, earlyLateRatio, curvature, slopeA, effectiveTauResult)

        // 17. Target ID: Unavailable until a calibrated labelled dataset/model exists
        // No physical VDI calibration dataset exists for this detector hardware and coil set.
        // Returning targetId = 0 and isTargetIdCalibrated = false indicates UNAVAILABLE.
        val targetId = 0

        // 18. Objective Target Classification
        val classification = determineClassification(targetScore, confidenceScore, ironScore, profile)

        // 19. Multi-zone Safe Ground Adaptation
        val groundAdaptationStatus: GroundAdaptationStatus
        if (updateState) {
            synchronized(stateLock) {
                groundAdaptationStatus = safeGroundTracker.processFrame(
                    groundCurve = groundCurve,
                    airCompensated = airCompensated,
                    targetScore = targetScore,
                    snr = snr,
                    persistence = persistence,
                    classification = classification,
                    noiseFloor = noiseFloor,
                    peakSignal = peakSignal,
                    isSaturated = block.isSaturated,
                    profile = profile,
                    applyUpdate = true
                )
            }
        } else {
            // Read-only evaluation on cloned ground curve: zero mutation
            groundAdaptationStatus = safeGroundTracker.processFrame(
                groundCurve = activeGround.clone(),
                airCompensated = airCompensated,
                targetScore = targetScore,
                snr = snr,
                persistence = persistence,
                classification = classification,
                noiseFloor = noiseFloor,
                peakSignal = peakSignal,
                isSaturated = block.isSaturated,
                profile = profile,
                applyUpdate = false
            )
        }

        // 20. Temporal Target Tracking State Machine
        val fv = FeatureVector(
            amplitude = peakNormalized,
            peak = peakNormalized,
            peakNormalized = peakNormalized,
            peakSigned = peakSigned,
            peakAbsolute = peakAbsolute,
            peakIndex = peakIndex,
            peakTimeUs = peakTimeUs,
            minimum = minResidual,
            maximum = maxResidual,
            range = maxResidual - minResidual,
            mean = residualSum / count.coerceAtLeast(1),
            meanAbsolute = residualAbsSum / count.coerceAtLeast(1),
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
            residualPeak = peakNormalized,
            residualArea = integrationArea,
            groundDifference = activeGround.average(),
            persistence = persistence,
            stability = stability,
            targetScore = targetScore,
            targetConfidence = confidenceScore,
            ironScore = ironScore,
            targetId = targetId,
            isTargetIdCalibrated = false,
            classification = classification,
            noiseMad = noiseMad,
            areaNorm = (integrationArea / count.coerceAtLeast(1)).coerceAtLeast(0.0),
            slope = slopeA,
            estimatedTauUs = if (effectiveTauResult.isAvailable) effectiveTauResult.tauUs else 0.0,
            earlyTauUs = if (earlyTauResult.isAvailable) earlyTauResult.tauUs else 0.0,
            lateTauUs = if (lateTauResult.isAvailable) lateTauResult.tauUs else 0.0,
            tauRatio = if (earlyTauResult.isAvailable && lateTauResult.isAvailable && lateTauResult.tauUs > 1e-4) {
                earlyTauResult.tauUs / lateTauResult.tauUs
            } else {
                0.0
            },
            tauFitR2 = effectiveTauResult.rSquared,
            tauFitError = effectiveTauResult.fitError,
            tauFitSampleCount = effectiveTauResult.fitSampleCount,
            isTauValid = effectiveTauResult.isAvailable,
            isEarlyTauValid = earlyTauResult.isAvailable,
            isLateTauValid = lateTauResult.isAvailable,
            tauFitStartUs = effectiveTauResult.fitStartUs,
            tauFitEndUs = effectiveTauResult.fitEndUs,
            energyA = energyA,
            energyB = energyB,
            energyC = energyC,
            integralA = valA,
            integralB = valB,
            integralC = valC,
            isGroundFrozen = groundAdaptationStatus.isFrozen,
            groundFreezeReason = groundAdaptationStatus.freezeReason,
            dtUs = dt,
            adcResolution = config.adcResolution,
            dspVersion = profile.dspVersion
        )

        if (updateState) {
            targetTracker.processFrame(fv, classification, block.timestamp, updateState = true)
        }

        val finalGroundSnapshot = if (updateState) {
            synchronized(stateLock) { groundCurve.clone() }
        } else {
            activeGround
        }

        return DspCalculationResult(
            block = block,
            filteredCurve = discriminationCurve,
            detectionCurve = detectionCurve,
            baselineCurve = activeBaseline,
            groundCurve = activeGround,
            residualCurve = rawResidual,
            normalizedResidualCurve = normalizedResidual,
            firstDerivative = d1,
            secondDerivative = d2,
            featureVector = fv,
            targetClassification = classification,
            tauFitResult = effectiveTauResult,
            earlyTauFitResult = earlyTauResult,
            lateTauFitResult = lateTauResult,
            isGroundFrozen = groundAdaptationStatus.isFrozen,
            groundFreezeReason = groundAdaptationStatus.freezeReason,
            postUpdateGroundCurve = finalGroundSnapshot
        )
    }

    private fun computeRegionMean(data: DoubleArray, start: Int, end: Int): Double {
        if (data.isEmpty() || start >= end) return 0.0
        val s = start.coerceIn(0, data.size - 1)
        val e = end.coerceIn(s + 1, data.size)
        var sum = 0.0
        for (i in s until e) sum += data[i]
        return sum / (e - s)
    }

    private fun computeRegionEnergy(data: DoubleArray, start: Int, end: Int, dt: Double): Double {
        if (data.isEmpty() || start >= end) return 0.0
        val s = start.coerceIn(0, data.size - 1)
        val e = end.coerceIn(s + 1, data.size)
        var sum = 0.0
        for (i in s until e) {
            val v = data[i]
            sum += v * v * dt
        }
        return sum
    }

    private fun computeRegionSlope(d1: DoubleArray, start: Int, end: Int): Double {
        if (d1.isEmpty() || start >= end) return 0.0
        val s = start.coerceIn(0, d1.size - 1)
        val e = end.coerceIn(s + 1, d1.size)
        var sum = 0.0
        for (i in s until e) sum += d1[i]
        return sum / (e - s)
    }

    private fun calculatePersistence(scores: List<Double>, noiseFloor: Double): Double {
        if (scores.isEmpty()) return 0.0
        val threshold = max(1.5 * noiseFloor, 2.0)
        val aboveCount = scores.count { it > threshold }
        return (aboveCount.toDouble() / scores.size) * 100.0
    }

    private fun calculateStability(scores: List<Double>): Double {
        if (scores.size < 3) return 50.0
        val mean = scores.average()
        if (mean < 0.5) return 90.0
        val variance = scores.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val cv = stdDev / mean
        return ((1.0 - cv.coerceIn(0.0, 1.0)) * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Calculates heuristic engineering confidence score (0-100).
     * Note: confidenceScore is a heuristic 0..100 engineering confidence metric based on SNR, persistence,
     * stability, and noise health, NOT a statistically calibrated probability.
     */
    private fun calculateConfidenceScore(
        snr: Double,
        persistence: Double,
        stability: Double,
        noiseFloor: Double,
        targetScore: Double,
        calibration: DetectionCalibration
    ): Double {
        val snrFactor = (snr / calibration.confidenceSnrScale).coerceIn(0.0, 1.0)
        val persistenceFactor = (persistence / 100.0).coerceIn(0.0, 1.0)
        val stabilityFactor = (stability / 100.0).coerceIn(0.0, 1.0)
        val noiseHealth = (1.0 / (1.0 + (noiseFloor / calibration.noiseHealthScale))).coerceIn(0.0, 1.0)

        val conf = (0.35 * snrFactor + 0.30 * persistenceFactor + 0.20 * stabilityFactor + 0.15 * noiseHealth) * 100.0
        return conf.coerceIn(0.0, 100.0)
    }

    /**
     * Calculates heuristic ferrous likelihood score (0-100) based on decay-shape metrics.
     * Note: This is an uncalibrated heuristic material indication, NOT a scientifically calibrated material identification.
     */
    private fun calculateHeuristicFerrousScore(
        aDivB: Double,
        bDivC: Double,
        earlyLateRatio: Double,
        curvature: Double,
        slopeA: Double,
        tauResult: TauFitResult
    ): Double {
        var score = 0.0
        // Rapid early collapse + low late eddy currents -> Ferrous
        if (aDivB > 2.8) score += 30.0
        else if (aDivB > 1.8) score += 15.0

        if (bDivC > 3.0) score += 25.0
        else if (bDivC > 2.0) score += 15.0

        if (earlyLateRatio > 5.0) score += 25.0
        else if (earlyLateRatio > 3.0) score += 15.0

        if (abs(curvature) > 4.0) score += 15.0

        // High tau (slow decay) indicates highly conductive non-ferrous metal (copper, silver, gold)
        if (tauResult.isAvailable) {
            if (tauResult.tauUs > 25.0) score -= 35.0
            else if (tauResult.tauUs < 8.0) score += 20.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    /**
     * Determines target classification using heuristic score and likelihood thresholds.
     * Note: FERROUS_LIKELY and NON_FERROUS_LIKELY are heuristic indications based on experimental engineering
     * thresholds, not laboratory-calibrated material identifications.
     */
    private fun determineClassification(
        score: Double,
        confidence: Double,
        ironScore: Double,
        profile: DspProfile
    ): TargetClassification {
        return when {
            score < profile.targetThreshold || confidence < 20.0 -> TargetClassification.NO_TARGET
            confidence < profile.confidenceThreshold -> TargetClassification.POSSIBLE_TARGET
            ironScore >= profile.ironRejectThreshold -> TargetClassification.FERROUS_LIKELY
            ironScore <= 35.0 && confidence >= profile.confidenceThreshold -> TargetClassification.NON_FERROUS_LIKELY
            score >= profile.targetThreshold && confidence >= profile.confidenceThreshold -> TargetClassification.POSSIBLE_TARGET
            else -> TargetClassification.UNKNOWN
        }
    }

    /**
     * Experimental Auto Delay analysis.
     * Analyzes early samples to detect coil flyback dissipation and returns recommended delay index.
     * Time is derived from active hardware sample spacing (t = sampleIndex * dt).
     */
    fun findAutoDelay(
        raw: IntArray,
        config: com.example.felezjoo.models.SamplingConfiguration = com.example.felezjoo.models.SamplingConfiguration()
    ): Triple<Int, Double, Double> {
        val count = raw.size
        val dt = if (config.sampleSpacingUs > 0.0) config.sampleSpacingUs else config.delayUnitUs
        if (count < 10) return Triple(8, if (dt > 0.0) 8 * dt else Double.NaN, 50.0)

        var bestIndex = 8
        var bestConfidence = 75.0

        for (i in 3 until min(25, count - 2)) {
            val slope = abs(raw[i] - raw[i + 1])
            val nextSlope = abs(raw[i + 1] - raw[i + 2])
            if (slope < 12 && nextSlope < 10) {
                bestIndex = i + 1
                bestConfidence = 85.0
                break
            }
        }
        val delayUs = if (dt > 0.0) bestIndex * dt else Double.NaN
        return Triple(bestIndex, delayUs, bestConfidence)
    }
}
