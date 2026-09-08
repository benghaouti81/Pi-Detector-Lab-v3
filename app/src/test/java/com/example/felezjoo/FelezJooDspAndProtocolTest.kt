package com.example.felezjoo

import com.example.felezjoo.dsp.DspPipeline
import com.example.felezjoo.dsp.EtsReconstruction
import com.example.felezjoo.dsp.EtsTransportOrder
import com.example.felezjoo.dsp.NoiseEstimator
import com.example.felezjoo.dsp.SafeGroundTracker
import com.example.felezjoo.dsp.TauEstimator
import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.models.GroundSpeed
import com.example.felezjoo.models.SamplingConfiguration
import com.example.felezjoo.models.TargetClassification
import com.example.felezjoo.models.WaveformPolarity
import com.example.felezjoo.protocol.Crc16Ccitt
import com.example.felezjoo.protocol.PacketConstants
import com.example.felezjoo.protocol.PacketGenerator
import com.example.felezjoo.protocol.ProtocolParser
import com.example.felezjoo.protocol.RawPacketRecord
import com.example.felezjoo.simulation.SimulationEngine
import com.example.felezjoo.simulation.SimulationTargetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

class FelezJooDspAndProtocolTest {

    @Test
    fun testCrc16Ccitt() {
        val testData = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = Crc16Ccitt.compute(testData)
        // Standard CCITT CRC for "123456789" with init 0xFFFF is 0x29B1
        assertEquals(0x29B1, crc)
    }

    @Test
    fun testPacketGeneratorAndParser() {
        var parsedBlock: DecayBlock? = null
        var parsedRecord: RawPacketRecord? = null

        val parser = ProtocolParser(
            onDecayBlockParsed = { parsedBlock = it },
            onRawPacketRecord = { parsedRecord = it },
            onAsciiLineParsed = {},
            onSequenceGapDetected = { _, _ -> },
            onCrcErrorDetected = { _, _ -> }
        )

        val sampleData = IntArray(70) { 500 - it * 5 }
        val packet = PacketGenerator.createRawBlockPacket(
            sequence = 42L,
            timestamp = System.currentTimeMillis(),
            delayTicks = 12,
            samples = sampleData,
            flags = 0x0001
        )

        assertEquals(PacketConstants.RAW_BLOCK_TOTAL_PACKET_LEN, packet.size)
        parser.processIncomingBytes(packet, packet.size)

        assertNotNull(parsedRecord)
        assertTrue(parsedRecord!!.isValid)
        assertEquals(42L, parsedRecord!!.sequence)

        assertNotNull(parsedBlock)
        assertEquals(42L, parsedBlock!!.sequenceNumber)
        assertEquals(70, parsedBlock!!.sampleCount)
        assertEquals(12, parsedBlock!!.delayTicks)
    }

    @Test
    fun testTauEstimatorScientificFit() {
        // Synthesize an exact exponential decay: V(t) = A * exp(-t / tau)
        // True parameters: A = 150.0, tau = 25.0 us, dt = 1.6 us
        val sampleCount = 60
        val dt = 1.6
        val trueTau = 25.0
        val waveform = DoubleArray(sampleCount) { i ->
            val t = i * dt
            150.0 * exp(-t / trueTau)
        }

        val fit = TauEstimator.estimateTau(
            waveform = waveform,
            sampleSpacingUs = dt,
            startIndex = 5,
            endIndex = 40,
            noiseFloor = 0.5
        )

        assertTrue("Fit must be available", fit.isAvailable)
        // Tau must be within 2% of the ground truth 25.0 us
        assertEquals(trueTau, fit.tauUs, 0.5)
        // R^2 must be > 0.999 for synthetic noiseless exponential
        assertTrue("R^2 must be near 1.0 (actual: ${fit.rSquared})", fit.rSquared > 0.999)
    }

    @Test
    fun testEtsChronologicalReordering() {
        val config = SamplingConfiguration(
            sampleCount = 70,
            sampleSpacingUs = 1.6,
            pulsesPerFrame = 5,
            samplesPerPulse = 14,
            transportOrder = EtsTransportOrder.PULSE_FIRST
        )

        // Monotonic physical signal
        val originalChronological = IntArray(70) { it * 10 }

        // Convert to pulse-first transport packing
        val pulseFirst = EtsReconstruction.toPulseFirst(originalChronological, config)

        // Reconstruct back to chronological using config
        val reconstructed = EtsReconstruction.toChronological(pulseFirst, config)

        for (i in 0 until 70) {
            assertEquals("Sample $i must match original after ETS reconstruction", originalChronological[i], reconstructed[i])
        }
    }

    @Test
    fun testNoiseEstimatorMad() {
        // Synthetic stationary data with zero slope: baseline 50.0 + uniform small noise
        val data = DoubleArray(50) { 50.0 + (if (it % 2 == 0) 1.5 else -1.5) }
        val noise = NoiseEstimator.estimateNoise(data, 0, 50)

        assertTrue("Noise floor must be > 0", noise.noiseFloor > 0.0)
        assertTrue("Noise MAD must be positive", noise.noiseMad > 0.0)
        assertTrue("Noise RMS must be positive", noise.noiseRms > 0.0)
    }

