package com.example.felezjoo.protocol

/**
 * CRC-16-CCITT implementation.
 * Polynomial: 0x1021 (x^16 + x^12 + x^5 + 1)
 * Initial Value: 0xFFFF
 */
object Crc16Ccitt {
    private const val POLYNOMIAL = 0x1021
    private const val INITIAL_VALUE = 0xFFFF

    private val TABLE = IntArray(256) { i ->
        var curr = i shl 8
        for (j in 0 until 8) {
            curr = if ((curr and 0x8000) != 0) {
                ((curr shl 1) xor POLYNOMIAL) and 0xFFFF
            } else {
                (curr shl 1) and 0xFFFF
            }
        }
        curr
    }

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = INITIAL_VALUE
        val end = offset + length
        for (i in offset until end) {
            val byte = data[i].toInt() and 0xFF
            val tableIndex = ((crc ushr 8) xor byte) and 0xFF
            crc = ((crc shl 8) xor TABLE[tableIndex]) and 0xFFFF
        }
        return crc
    }

    fun verify(data: ByteArray, offset: Int, length: Int, expectedCrc: Int): Boolean {
        val calculated = compute(data, offset, length)
        return calculated == (expectedCrc and 0xFFFF)
    }
}
