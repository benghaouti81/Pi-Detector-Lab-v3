package com.example.felezjoo.simulation

import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.SamplingConfiguration
import com.example.felezjoo.protocol.PacketGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.exp
import kotlin.math.sin

enum class SimulationTargetType(val displayName: String, val baseTau: Double, val isFerrous: Boolean) {
    NO_TARGET("No Target (Clean Ground)", 0.0, false),
    IRON_NAIL("Iron Nail (Ferrous Signature)", 1.2, true),
    RUSTY_CAN("Rusty Iron Plate (Strong Ferrous)", 1.6, true),
    SMALL_GOLD_NUGGET("Small Gold Nugget", 2.2, false),
    BRASS_RELIC("Brass Relic", 3.2, false),
    COPPER_COIN("Copper Coin (High Conductivity)", 4.5, false),
    SILVER_COIN("Silver Coin (Long Decay Tau)", 5.8, false),
    ALUMINUM_PULLTAB("Aluminum Pulltab", 2.8, false)
}

class SimulationEngine(
    private val scope: CoroutineScope,
    private val onBlockGenerated: (DecayBlock, ByteArray) -> Unit
) {
    var isRunning: Boolean = false
        private set

    var targetType: SimulationTargetType = SimulationTargetType.COPPER_COIN
    var targetAmplitude: Double = 120.0
    var groundAmplitude: Double = 35.0
    var noiseLevel: Double = 3.5
    var isSweeping: Boolean = true

    private var simJob: Job? = null
    private var sequenceNumber = 0L
    private val random = Random()
    private var sweepPhase = 0.0

    fun start() {
        if (isRunning) return
        isRunning = true
        simJob = scope.launch(Dispatchers.Default) {
            val sampleCount = 70
            val config = SamplingConfiguration(sampleCount = sampleCount, sampleSpacingUs = 1.6)

            while (isActive && isRunning) {
                sequenceNumber++
                val now = System.currentTimeMillis()

                // Calculate sweep envelope if sweeping
                val sweepEnvelope = if (isSweeping) {
                    sweepPhase += 0.12
                    val s = sin(sweepPhase)
                    if (s > 0.1) (s * s) else 0.0
                } else {
                    1.0
                }

                val currentAmp = targetAmplitude * sweepEnvelope
                val samples = IntArray(sampleCount)

                // Physical Pulse Induction model:
                // Base ADC ground level ~ 120
                // + Coil flyback recovery (steep exponential early transient)
                // + Ground mineral curve (slow exponential baseline)
                // + Target eddy current exponential decay: A * exp(-t / tau)
                // + Gaussian noise
                for (i in 0 until sampleCount) {
                    val tUs = i * config.sampleSpacingUs

                    // 1. Coil recovery early transient (clears by ~10us)
                    val flyback = 650.0 * exp(-tUs / 2.2)

                    // 2. Ground mineralization decay
                    val ground = groundAmplitude * exp(-tUs / 18.0)

                    // 3. Target decay response
                    val targetSignal = if (targetType != SimulationTargetType.NO_TARGET && currentAmp > 0.1) {
                        val tau = targetType.baseTau
                        if (targetType.isFerrous) {
                            // Ferrous: rapid early decay with steep initial curvature and zero late response
                            currentAmp * exp(-tUs / (tau * 1.8)) * (1.0 / (1.0 + tUs * 0.15))
                        } else {
                            // Non-ferrous: smooth sustained exponential decay
                            currentAmp * exp(-tUs / (tau * 4.2))
                        }
                    } else {
                        0.0
                    }

                    // 4. Electronic Noise
                    val noise = random.nextGaussian() * noiseLevel

                    val adcValue = (120.0 + flyback + ground + targetSignal + noise).toInt().coerceIn(0, 1023)
                    samples[i] = adcValue
                }

                val decayBlock = DecayBlock(
                    sequenceNumber = sequenceNumber,
                    timestamp = now,
                    pulseRate = 200,
                    pulseWidthUs = 150,
                    delayTicks = 10,
                    delayUs = 16.0,
                    sampleSpacingUs = 1.6,
                    sampleCount = sampleCount,
                    rawSamples = samples,
                    firmwareVersion = "2.0-SIM",
                    protocolVersion = "1.0",
                    flags = if (currentAmp > 40.0) 0x01 else 0x00,
                    samplingConfiguration = config
                )

                // Generate real binary 162-byte packet for simulation
                val rawPacketBytes = PacketGenerator.createRawBlockPacket(
                    sequence = sequenceNumber,
                    timestamp = now,
                    delayTicks = 10,
                    sampleCount = sampleCount,
                    samples = samples,
                    flags = decayBlock.flags
                )

                onBlockGenerated(decayBlock, rawPacketBytes)

                // ~14 blocks per second (71 ms interval)
                delay(71L)
            }
        }
    }

    fun stop() {
        isRunning = false
        simJob?.cancel()
        simJob = null
    }

    fun generateSingleBlock(seq: Long = 1L): Pair<DecayBlock, ByteArray> {
        val sampleCount = 70
        val config = SamplingConfiguration(sampleCount = sampleCount, sampleSpacingUs = 1.6)
        val now = System.currentTimeMillis()
        val currentAmp = targetAmplitude
        val samples = IntArray(sampleCount)

        for (i in 0 until sampleCount) {
            val tUs = i * config.sampleSpacingUs
            val flyback = 650.0 * exp(-tUs / 2.2)
            val ground = groundAmplitude * exp(-tUs / 18.0)
            val targetSignal = if (targetType != SimulationTargetType.NO_TARGET && currentAmp > 0.1) {
                val tau = targetType.baseTau
                if (targetType.isFerrous) {
                    currentAmp * exp(-tUs / (tau * 1.8)) * (1.0 / (1.0 + tUs * 0.15))
                } else {
                    currentAmp * exp(-tUs / (tau * 4.2))
                }
            } else {
                0.0
            }
            val noise = random.nextGaussian() * noiseLevel
            val adcValue = (120.0 + flyback + ground + targetSignal + noise).toInt().coerceIn(0, 1023)
            samples[i] = adcValue
        }

        val decayBlock = DecayBlock(
            sequenceNumber = seq,
            timestamp = now,
            pulseRate = 200,
            pulseWidthUs = 150,
            delayTicks = 10,
            delayUs = 16.0,
            sampleSpacingUs = 1.6,
            sampleCount = sampleCount,
            rawSamples = samples,
            firmwareVersion = "2.0-SIM",
            protocolVersion = "1.0",
            flags = if (currentAmp > 40.0) 0x01 else 0x00,
            samplingConfiguration = config
        )

        val rawPacketBytes = PacketGenerator.createRawBlockPacket(
            sequence = seq,
            timestamp = now,
            delayTicks = 10,
            sampleCount = sampleCount,
            samples = samples,
            flags = decayBlock.flags
        )

        return Pair(decayBlock, rawPacketBytes)
    }
}