    @Test
    fun testSafeGroundTrackerFreezesOnTarget() {
        val tracker = SafeGroundTracker()
        val ground = DoubleArray(70) { 10.0 }
        val airCompensated = DoubleArray(70) { 50.0 } // target deflection

        val profile = DspProfile.STABLE

        // 1. First quiet frame (debouncing/awaiting quiet frames)
        tracker.processFrame(
            groundCurve = ground,
            airCompensated = DoubleArray(70) { 10.0 },
            targetScore = 5.0,
            snr = 0.5,
            persistence = 0.0,
            classification = TargetClassification.NO_TARGET,
            noiseFloor = 2.0,
            peakSignal = 2.0,
            isSaturated = false,
            profile = profile,
            applyUpdate = true
        )

        // 2. Second quiet frame -> hysteresis satisfied, ground must be adapting
        val statusQuiet = tracker.processFrame(
            groundCurve = ground,
            airCompensated = DoubleArray(70) { 10.0 },
            targetScore = 5.0,
            snr = 0.5,
            persistence = 0.0,
            classification = TargetClassification.NO_TARGET,
            noiseFloor = 2.0,
            peakSignal = 2.0,
            isSaturated = false,
            profile = profile,
            applyUpdate = true
        )
        assertFalse("Ground must adapt after quiet frames hysteresis", statusQuiet.isFrozen)

        // 3. Strong target frame -> must freeze immediately
        val statusTarget = tracker.processFrame(
            groundCurve = ground,
            airCompensated = airCompensated,
            targetScore = 75.0,
            snr = 18.0,
            persistence = 80.0,
            classification = TargetClassification.FERROUS_LIKELY,
            noiseFloor = 2.0,
            peakSignal = 40.0,
            isSaturated = false,
            profile = profile,
            applyUpdate = true
        )
        assertTrue("Ground must freeze on target detection", statusTarget.isFrozen)
        assertTrue("Freeze reason must indicate target presence (was: ${statusTarget.freezeReason})", statusTarget.freezeReason.contains("TARGET"))
    }

    @Test
    fun testStateIsolationReadOnly() {
        val pipeline = DspPipeline()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val simEngine = SimulationEngine(scope) { _, _ -> }
        simEngine.targetType = SimulationTargetType.BRASS_RELIC
        val (block, _) = simEngine.generateSingleBlock()

        // 1. Process one block with updateState = true to establish active tracking state
        pipeline.processBlock(block, DspProfile.STABLE, updateState = true)

        // Capture full snapshot before read-only operations
        val initialGround = pipeline.groundCurve.clone()
        val initialBaseline = pipeline.baselineCurve.clone()
        val initialRecentScores = pipeline.getRecentScores()
        val initialQuietFrames = pipeline.safeGroundTracker.quietFramesCount
        val initialIsFrozen = pipeline.safeGroundTracker.isFrozen
        val initialFreezeReason = pipeline.safeGroundTracker.lastFreezeReason
        val initialTrackerState = pipeline.targetTracker.state
        val initialCandidateCount = pipeline.targetTracker.currentCandidateCount
        val initialQuietCount = pipeline.targetTracker.currentQuietCount
        val initialActiveEvent = pipeline.targetTracker.currentActiveEvent

        // 2. Execute processBlock with updateState = false across multiple profiles and diverse targets
        val profilesToTest = listOf(
            DspProfile.ORIGINAL_LIKE,
            DspProfile.STABLE,
            DspProfile.MAXIMUM_DEPTH,
            DspProfile.FAST_RESPONSE,
            DspProfile.MINERALIZED_GROUND,
            DspProfile.EXPERIMENTAL_A
        )

        val targetTypesToTest = listOf(
            SimulationTargetType.NO_TARGET,
            SimulationTargetType.IRON_NAIL,
            SimulationTargetType.COPPER_COIN,
            SimulationTargetType.SMALL_GOLD_NUGGET,
            SimulationTargetType.RUSTY_CAN
        )

        for (target in targetTypesToTest) {
            simEngine.targetType = target
            val (testBlock, _) = simEngine.generateSingleBlock()
            for (p in profilesToTest) {
                val res = pipeline.processBlock(testBlock, p, updateState = false)
                assertNotNull("ProcessBlock result must not be null in read-only mode", res)
            }
        }

        // 3. Assert absolutely ZERO mutation occurred in any adaptive state
        // Baseline & Ground curves
        for (i in initialGround.indices) {
            assertEquals("Ground model must not mutate during read-only calls (index $i)", initialGround[i], pipeline.groundCurve[i], 1e-9)
            assertEquals("Baseline model must not mutate during read-only calls (index $i)", initialBaseline[i], pipeline.baselineCurve[i], 1e-9)
        }

        // Recent scores history
        assertEquals("recentScores list size must remain unchanged", initialRecentScores.size, pipeline.getRecentScores().size)
        assertEquals("recentScores history must not mutate", initialRecentScores, pipeline.getRecentScores())

        // SafeGroundTracker state
        assertEquals("SafeGroundTracker quietFramesCount must not mutate", initialQuietFrames, pipeline.safeGroundTracker.quietFramesCount)
        assertEquals("SafeGroundTracker isFrozen must not mutate", initialIsFrozen, pipeline.safeGroundTracker.isFrozen)
        assertEquals("SafeGroundTracker lastFreezeReason must not mutate", initialFreezeReason, pipeline.safeGroundTracker.lastFreezeReason)

        // TemporalTargetTracker state
        assertEquals("TemporalTargetTracker state must not mutate", initialTrackerState, pipeline.targetTracker.state)
        assertEquals("TemporalTargetTracker candidateCount must not mutate", initialCandidateCount, pipeline.targetTracker.currentCandidateCount)
        assertEquals("TemporalTargetTracker quietCount must not mutate", initialQuietCount, pipeline.targetTracker.currentQuietCount)
        assertEquals("TemporalTargetTracker activeEvent must not mutate", initialActiveEvent, pipeline.targetTracker.currentActiveEvent)
    }

    @Test
    fun testTargetIdUncalibratedPolicy() {
        val pipeline = DspPipeline()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val simEngine = SimulationEngine(scope) { _, _ -> }

        // Clean ground (No target, air baseline calibrated)
        simEngine.targetType = SimulationTargetType.NO_TARGET
        val (cleanBlock, _) = simEngine.generateSingleBlock()
        pipeline.captureAirBaseline(DoubleArray(70) { cleanBlock.rawSamples[it].toDouble() })
        val cleanResult = pipeline.processBlock(cleanBlock, DspProfile.STABLE)

        assertEquals("Target ID must be 0 when uncalibrated", 0, cleanResult.featureVector.targetId)
        assertFalse("isTargetIdCalibrated must be false without physical VDI dataset", cleanResult.featureVector.isTargetIdCalibrated)

        // Repeatable copper target
        simEngine.targetType = SimulationTargetType.COPPER_COIN
        simEngine.targetAmplitude = 200.0
        val (copperBlock, _) = simEngine.generateSingleBlock(seq = 100L)
        val r1 = pipeline.processBlock(copperBlock, DspProfile.STABLE, updateState = false)
        val r2 = pipeline.processBlock(copperBlock, DspProfile.STABLE, updateState = false)

        assertEquals("Target ID must be 0 (no fake numbers)", 0, r1.featureVector.targetId)
        assertFalse("Target ID must not report as calibrated", r1.featureVector.isTargetIdCalibrated)
        assertEquals("Results must be deterministic", r1.featureVector.targetScore, r2.featureVector.targetScore, 0.001)
    }

