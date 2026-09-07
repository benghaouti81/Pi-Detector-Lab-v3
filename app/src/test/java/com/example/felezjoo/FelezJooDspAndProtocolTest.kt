package com.example.felezjoo

import com.example.felezjoo.dsp.DspPipeline
import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.DspProfile
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun testDspPipelineProcessing() {
        val pipeline = DspPipeline()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val simEngine = SimulationEngine(scope) { _, _ -> }

        simEngine.targetType = SimulationTargetType.COPPER_COIN
        simEngine.targetAmplitude = 150.0
        val (block, _) = simEngine.generateSingleBlock()

        val result = pipeline.processBlock(block, DspProfile.STABLE)
        assertNotNull(result)
        assertEquals(70, result.filteredCurve.size)
        assertTrue(result.featureVector.targetScore > 0.0)
        assertTrue(result.featureVector.targetConfidence > 0.0)
    }

    @Test
    fun testSimulationTargetVariety() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val simEngine = SimulationEngine(scope) { _, _ -> }

        simEngine.targetType = SimulationTargetType.IRON_NAIL
        val (ironBlock, _) = simEngine.generateSingleBlock()
        assertEquals(70, ironBlock.rawSamples.size)

        simEngine.targetType = SimulationTargetType.SMALL_GOLD_NUGGET
        val (goldBlock, _) = simEngine.generateSingleBlock()
        assertEquals(70, goldBlock.rawSamples.size)
    }
}
