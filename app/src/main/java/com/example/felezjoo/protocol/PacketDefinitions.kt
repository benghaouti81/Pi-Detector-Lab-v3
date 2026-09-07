package com.example.felezjoo.protocol

import java.io.Serializable

object PacketConstants {
    const val SYNC_BYTE_0: Byte = 0xF5.toByte()
    const val SYNC_BYTE_1: Byte = 0x5A.toByte()
    const val CURRENT_PROTOCOL_VERSION: Byte = 0x01.toByte()

    const val TYPE_RAW_BLOCK: Byte = 0x01.toByte()
    const val TYPE_CONFIG: Byte = 0x02.toByte()
    const val TYPE_STATUS: Byte = 0x03.toByte()
    const val TYPE_COMMAND_ACK: Byte = 0x04.toByte()
    const val TYPE_ERROR: Byte = 0x05.toByte()
    const val TYPE_PING: Byte = 0x06.toByte()
    const val TYPE_DEVICE_INFO: Byte = 0x07.toByte()

    // RAW_BLOCK packet sizing:
    // Header: 2 (sync) + 1 (ver) + 1 (type) + 2 (len=154) = 6 bytes
    // Payload: 4 (seq) + 4 (timestamp) + 2 (delay) + 2 (count=70) + 140 (70 samples * 2) + 2 (flags) = 154 bytes
    // CRC: 2 bytes
    // Total: 162 bytes
    const val RAW_BLOCK_EXPECTED_PAYLOAD_LEN = 154
    const val RAW_BLOCK_TOTAL_PACKET_LEN = 162
    const val RAW_BLOCK_SAMPLE_COUNT = 70
}

enum class PacketErrorReason {
    OK,
    BAD_CRC,
    BAD_LENGTH,
    BAD_HEADER,
    UNKNOWN_VERSION,
    UNKNOWN_TYPE,
    SEQUENCE_GAP,
    TRUNCATED_PACKET
}

data class RawPacketRecord(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val packetType: Byte = PacketConstants.TYPE_RAW_BLOCK,
    val packetLength: Int = 0,
    val sequence: Long = 0L,
    val receivedCrc: Int = 0,
    val calculatedCrc: Int = 0,
    val isValid: Boolean = true,
    val errorReason: PacketErrorReason = PacketErrorReason.OK,
    val delayTicks: Int = 0,
    val sampleCount: Int = 0,
    val flags: Int = 0,
    val rawBytes: ByteArray = ByteArray(0)
) : Serializable {

    val sequenceNumber: Long get() = sequence
    val packetCrc: Int get() = receivedCrc
    val formattedTime: String get() = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(timestamp))

    val typeName: String
        get() = when (packetType) {
            PacketConstants.TYPE_RAW_BLOCK -> "RAW_BLOCK (0x01)"
            PacketConstants.TYPE_CONFIG -> "CONFIG (0x02)"
            PacketConstants.TYPE_STATUS -> "STATUS (0x03)"
            PacketConstants.TYPE_COMMAND_ACK -> "ACK (0x04)"
            PacketConstants.TYPE_ERROR -> "ERROR (0x05)"
            PacketConstants.TYPE_PING -> "PING (0x06)"
            PacketConstants.TYPE_DEVICE_INFO -> "DEV_INFO (0x07)"
            else -> "UNKNOWN (0x%02X)".format(packetType)
        }

    fun toHexDump(maxBytes: Int = 162): String {
        return rawBytes.take(maxBytes).joinToString(" ") { "%02X".format(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawPacketRecord
        if (id != other.id) return false
        if (sequence != other.sequence) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}