    @Test
    fun testPhysicalTimingWindowMapping() {
        // Leonardo default: 1.6 us spacing
        val leonardoConfig = SamplingConfiguration(sampleCount = 70, sampleSpacingUs = 1.6)
        val (startIdx, endIdx) = leonardoConfig.physicalRangeToIndices(12.8, 48.0)
        assertEquals(8, startIdx)
        assertEquals(30, endIdx)

        // Custom high-speed ADC: 0.8 us spacing, 100 samples
        val fastConfig = SamplingConfiguration(sampleCount = 100, sampleSpacingUs = 0.8)
        val (fastStart, fastEnd) = fastConfig.physicalRangeToIndices(12.8, 48.0)
        assertEquals(16, fastStart)
        assertEquals(60, fastEnd)

        // Profile window derivation
        val profile = DspProfile.STABLE
        val (pStart, pEnd) = profile.getIntegrationIndices(leonardoConfig)
        assertTrue(pStart >= 0)
        assertTrue(pEnd <= 70)
        assertTrue(pEnd > pStart)

        val (aStart, aEnd) = profile.getRegionAIndices(leonardoConfig)
        val (bStart, bEnd) = profile.getRegionBIndices(leonardoConfig)
        val (cStart, cEnd) = profile.getRegionCIndices(leonardoConfig)
        assertTrue(aStart <= aEnd)
        assertTrue(bStart <= bEnd)
        assertTrue(cStart <= cEnd)
    }

    @Test
    fun testInvertingPolarityNormalization() {
        val pipeline = DspPipeline()
        val config = SamplingConfiguration(sampleCount = 70, sampleSpacingUs = 1.6, polarity = WaveformPolarity.NEGATIVE)

        // Quiescent background with no target
        val (quiescentBlock, _) = SimulationEngine.generatePhysicsBlock(
            seq = 0L,
            currentAmp = 0.0,
            tauUs = 0.0,
            isFerrous = false,
            groundAmp = 20.0,
            noiseStdDev = 0.0,
            polarity = WaveformPolarity.NEGATIVE,
            config = config
        )
        pipeline.captureAirBaseline(DoubleArray(70) { quiescentBlock.rawSamples[it].toDouble() })

        // Target introduced: voltage deflects downwards (negative)
        val (block, _) = SimulationEngine.generatePhysicsBlock(
            seq = 1L,
            currentAmp = 120.0,
            tauUs = 35.0,
            isFerrous = false,
            groundAmp = 20.0,
            noiseStdDev = 1.0,
            polarity = WaveformPolarity.NEGATIVE,
            config = config
        )

        val result = pipeline.processBlock(block, DspProfile.STABLE, updateState = false)

        assertNotNull(result)
        // Normalized residual should have positive deflection despite negative raw deflection
        assertTrue("Normalized residual peak must be positive (actual: ${result.featureVector.amplitude})", result.featureVector.amplitude > 0.0)
    }

    @Test
    fun testFeatureVectorMeanAndMeanAbsolute() {
        val pipeline = DspPipeline()
        val config = SamplingConfiguration(sampleCount = 70, sampleSpacingUs = 1.6)

        // Block with zero signal
        val (block, _) = SimulationEngine.generatePhysicsBlock(
            seq = 1L,
            currentAmp = 50.0,
            tauUs = 25.0,
            isFerrous = false,
            groundAmp = 0.0,
            noiseStdDev = 0.0,
            polarity = WaveformPolarity.POSITIVE,
            config = config
        )
        val result = pipeline.processBlock(block, DspProfile.STABLE, updateState = false)
        val fv = result.featureVector

        // mean is signed, meanAbsolute is absolute: meanAbsolute must be >= 0.0
        assertTrue("meanAbsolute must be non-negative", fv.meanAbsolute >= 0.0)
        assertTrue("meanAbsolute >= abs(mean)", fv.meanAbsolute >= kotlin.math.abs(fv.mean) - 1e-6)
    }

    @Test
    fun testTauEstimatorScientificDecay() {
        // Ideal exponential decay: V(t) = 100 * exp(-t / 30.0)
        val dt = 1.6
        val trueTau = 30.0
        val sampleCount = 50
        val waveform = DoubleArray(sampleCount) { i ->
            val t = i * dt
            100.0 * kotlin.math.exp(-t / trueTau)
        }

        val result = com.example.felezjoo.dsp.TauEstimator.estimateTau(
            waveform = waveform,
            sampleSpacingUs = dt,
            startIndex = 5,
            endIndex = 35,
            noiseFloor = 1.0,
            minSamples = 4,
            minR2 = 0.95,
            minTauUs = 1.0,
            maxTauUs = 300.0
        )

        assertTrue("Tau fit must be available for clean exponential", result.isAvailable)
        assertEquals("Fitted tau must match true tau within 1%", trueTau, result.tauUs, 0.3)
        assertTrue("R^2 must exceed 0.99 for pure noiseless decay", result.rSquared > 0.99)

        // Invalid: dt <= 0
        val invalidDt = com.example.felezjoo.dsp.TauEstimator.estimateTau(
            waveform = waveform,
            sampleSpacingUs = 0.0,
            startIndex = 5,
            endIndex = 35,
            noiseFloor = 1.0
        )
        assertFalse("Tau must be unavailable when sampleSpacingUs is 0", invalidDt.isAvailable)
        assertTrue("Tau must be NaN when sampleSpacingUs is 0", invalidDt.tauUs.isNaN())
    }

