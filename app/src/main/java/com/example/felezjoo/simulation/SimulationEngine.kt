package com.example.felezjoo.simulation

import com.example.felezjoo.dsp.EtsReconstruction
import com.example.felezjoo.dsp.EtsTransportOrder
import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.SamplingConfiguration
import com.example.felezjoo.models.WaveformPolarity
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

enum class SimulationTargetType(
    val displayName: String,
    val physicalTauUs: Double,
    val isFerrous: Boolean
) {
    NO_TARGET("No Target (Clean Ground)", 0.0, false),
    IRON_NAIL("Iron Nail (Fast Decay Ferrous)", 5.0, true),
    RUSTY_CAN("Rusty Iron Plate (Strong Ferrous)", 7.5, true),
    SMALL_GOLD_NUGGET("Small Gold Nugget (Short Tau)", 12.0, false),
    BRASS_RELIC("Brass Relic (Medium Tau)", 22.0, false),
    COPPER_COIN("Copper Coin (High Tau)", 38.0, false),
    SILVER_COIN("Silver Coin (Long Tau)", 55.0, false),
    ALUMINUM_PULLTAB("Aluminum Pulltab", 18.0, false)
}

/**
 * Scientific simulation engine for Pulse Induction research and testing.
 * Implements actual electromagnetic decay physics:
 * V_coil(t) = V_bias + V_flyback(t) + V_ground(t) + [polaritySign * V_target(t)] + noise(t)
 */
class SimulationEngine(
    private val scope: CoroutineScope,
    private val onBlockGenerated: (DecayBlock, ByteArray) -> Unit
) {
    var isRunning: Boolean = false
        private set

    var targetType: SimulationTargetType = SimulationTargetType.COPPER_COIN
    var targetAmplitude: Double = 120.0
    var targetTauUsOverride: Double? = null
    var groundAmplitude: Double = 35.0
    var noiseLevel: Double = 3.5
    var isSweeping: Boolean = true
    var polarity: WaveformPolarity = WaveformPolarity.POSITIVE
    var transportOrder: EtsTransportOrder = EtsTransportOrder.CHRONOLOGICAL
    var activeSamplingConfig: SamplingConfiguration = SamplingConfiguration()

    private var simJob: Job? = null
    private var sequenceNumber = 0L
    private val random = Random()
    private var sweepPhase = 0.0

    fun start() {
        if (isRunning) return
        isRunning = true
        simJob = scope.launch(Dispatchers.Default) {
            val config = activeSamplingConfig.copy(
                polarity = polarity,
                transportOrder = transportOrder
            )

            while (isActive && isRunning) {
                sequenceNumber++
                val now = System.currentTimeMillis()

                val sweepEnvelope = if (isSweeping) {
                    sweepPhase += 0.12
                    val s = sin(sweepPhase)
                    if (s > 0.1) (s * s) else 0.0
                } else {
                    1.0
                }

                val currentAmp = targetAmplitude * sweepEnvelope
                val tauUs = targetTauUsOverride ?: targetType.physicalTauUs

                val (block, rawPacketBytes) = generatePhysicsBlock(
                    seq = sequenceNumber,
                    timestamp = now,
                    currentAmp = currentAmp,
                    tauUs = tauUs,
                    isFerrous = targetType.isFerrous,
                    groundAmp = groundAmplitude,
                    noiseStdDev = noiseLevel,
                    polarity = polarity,
                    config = config,
                    transportOrder = transportOrder,
                    rng = random
                )

                onBlockGenerated(block, rawPacketBytes)

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
        val config = activeSamplingConfig.copy(
            polarity = polarity,
            transportOrder = transportOrder
        )
        val tauUs = targetTauUsOverride ?: targetType.physicalTauUs

        return generatePhysicsBlock(
            seq = seq,
            timestamp = System.currentTimeMillis(),
            currentAmp = targetAmplitude,
            tauUs = tauUs,
            isFerrous = targetType.isFerrous,
            groundAmp = groundAmplitude,
            noiseStdDev = noiseLevel,
            polarity = polarity,
            config = config,
            transportOrder = transportOrder,
            rng = random
        )
    }

    companion object {
        /**
         * Deterministic physics waveform synthesis with configurable electromagnetic parameters.
         */
        fun generatePhysicsBlock(
            seq: Long = 1L,
            timestamp: Long = System.currentTimeMillis(),
            currentAmp: Double,
            tauUs: Double,
            isFerrous: Boolean,
            groundAmp: Double,
            noiseStdDev: Double,
            polarity: WaveformPolarity,
            config: SamplingConfiguration,
            transportOrder: EtsTransportOrder = EtsTransportOrder.CHRONOLOGICAL,
            rng: Random = Random(seq)
        ): Pair<DecayBlock, ByteArray> {
            val sampleCount = config.sampleCount
            val chronologicalSamples = IntArray(sampleCount)
            val maxAdc = config.maxAdcValue

            for (i in 0 until sampleCount) {
                val tUs = i * config.sampleSpacingUs

                // 1. Transmitter coil flyback transient (exponential dissipation)
                val flyback = 650.0 * exp(-tUs / 2.2)

                // 2. Soil ground mineralization response
                val ground = groundAmp * exp(-tUs / 22.0)

                // 3. Target eddy current decay: V(t) = A * exp(-t / tau)
                val targetEddy = if (currentAmp > 0.1 && tauUs > 0.5) {
                    if (isFerrous) {
                        currentAmp * exp(-tUs / tauUs) * (1.0 / (1.0 + tUs * 0.12))
                    } else {
                        currentAmp * exp(-tUs / tauUs)
                    }
                } else {
                    0.0
                }

                // Invert deflection if analog front end is inverting
                val polaritySign = polarity.sign
                val targetDeflection = targetEddy * polaritySign

                // 4. Electronic thermal and AFE noise
                val noise = if (noiseStdDev > 0.0) rng.nextGaussian() * noiseStdDev else 0.0

                // Baseline ADC center level
                val baseLevel = if (polarity == WaveformPolarity.NEGATIVE) 850.0 else 120.0
                val adcVal = (baseLevel + flyback + ground + targetDeflection + noise).toInt().coerceIn(0, maxAdc)
                chronologicalSamples[i] = adcVal
            }

            // If transport order is pulse-first, reorder before packetizing
            val transportSamples = if (transportOrder == EtsTransportOrder.PULSE_FIRST) {
                EtsReconstruction.toPulseFirst(chronologicalSamples, config)
            } else {
                chronologicalSamples
            }

            val decayBlock = DecayBlock(
                sequenceNumber = seq,
                timestamp = timestamp,
                pulseRate = 200,
                pulseWidthUs = 150,
                delayTicks = 10,
                delayUs = config.delayUs,
                sampleSpacingUs = config.sampleSpacingUs,
                sampleCount = sampleCount,
                rawSamples = transportSamples,
                firmwareVersion = "2.1-SIM",
                protocolVersion = "1.0",
                flags = if (currentAmp > 35.0) 0x01 else 0x00,
                samplingConfiguration = config,
                polarity = polarity
            )

            val rawPacketBytes = PacketGenerator.createRawBlockPacket(
                sequence = seq,
                timestamp = timestamp,
                delayTicks = 10,
                sampleCount = sampleCount,
                samples = transportSamples,
                flags = decayBlock.flags
            )

            return Pair(decayBlock, rawPacketBytes)
        }
    }
}
