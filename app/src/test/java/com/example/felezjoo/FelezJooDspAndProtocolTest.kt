package com.example.felezjoo

import com.example.felezjoo.dsp.DspPipeline
import com.example.felezjoo.dsp.EtsReconstruction
import com.example.felezjoo.dsp.EtsTransportOrder
import com.example.felezjoo.dsp.NoiseEstimator
import com.example.felezjoo.dsp.SafeGroundTracker
import com.example.felezjoo.dsp.TauEstimator
import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.DspProfile
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

        // Capture snapshot before read-only call
        val initialGround = pipeline.groundCurve.clone()
        val initialBaseline = pipeline.baselineCurve.clone()

        // Call with updateState = false (inspection mode)
        val result = pipeline.processBlock(block, DspProfile.STABLE, updateState = false)

        assertNotNull(result)
        // Assert ground and baseline were NOT mutated
        for (i in initialGround.indices) {
            assertEquals("Ground model must not mutate during read-only call", initialGround[i], pipeline.groundCurve[i], 0.0001)
            assertEquals("Baseline model must not mutate during read-only call", initialBaseline[i], pipeline.baselineCurve[i], 0.0001)
        }
    }

    @Test
    fun testDeterministicTargetId() {
        val pipeline = DspPipeline()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val simEngine = SimulationEngine(scope) { _, _ -> }

        // Clean ground (No target, air baseline calibrated)
        simEngine.targetType = SimulationTargetType.NO_TARGET
        val (cleanBlock, _) = simEngine.generateSingleBlock()
        pipeline.captureAirBaseline(DoubleArray(70) { cleanBlock.rawSamples[it].toDouble() })
        val cleanResult = pipeline.processBlock(cleanBlock, DspProfile.STABLE)

        assertEquals("Target ID must be exactly 0 when no target is present (No fake numbers)", 0, cleanResult.featureVector.targetId)

        // Repeatable copper target
        simEngine.targetType = SimulationTargetType.COPPER_COIN
        simEngine.targetAmplitude = 200.0
        val (copperBlock, _) = simEngine.generateSingleBlock(seq = 100L)
        val r1 = pipeline.processBlock(copperBlock, DspProfile.STABLE, updateState = false)
        val r2 = pipeline.processBlock(copperBlock, DspProfile.STABLE, updateState = false)

        assertEquals("Target ID must be deterministic across runs", r1.featureVector.targetId, r2.featureVector.targetId)
        assertTrue("Copper coin target ID must be > 0", r1.featureVector.targetId > 0)
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
}