    @Test
    fun testDetectionCalibrationIntegration() {
        val config = SamplingConfiguration(sampleCount = 70, sampleSpacingUs = 1.6)
        val (block, _) = SimulationEngine.generatePhysicsBlock(
            seq = 1L,
            currentAmp = 40.0,
            tauUs = 25.0,
            isFerrous = false,
            groundAmp = 10.0,
            noiseStdDev = 3.0,
            polarity = WaveformPolarity.POSITIVE,
            config = config
        )

        val pipeline = DspPipeline()
        // Profile with default calibration
        val pDefault = DspProfile.STABLE
        val rDefault = pipeline.processBlock(block, pDefault, updateState = false)

        // Profile with custom calibration (higher SNR and Signal requirement)
        val customCal = com.example.felezjoo.models.DetectionCalibration(
            snrReference = 60.0,
            signalFractionRef = 0.50
        )
        val pCustom = pDefault.copy(calibration = customCal)
        val rCustom = pipeline.processBlock(block, pCustom, updateState = false)

        // Higher reference requirements yield lower targetScore
        assertTrue(
            "Custom calibration must dynamically affect score without hardcoded constants (default=${rDefault.featureVector.targetScore}, custom=${rCustom.featureVector.targetScore})",
            rCustom.featureVector.targetScore < rDefault.featureVector.targetScore
        )
    }

    @Test
    fun testRealHardwareEts14x5RoundTrip() {
        val config = SamplingConfiguration(
            pulsesPerFrame = 14,
            samplesPerPulse = 5,
            sampleCount = 70,
            sampleSpacingUs = 1.6
        )

        assertEquals("Pulses per frame must be 14", 14, config.pulsesPerFrame)
        assertEquals("Samples per pulse must be 5", 5, config.samplesPerPulse)
        assertEquals("Total sample count must be 70", 70, config.sampleCount)

        // Explicit formula verification: reconstructedIndex = pulseIndex + sampleSlot * pulsesPerFrame
        for (pulseIndex in 0 until 14) {
            for (sampleSlot in 0 until 5) {
                val expectedReconstructed = pulseIndex + sampleSlot * 14
                val actualReconstructed = EtsReconstruction.getChronologicalIndex(pulseIndex, sampleSlot, config)
                assertEquals(
                    "Reconstructed index must follow pulseIndex + sampleSlot * pulsesPerFrame",
                    expectedReconstructed,
                    actualReconstructed
                )

                val (p, s) = EtsReconstruction.getPulseAndSlot(actualReconstructed, config)
                assertEquals("Reverse pulse index mapping must match", pulseIndex, p)
                assertEquals("Reverse sample slot mapping must match", sampleSlot, s)
            }
        }

        // Test PULSE_FIRST -> CHRONOLOGICAL
        // Transport order: 14 pulses of 5 samples each = 70 samples
        val pulseFirstData = IntArray(70) { it }
        val chronoData = EtsReconstruction.toChronological(pulseFirstData, config, EtsTransportOrder.PULSE_FIRST)
        assertEquals(70, chronoData.size)

        // Verify that sample at pulse p, slot s appears at chrono index (p + s * 14)
        for (p in 0 until 14) {
            for (s in 0 until 5) {
                val transportIdx = p * 5 + s
                val chronoIdx = p + s * 14
                assertEquals(
                    "Chrono sample at $chronoIdx must come from transport $transportIdx",
                    pulseFirstData[transportIdx],
                    chronoData[chronoIdx]
                )
            }
        }

        // Test CHRONOLOGICAL -> PULSE_FIRST round-trip
        val reconstructedPulseFirst = EtsReconstruction.toPulseFirst(chronoData, config)
        assertTrue(
            "CHRONOLOGICAL -> PULSE_FIRST round-trip must be lossless",
            pulseFirstData.contentEquals(reconstructedPulseFirst)
        )

        // Reverse round-trip: Arbitrary chronological decay waveform -> PULSE_FIRST -> CHRONOLOGICAL
        val arbitraryChrono = IntArray(70) { 1023 - it * 12 }
        val transport = EtsReconstruction.toPulseFirst(arbitraryChrono, config)
        val reconstructedChrono = EtsReconstruction.toChronological(transport, config, EtsTransportOrder.PULSE_FIRST)
        assertTrue(
            "PULSE_FIRST -> CHRONOLOGICAL round-trip must be lossless",
            arbitraryChrono.contentEquals(reconstructedChrono)
        )
    }

    @Test
    fun testGroundConfigIsSingleSourceOfTruth() {
        assertEquals(0.005, DspProfile.MAXIMUM_DEPTH.groundConfig.alpha, 1e-6)
        assertEquals(GroundSpeed.SLOW, DspProfile.MAXIMUM_DEPTH.groundConfig.speed)
        assertEquals(0.005, DspProfile.MAXIMUM_DEPTH.groundAlpha, 1e-6)
        assertEquals(GroundSpeed.SLOW, DspProfile.MAXIMUM_DEPTH.groundSpeed)

        assertEquals(0.08, DspProfile.FAST_RESPONSE.groundConfig.alpha, 1e-6)
        assertEquals(GroundSpeed.FAST, DspProfile.FAST_RESPONSE.groundConfig.speed)
        assertEquals(0.08, DspProfile.FAST_RESPONSE.groundAlpha, 1e-6)
        assertEquals(GroundSpeed.FAST, DspProfile.FAST_RESPONSE.groundSpeed)

        assertEquals(0.05, DspProfile.MINERALIZED_GROUND.groundConfig.alpha, 1e-6)
        assertEquals(GroundSpeed.CUSTOM, DspProfile.MINERALIZED_GROUND.groundConfig.speed)
        assertEquals(0.05, DspProfile.MINERALIZED_GROUND.groundAlpha, 1e-6)
        assertEquals(GroundSpeed.CUSTOM, DspProfile.MINERALIZED_GROUND.groundSpeed)

        // copyWithGroundSpeed updates both alpha and speed in groundConfig
        val updated = DspProfile.STABLE.copyWithGroundSpeed(GroundSpeed.FAST)
        assertEquals(0.08, updated.groundConfig.alpha, 1e-6)
        assertEquals(GroundSpeed.FAST, updated.groundConfig.speed)
        assertEquals(0.08, updated.groundAlpha, 1e-6)
        assertEquals(GroundSpeed.FAST, updated.groundSpeed)

        // copyWithGroundAlpha updates both alpha and speed in groundConfig
        val customAlpha = DspProfile.STABLE.copyWithGroundAlpha(0.005)
        assertEquals(0.005, customAlpha.groundConfig.alpha, 1e-6)
        assertEquals(GroundSpeed.SLOW, customAlpha.groundConfig.speed)
        assertEquals(0.005, customAlpha.groundAlpha, 1e-6)
        assertEquals(GroundSpeed.SLOW, customAlpha.groundSpeed)
    }

