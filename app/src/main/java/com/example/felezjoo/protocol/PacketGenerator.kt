package com.example.felezjoo.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketGenerator {

    /**
     * Builds a real 162-byte RAW_BLOCK packet with valid CRC-16-CCITT little endian.
     */
    fun createRawBlockPacket(
        sequence: Long,
        timestamp: Long,
        delayTicks: Int,
        sampleCount: Int = 70,
        samples: IntArray,
        flags: Int = 0
    ): ByteArray {
        val payloadLen = 4 + 4 + 2 + 2 + (sampleCount * 2) + 2 // 154 for count=70
        val totalLen = 6 + payloadLen + 2 // 162 bytes
        val bb = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)

        // Header (6 bytes)
        bb.put(PacketConstants.SYNC_BYTE_0)
        bb.put(PacketConstants.SYNC_BYTE_1)
        bb.put(PacketConstants.CURRENT_PROTOCOL_VERSION)
        bb.put(PacketConstants.TYPE_RAW_BLOCK)
        bb.putShort(payloadLen.toShort())

        // Payload
        bb.putInt(sequence.toInt())
        bb.putInt(timestamp.toInt())
        bb.putShort(delayTicks.toShort())
        bb.putShort(sampleCount.toShort())
        for (i in 0 until sampleCount) {
            val s = if (i < samples.size) samples[i] else 0
            bb.putShort(s.toShort())
        }
        bb.putShort(flags.toShort())

        // CRC-16 over all previous bytes
        val dataBeforeCrc = bb.array()
        val crc = Crc16Ccitt.compute(dataBeforeCrc, 0, totalLen - 2)
        bb.putShort(crc.toShort())

        return bb.array()
    }
}