    @Test
    fun testEarlyAndLateTauIndependentPhysics() {
        val dt = 1.6
        val sampleCount = 70
        val config = SamplingConfiguration(sampleCount = sampleCount, sampleSpacingUs = dt)
        val profile = DspProfile.STABLE // Region A: 8..24 us, Region C: 51.2..96 us

        // Two-component decay: fast transient (tau=10 us) + slow conductive eddy currents (tau=50 us)
        val twoComponentDecay = DoubleArray(sampleCount) { i ->
            val t = i * dt
            450.0 * kotlin.math.exp(-t / 10.0) + 180.0 * kotlin.math.exp(-t / 50.0)
        }

        val rawBlock = DecayBlock(
            sequenceNumber = 100L,
            timestamp = 1000L,
            delayTicks = 12,
            rawSamples = twoComponentDecay.map { it.toInt() }.toIntArray(),
            samplingConfiguration = config
        )

        val pipeline = DspPipeline()
        val result = pipeline.processBlock(rawBlock, profile, updateState = false)
        val fv = result.featureVector

        // 1. Independent calculation verification
        assertTrue("Early Tau must be valid", fv.isEarlyTauValid)
        assertTrue("Late Tau must be valid", fv.isLateTauValid)
        assertTrue("Early Tau fit result must be available", result.earlyTauFitResult.isAvailable)
        assertTrue("Late Tau fit result must be available", result.lateTauFitResult.isAvailable)

        // Early window (8..24 us) is dominated by the 10 us component (tau ~ 13-18 us)
        // Late window (51.2..96 us) is dominated almost purely by the 50 us component (tau ~ 45-52 us)
        assertTrue(
            "Early Tau (${fv.earlyTauUs}) must be significantly faster than Late Tau (${fv.lateTauUs})",
            fv.earlyTauUs < (fv.lateTauUs * 0.6)
        )
        assertTrue("Early Tau must be in sensible range [8, 25] us", fv.earlyTauUs in 8.0..25.0)
        assertTrue("Late Tau must be in sensible range [40, 60] us", fv.lateTauUs in 40.0..60.0)
        assertTrue("tauRatio must reflect early/late tau ratio", fv.tauRatio > 0.0 && fv.tauRatio < 0.6)

        // 2. Independent validity verification: Fast decay that drops below noise floor in late window
        val fastDecayOnly = DoubleArray(sampleCount) { i ->
            val t = i * dt
            if (t < 30.0) 500.0 * kotlin.math.exp(-t / 6.0) else 0.5 // Sub-noise floor in region C
        }
        val fastBlock = DecayBlock(
            sequenceNumber = 101L,
            timestamp = 1010L,
            delayTicks = 12,
            rawSamples = fastDecayOnly.map { it.toInt() }.toIntArray(),
            samplingConfiguration = config
        )

        val fastResult = pipeline.processBlock(fastBlock, profile, updateState = false)
        val fastFv = fastResult.featureVector

        assertTrue("Early Tau must remain valid for fast transient", fastFv.isEarlyTauValid)
        assertFalse("Late Tau must be unavailable when region C lacks signal above noise", fastFv.isLateTauValid)
        assertFalse("Late Tau fit result must report unavailable", fastResult.lateTauFitResult.isAvailable)
        assertTrue("Late Tau fit result tauUs must be NaN", fastResult.lateTauFitResult.tauUs.isNaN())
        assertEquals("Late Tau in FeatureVector must be 0.0 when unavailable", 0.0, fastFv.lateTauUs, 1e-9)
        assertTrue(
            "Late Tau must NEVER be silently copied from effectiveTauUs (effective=${fastFv.effectiveTauUs}, late=${fastFv.lateTauUs})",
            fastFv.lateTauUs != fastFv.effectiveTauUs
        )
    }

    @Test
    fun testEarlyAndLateTauPhysicalTimeInvarianceWithDifferentSpacing() {
        val continuousTau = 22.0
        val continuousAmp = 350.0

        // Configuration 1: spacing = 1.6 us
        val config1 = SamplingConfiguration(sampleCount = 70, sampleSpacingUs = 1.6)
        val samples1 = DoubleArray(config1.sampleCount) { i ->
            val t = i * config1.sampleSpacingUs
            continuousAmp * kotlin.math.exp(-t / continuousTau)
        }
        val block1 = DecayBlock(
            sequenceNumber = 1L,
            timestamp = 100L,
            delayTicks = 12,
            sampleSpacingUs = config1.sampleSpacingUs,
            sampleCount = config1.sampleCount,
            rawSamples = samples1.map { it.toInt() }.toIntArray(),
            samplingConfiguration = config1
        )

        // Configuration 2: spacing = 2.4 us (fewer samples over same total physical duration)
        val config2 = SamplingConfiguration(sampleCount = 46, sampleSpacingUs = 2.4)
        val samples2 = DoubleArray(config2.sampleCount) { i ->
            val t = i * config2.sampleSpacingUs
            continuousAmp * kotlin.math.exp(-t / continuousTau)
        }
        val block2 = DecayBlock(
            sequenceNumber = 2L,
            timestamp = 200L,
            delayTicks = 12,
            sampleSpacingUs = config2.sampleSpacingUs,
            sampleCount = config2.sampleCount,
            rawSamples = samples2.map { it.toInt() }.toIntArray(),
            samplingConfiguration = config2
        )

        val profile = DspProfile.STABLE
        val pipeline = DspPipeline()

        val r1 = pipeline.processBlock(block1, profile, updateState = false)
        val r2 = pipeline.processBlock(block2, profile, updateState = false)

        assertTrue("Config1 early Tau must be valid", r1.featureVector.isEarlyTauValid)
        assertTrue("Config2 early Tau must be valid", r2.featureVector.isEarlyTauValid)

        // Verify that physical microsecond time windowing gives consistent physical Tau regardless of dt
        assertEquals("Early Tau must be invariant to sample spacing within 1.5 us", r1.featureVector.earlyTauUs, r2.featureVector.earlyTauUs, 1.5)
        assertEquals("Effective Tau must be invariant to sample spacing within 1.5 us", r1.featureVector.effectiveTauUs, r2.featureVector.effectiveTauUs, 1.5)
    }

    @Test
    fun testTauEstimatorComprehensiveRobustness() {
        val dt = 1.6
        val sampleCount = 40
        val trueTau = 25.0
        val a0 = 250.0

        // A. Clean exponential decay
        val clean = DoubleArray(sampleCount) { i -> a0 * kotlin.math.exp(-(i * dt) / trueTau) }
        val rClean = TauEstimator.estimateTau(clean, dt, startIndex = 4, endIndex = 35, noiseFloor = 1.0)
        assertTrue("A: Clean decay must be available", rClean.isAvailable)
        assertEquals("A: Clean decay tau must match true tau within 0.5 us", trueTau, rClean.tauUs, 0.5)
        assertTrue("A: Clean R2 must exceed 0.99", rClean.rSquared > 0.99)

        // B. Exponential + deterministic reproducible noise
        val noisy = DoubleArray(sampleCount) { i ->
            val t = i * dt
            // Deterministic bounded pseudorandom variation using sine harmonics
            val noise = 3.5 * kotlin.math.sin(i * 1.7) + 2.0 * kotlin.math.cos(i * 3.1)
            (a0 * kotlin.math.exp(-t / trueTau) + noise).coerceAtLeast(0.0)
        }
        val rNoisy = TauEstimator.estimateTau(noisy, dt, startIndex = 4, endIndex = 35, noiseFloor = 2.0)
        assertTrue("B: Noisy decay must remain available", rNoisy.isAvailable)
        assertEquals("B: Noisy decay tau must remain close to true tau within 2.0 us", trueTau, rNoisy.tauUs, 2.0)

        // C. Exponential with one strong positive spike
        val positiveSpike = clean.clone()
        positiveSpike[14] = positiveSpike[14] * 3.5 // strong outlier spike
        val rPosSpike = TauEstimator.estimateTau(positiveSpike, dt, startIndex = 4, endIndex = 35, noiseFloor = 1.0)
        assertTrue("C: Positive spike must be handled by outlier rejection", rPosSpike.isAvailable)
        assertEquals("C: Tau after positive spike rejection must match true tau within 2.0 us", trueTau, rPosSpike.tauUs, 2.0)

        // D. Exponential with one strong negative disturbance (well above noise floor)
        val negativeSpike = clean.clone()
        negativeSpike[14] = negativeSpike[14] * 0.25 // strong downward glitch
        val rNegSpike = TauEstimator.estimateTau(negativeSpike, dt, startIndex = 4, endIndex = 35, noiseFloor = 1.0)
        assertTrue("D: Negative disturbance must be handled by outlier rejection", rNegSpike.isAvailable)
        assertEquals("D: Tau after negative glitch rejection must match true tau within 2.5 us", trueTau, rNegSpike.tauUs, 2.5)

        // E. Insufficient valid samples (< minSamples)
        val rFew = TauEstimator.estimateTau(clean, dt, startIndex = 4, endIndex = 6, noiseFloor = 1.0, minSamples = 4)
        assertFalse("E: Insufficient samples must return unavailable", rFew.isAvailable)
        assertTrue("E: Tau must be NaN when insufficient samples", rFew.tauUs.isNaN())

        // F. Poor R2 / non-exponential waveform (constant DC)
        val flatWaveform = DoubleArray(sampleCount) { 100.0 }
        val rFlat = TauEstimator.estimateTau(flatWaveform, dt, startIndex = 4, endIndex = 35, noiseFloor = 1.0)
        assertFalse("F: Flat waveform must return unavailable", rFlat.isAvailable)

        // G. DetectionCalibration controls bounds: minTauUs, maxTauUs, minTauR2, noiseThresholdMultiplier
        val calStrictR2 = com.example.felezjoo.models.DetectionCalibration(minTauR2 = 0.999)
        val rStrictR2 = TauEstimator.estimateTau(noisy, dt, startIndex = 4, endIndex = 35, noiseFloor = 2.0, calibration = calStrictR2)
        assertFalse("G: Strict minTauR2 from calibration must reject imperfect fit", rStrictR2.isAvailable)

        val calMinTau = com.example.felezjoo.models.DetectionCalibration(minTauUs = 30.0) // true tau is 25 us < 30 us
        val rMinTau = TauEstimator.estimateTau(clean, dt, startIndex = 4, endIndex = 35, noiseFloor = 1.0, calibration = calMinTau)
        assertFalse("G: Calibration minTauUs must reject faster decays", rMinTau.isAvailable)

        val calMaxTau = com.example.felezjoo.models.DetectionCalibration(maxTauUs = 20.0) // true tau is 25 us > 20 us
        val rMaxTau = TauEstimator.estimateTau(clean, dt, startIndex = 4, endIndex = 35, noiseFloor = 1.0, calibration = calMaxTau)
        assertFalse("G: Calibration maxTauUs must reject slower decays", rMaxTau.isAvailable)

        val calHighNoiseMult = com.example.felezjoo.models.DetectionCalibration(noiseThresholdMultiplier = 50.0) // excludes all samples
        val rHighNoise = TauEstimator.estimateTau(clean, dt, startIndex = 4, endIndex = 35, noiseFloor = 20.0, calibration = calHighNoiseMult)
        assertFalse("G: Calibration noiseThresholdMultiplier must exclude below-threshold points", rHighNoise.isAvailable)
    }

    @Test
    fun testMultiFrameTemporalGroundTrackingSequence() {
        val dt = 1.6
        val sampleCount = 70
        val config = SamplingConfiguration(sampleCount = sampleCount, sampleSpacingUs = dt)
        val profile = DspProfile.STABLE.copy(
            groundConfig = com.example.felezjoo.models.GroundTrackingConfig(
                alpha = 0.05,
                quietFramesRequired = 2,
                freezeScore = 30.0,
                freezeSnr = 4.0
            )
        )

        val pipeline = DspPipeline()

        // Pure soil model
        val soilWaveform = IntArray(sampleCount) { i ->
            (120.0 * kotlin.math.exp(-(i * dt) / 35.0) + 15.0).toInt()
        }

        // Initialize ground model to the soil baseline (simulating completed ground balance)
        pipeline.setGroundDirect(DoubleArray(sampleCount) { i -> soilWaveform[i].toDouble() })

        // Target signal (metal target)
        val targetSignal = IntArray(sampleCount) { i ->
            (400.0 * kotlin.math.exp(-(i * dt) / 14.0)).toInt()
        }

        // ==========================================
        // PHASE 1: Stable soil only (frames 1..5)
        // Ground is allowed to converge.
        // ==========================================
        for (f in 1..5) {
            val soilBlock = DecayBlock(sequenceNumber = f.toLong(), timestamp = 1000L + f * 50, delayTicks = 12, rawSamples = soilWaveform.clone(), samplingConfiguration = config)
            val res = pipeline.processBlock(soilBlock, profile, updateState = true)
            if (f >= 3) {
                assertFalse("Phase 1: Ground tracking must be active (unfrozen) during pure stable soil (frame $f)", res.isGroundFrozen)
            }
        }

        val groundAfterPhase1 = pipeline.groundCurve.clone()

        // ==========================================
        // PHASE 2: Target appears (frames 6..10)
        // Target detection rises. Ground tracking freezes.
        // The target must NOT be absorbed into ground model.
        // ==========================================
        val soilPlusTarget = IntArray(sampleCount) { i -> soilWaveform[i] + targetSignal[i] }
        for (f in 6..10) {
            val targetBlock = DecayBlock(sequenceNumber = f.toLong(), timestamp = 1000L + f * 50, delayTicks = 12, rawSamples = soilPlusTarget.clone(), samplingConfiguration = config)
            val res = pipeline.processBlock(targetBlock, profile, updateState = true)
            assertTrue("Phase 2: Target score must rise (frame $f, score=${res.featureVector.targetScore})", res.featureVector.targetScore >= 30.0)
            assertTrue("Phase 2: Ground tracking MUST freeze when target is detected (frame $f)", res.isGroundFrozen)
            assertTrue("Phase 2: Freeze reason must explain target detection", res.groundFreezeReason.isNotEmpty())
        }

        val groundAfterPhase2 = pipeline.groundCurve
        // Verify that ground curve did NOT absorb target
        for (i in 0 until sampleCount) {
            assertEquals("Phase 2: Ground curve must remain unchanged while frozen (index $i)", groundAfterPhase1[i], groundAfterPhase2[i], 1e-6)
        }

        // ==========================================
        // PHASE 3: Target remains present (frames 11..15)
        // Ground remains protected from learning target as soil.
        // ==========================================
        for (f in 11..15) {
            val targetBlock = DecayBlock(sequenceNumber = f.toLong(), timestamp = 1000L + f * 50, delayTicks = 12, rawSamples = soilPlusTarget.clone(), samplingConfiguration = config)
            val res = pipeline.processBlock(targetBlock, profile, updateState = true)
            assertTrue("Phase 3: Ground must remain frozen while target is held", res.isGroundFrozen)
        }
        val groundAfterPhase3 = pipeline.groundCurve
        for (i in 0 until sampleCount) {
            assertEquals("Phase 3: Ground model must not learn target as soil (index $i)", groundAfterPhase1[i], groundAfterPhase3[i], 1e-6)
        }

        // ==========================================
        // PHASE 4: Target disappears (frames 16..22)
        // Ground tracking does not instantly jump.
        // Hysteresis protects ground while target clears coil envelope.
        // ==========================================
        val res16 = pipeline.processBlock(DecayBlock(sequenceNumber = 16L, timestamp = 1000L + 16 * 50, delayTicks = 12, rawSamples = soilWaveform.clone(), samplingConfiguration = config), profile, updateState = true)
        assertTrue("Phase 4 (frame 16): Ground must remain frozen immediately after target exits", res16.isGroundFrozen)

        // Process quiet frames until tracker clears lingering detection and quiet requirement is fulfilled
        var unfrozenFrame = -1
        for (f in 17..36) {
            val qBlock = DecayBlock(sequenceNumber = f.toLong(), timestamp = 1000L + f * 50, delayTicks = 12, rawSamples = soilWaveform.clone(), samplingConfiguration = config)
            val res = pipeline.processBlock(qBlock, profile, updateState = true)
            if (!res.isGroundFrozen) {
                unfrozenFrame = f
                break
            }
        }
        assertTrue("Phase 4: Ground tracking must resume once persistence and quiet frames conditions are fulfilled (unfrozen=$unfrozenFrame)", unfrozenFrame > 0)

        // ==========================================
        // PHASE 5: Soil baseline slowly drifts with no target (frames 37..50)
        // Ground tracking resumes and follows drifted ground condition according to profile alpha.
        // ==========================================
        val groundBeforeDrift = pipeline.groundCurve.clone()

        for (f in 37..50) {
            val driftFactor = 1.0 + (f - 36) * 0.002 // gradual 0.2% drift per frame
            val driftedSoil = IntArray(sampleCount) { i -> (soilWaveform[i] * driftFactor).toInt() }
            val driftBlock = DecayBlock(sequenceNumber = f.toLong(), timestamp = 1000L + f * 50, delayTicks = 12, rawSamples = driftedSoil, samplingConfiguration = config)
            val res = pipeline.processBlock(driftBlock, profile, updateState = true)
            assertFalse("Phase 5: Ground tracking must actively adapt to gradual soil drift (frame $f, reason=${res.groundFreezeReason})", res.isGroundFrozen)
        }

        val groundAfterPhase5 = pipeline.groundCurve
        val finalDriftedSoil = IntArray(sampleCount) { i -> (soilWaveform[i] * (1.0 + (50 - 36) * 0.002)).toInt() }

        // Verify that ground tracked toward the drifted soil in the active decay region
        assertTrue("Ground curve must track upwards following positive drift at sample 10", groundAfterPhase5[10] > groundBeforeDrift[10])
        assertTrue("Ground curve must track upwards following positive drift at sample 20", groundAfterPhase5[20] > groundBeforeDrift[20])
    }

    @Test
    fun testDifferentGroundAlphaConvergenceSpeeds() {
        val dt = 1.6
        val sampleCount = 70
        val config = SamplingConfiguration(sampleCount = sampleCount, sampleSpacingUs = dt)

        val pipelineFast = DspPipeline()
        val pipelineSlow = DspPipeline()

        // Profile 1: FAST_RESPONSE (alpha = 0.08)
        val profileFast = DspProfile.FAST_RESPONSE
        // Profile 2: MAXIMUM_DEPTH (alpha = 0.005)
        val profileSlow = DspProfile.MAXIMUM_DEPTH

        val initialSoil = IntArray(sampleCount) { 80 }
        val steppedSoil = IntArray(sampleCount) { 140 } // +60 step change in ground level

        // Initialize both pipelines to initial soil (5 quiet frames)
        for (f in 1..5) {
            val b = DecayBlock(sequenceNumber = f.toLong(), timestamp = f * 50L, delayTicks = 12, rawSamples = initialSoil.clone(), samplingConfiguration = config)
            pipelineFast.processBlock(b, profileFast, updateState = true)
            pipelineSlow.processBlock(b, profileSlow, updateState = true)
        }

        // Apply stepped soil for 8 frames
        for (f in 6..13) {
            val b = DecayBlock(sequenceNumber = f.toLong(), timestamp = f * 50L, delayTicks = 12, rawSamples = steppedSoil.clone(), samplingConfiguration = config)
            pipelineFast.processBlock(b, profileFast, updateState = true)
            pipelineSlow.processBlock(b, profileSlow, updateState = true)
        }

        val fastGround = pipelineFast.groundCurve
        val slowGround = pipelineSlow.groundCurve

        val fastDelta = fastGround[20] - initialSoil[20]
        val slowDelta = slowGround[20] - initialSoil[20]

        // Fast profile (alpha=0.08) must adapt significantly more than slow profile (alpha=0.005)
        assertTrue(
            "Fast profile ground adaptation ($fastDelta) must be much greater than slow profile ($slowDelta)",
            fastDelta > slowDelta * 4.0
        )
    }

    @Test
    fun testSemanticHonestyAndBoundaries() {
        val config = SamplingConfiguration(sampleCount = 70, sampleSpacingUs = 1.6)
        val (block, _) = SimulationEngine.generatePhysicsBlock(
            seq = 200L,
            currentAmp = 50.0,
            tauUs = 20.0,
            isFerrous = false,
            groundAmp = 10.0,
            noiseStdDev = 2.0,
            polarity = WaveformPolarity.POSITIVE,
            config = config
        )

        val pipeline = DspPipeline()
        val result = pipeline.processBlock(block, DspProfile.STABLE, updateState = false)
        val fv = result.featureVector

        // 1. Confidence score is heuristic engineering metric 0..100, not probability
        assertEquals("confidenceScore must equal targetConfidence", fv.targetConfidence, fv.confidenceScore, 1e-9)
        assertTrue("confidenceScore must be bounded in [0, 100]", fv.confidenceScore in 0.0..100.0)

        // 2. Ferrous score is heuristic likelihood metric 0..100, not calibrated material identification
        assertEquals("ferrousScore must equal ironScore", fv.ironScore, fv.ferrousScore, 1e-9)
        assertEquals("ferrousLikelihoodScore must equal ironScore", fv.ironScore, fv.ferrousLikelihoodScore, 1e-9)
        assertTrue("ferrousScore must be bounded in [0, 100]", fv.ferrousScore in 0.0..100.0)

        // 3. Effective single-exponential Tau matches estimatedTauUs
        assertEquals("effectiveTauUs must equal estimatedTauUs", fv.estimatedTauUs, fv.effectiveTauUs, 1e-9)
        assertEquals("tauUs must equal estimatedTauUs", fv.estimatedTauUs, fv.tauUs, 1e-9)

        // 4. Target ID is explicitly unavailable
        assertEquals("Target ID must be 0 (unavailable)", 0, fv.targetId)
        assertFalse("Target ID must not report as calibrated", fv.isTargetIdCalibrated)

        // 5. updateState = false guarantees zero mutation of pipeline internal models
        val gBefore = pipeline.groundCurve.clone()
        val bBefore = pipeline.baselineCurve.clone()
        pipeline.processBlock(block, DspProfile.STABLE, updateState = false)
        val gAfter = pipeline.groundCurve
        val bAfter = pipeline.baselineCurve
        assertTrue("updateState=false must cause zero ground mutation", gBefore.contentEquals(gAfter))
        assertTrue("updateState=false must cause zero baseline mutation", bBefore.contentEquals(bAfter))
    }
}
