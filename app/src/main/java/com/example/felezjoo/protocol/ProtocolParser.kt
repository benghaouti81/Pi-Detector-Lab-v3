package com.example.felezjoo.protocol

import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.SamplingConfiguration
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, memory-safe, fragmented binary protocol and ASCII parser.
 */
class ProtocolParser(
    private val onDecayBlockParsed: (DecayBlock) -> Unit,
    private val onRawPacketRecord: (RawPacketRecord) -> Unit,
    private val onAsciiLineParsed: (String) -> Unit,
    private val onSequenceGapDetected: (expected: Long, actual: Long) -> Unit,
    private val onCrcErrorDetected: (expected: Int, actual: Int) -> Unit
) {
    private val rxBuffer = ByteArrayOutputStream(4096)
    private var lastSequenceNumber: Long = -1L
    private var activeSamplingConfig = SamplingConfiguration()

    fun updateSamplingConfig(config: SamplingConfiguration) {
        this.activeSamplingConfig = config
    }

    @Synchronized
    fun processIncomingBytes(incoming: ByteArray, length: Int) {
        if (length <= 0) return
        rxBuffer.write(incoming, 0, length)

        val bufferBytes = rxBuffer.toByteArray()
        var readIndex = 0
        val bufferLen = bufferBytes.size

        while (readIndex < bufferLen) {
            // Check for ASCII line prefix '#' or standard text lines if not at sync
            if (bufferBytes[readIndex] == '#'.code.toByte()) {
                val newlineIdx = findNewline(bufferBytes, readIndex, bufferLen)
                if (newlineIdx != -1) {
                    val line = String(bufferBytes, readIndex, newlineIdx - readIndex).trim()
                    handleAsciiLine(line)
                    readIndex = newlineIdx + 1
                    // Skip optional '\n' if newline was '\r\n'
                    if (readIndex < bufferLen && bufferBytes[readIndex] == '\n'.code.toByte()) {
                        readIndex++
                    }
                    continue
                }
            }

            // Look for binary sync 0xF5 0x5A
            if (readIndex + 1 < bufferLen &&
                bufferBytes[readIndex] == PacketConstants.SYNC_BYTE_0 &&
                bufferBytes[readIndex + 1] == PacketConstants.SYNC_BYTE_1
            ) {
                // Header is at least 6 bytes: sync(2) + ver(1) + type(1) + len(2)
                if (readIndex + 6 > bufferLen) {
                    // Incomplete header, wait for more data
                    break
                }

                val version = bufferBytes[readIndex + 2]
                val packetType = bufferBytes[readIndex + 3]
                val payloadLen = ((bufferBytes[readIndex + 4].toInt() and 0xFF) or
                        ((bufferBytes[readIndex + 5].toInt() and 0xFF) shl 8))

                // Sanity check length (max reasonable detector payload is 1024 bytes)
                if (payloadLen < 0 || payloadLen > 1024) {
                    // Corrupted header, skip sync and resynchronize
                    val badHeaderRecord = RawPacketRecord(
                        packetType = packetType,
                        packetLength = payloadLen,
                        isValid = false,
                        errorReason = PacketErrorReason.BAD_LENGTH,
                        rawBytes = bufferBytes.copyOfRange(readIndex, readIndex + 6)
                    )
                    onRawPacketRecord(badHeaderRecord)
                    readIndex += 2
                    continue
                }

                val totalExpectedLen = 6 + payloadLen + 2 // header(6) + payload + CRC(2)
                if (readIndex + totalExpectedLen > bufferLen) {
                    // Fragmented packet: entire packet not yet received, wait for next chunk
                    break
                }

                // Complete packet available
                val packetBytes = bufferBytes.copyOfRange(readIndex, readIndex + totalExpectedLen)
                parseCompletePacket(packetBytes, version, packetType, payloadLen)

                readIndex += totalExpectedLen
            } else {
                // Not a sync byte: check if it's an ASCII line (e.g. text debug output)
                val newlineIdx = findNewline(bufferBytes, readIndex, bufferLen)
                if (newlineIdx != -1 && (newlineIdx - readIndex) < 128) {
                    // Check if all bytes are printable ASCII
                    var isAscii = true
                    for (k in readIndex until newlineIdx) {
                        val b = bufferBytes[k].toInt() and 0xFF
                        if (b !in 32..126 && b != '\t'.code) {
                            isAscii = false
                            break
                        }
                    }
                    if (isAscii && (newlineIdx - readIndex) > 0) {
                        val line = String(bufferBytes, readIndex, newlineIdx - readIndex).trim()
                        if (line.isNotEmpty()) {
                            handleAsciiLine(line)
                        }
                        readIndex = newlineIdx + 1
                        if (readIndex < bufferLen && bufferBytes[readIndex] == '\n'.code.toByte()) {
                            readIndex++
                        }
                        continue
                    }
                }

                // Discard single garbage byte to search next
                readIndex++
            }
        }

        // Retain remaining unparsed bytes in rxBuffer
        rxBuffer.reset()
        if (readIndex < bufferLen) {
            rxBuffer.write(bufferBytes, readIndex, bufferLen - readIndex)
        }
    }

    private fun findNewline(bytes: ByteArray, start: Int, end: Int): Int {
        for (i in start until end) {
            if (bytes[i] == '\n'.code.toByte() || bytes[i] == '\r'.code.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun handleAsciiLine(line: String) {
        onAsciiLineParsed(line)
        if (line.startsWith("#CONFIG:")) {
            parseConfigLine(line)
        }
    }

    /**
     * Parses #CONFIG:<sampleCount>,<sampleSpacingNs>,<adcBits>,<mode>,<pulses>,<samplesPerPulse>
     * e.g. #CONFIG:70,1600,10,ETS,14,5
     */
    fun parseConfigLine(line: String) {
        try {
            val content = line.substringAfter("#CONFIG:").trim()
            val tokens = content.split(",")
            if (tokens.size >= 6) {
                val sampleCount = tokens[0].toIntOrNull() ?: 70
                val spacingNs = tokens[1].toDoubleOrNull() ?: 1600.0
                val adcBits = tokens[2].toIntOrNull() ?: 10
                val mode = tokens[3]
                val pulses = tokens[4].toIntOrNull() ?: 14
                val samplesPerPulse = tokens[5].toIntOrNull() ?: 5

                val newConfig = SamplingConfiguration(
                    id = "device_reported_${sampleCount}",
                    sampleCount = sampleCount,
                    sampleSpacingUs = spacingNs / 1000.0,
                    samplingMode = mode,
                    pulsesPerFrame = pulses,
                    samplesPerPulse = samplesPerPulse,
                    adcResolution = adcBits,
                    delayUnitUs = spacingNs / 1000.0
                )
                activeSamplingConfig = newConfig
                onAsciiLineParsed("ACK: Config updated ($sampleCount samples, ${spacingNs / 1000.0}us, $mode)")
            }
        } catch (e: Exception) {
            onAsciiLineParsed("ERR: Config parse error: ${e.message}")
        }
    }

    private fun parseCompletePacket(packetBytes: ByteArray, version: Byte, packetType: Byte, payloadLen: Int) {
        val totalLen = packetBytes.size
        // Expected CRC is last 2 bytes little-endian
        val receivedCrc = ((packetBytes[totalLen - 2].toInt() and 0xFF) or
                ((packetBytes[totalLen - 1].toInt() and 0xFF) shl 8))

        // Compute CRC over bytes 0 until (totalLen - 2)
        val calculatedCrc = Crc16Ccitt.compute(packetBytes, 0, totalLen - 2)

        if (receivedCrc != calculatedCrc) {
            onCrcErrorDetected(calculatedCrc, receivedCrc)
            val badCrcRecord = RawPacketRecord(
                packetType = packetType,
                packetLength = payloadLen,
                receivedCrc = receivedCrc,
                calculatedCrc = calculatedCrc,
                isValid = false,
                errorReason = PacketErrorReason.BAD_CRC,
                rawBytes = packetBytes
            )
            onRawPacketRecord(badCrcRecord)
            return
        }

        if (version != PacketConstants.CURRENT_PROTOCOL_VERSION) {
            val badVersionRecord = RawPacketRecord(
                packetType = packetType,
                packetLength = payloadLen,
                receivedCrc = receivedCrc,
                calculatedCrc = calculatedCrc,
                isValid = false,
                errorReason = PacketErrorReason.UNKNOWN_VERSION,
                rawBytes = packetBytes
            )
            onRawPacketRecord(badVersionRecord)
            return
        }

        when (packetType) {
            PacketConstants.TYPE_RAW_BLOCK -> {
                parseRawBlock(packetBytes, payloadLen, receivedCrc, calculatedCrc)
            }
            else -> {
                // General or command/status packet
                val record = RawPacketRecord(
                    packetType = packetType,
                    packetLength = payloadLen,
                    receivedCrc = receivedCrc,
                    calculatedCrc = calculatedCrc,
                    isValid = true,
                    errorReason = PacketErrorReason.OK,
                    rawBytes = packetBytes
                )
                onRawPacketRecord(record)
            }
        }
    }

    private fun parseRawBlock(packetBytes: ByteArray, payloadLen: Int, receivedCrc: Int, calculatedCrc: Int) {
        val bb = ByteBuffer.wrap(packetBytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(6) // Skip 2 sync, 1 ver, 1 type, 2 len

        val sequence = bb.getInt().toLong() and 0xFFFFFFFFL
        val timestamp = bb.getInt().toLong() and 0xFFFFFFFFL
        val delayTicks = bb.getShort().toInt() and 0xFFFF
        val sampleCount = bb.getShort().toInt() and 0xFFFF

        if (sampleCount <= 0 || sampleCount > 512) {
            val errRecord = RawPacketRecord(
                packetType = PacketConstants.TYPE_RAW_BLOCK,
                packetLength = payloadLen,
                sequence = sequence,
                receivedCrc = receivedCrc,
                calculatedCrc = calculatedCrc,
                isValid = false,
                errorReason = PacketErrorReason.BAD_LENGTH,
                delayTicks = delayTicks,
                sampleCount = sampleCount,
                rawBytes = packetBytes
            )
            onRawPacketRecord(errRecord)
            return
        }

        // Sequence gap check
        if (lastSequenceNumber != -1L && sequence > lastSequenceNumber + 1L) {
            onSequenceGapDetected(lastSequenceNumber + 1L, sequence)
        }
        lastSequenceNumber = sequence

        val samples = IntArray(sampleCount)
        for (i in 0 until sampleCount) {
            samples[i] = bb.getShort().toInt() and 0xFFFF
        }
        val flags = bb.getShort().toInt() and 0xFFFF

        val record = RawPacketRecord(
            packetType = PacketConstants.TYPE_RAW_BLOCK,
            packetLength = payloadLen,
            sequence = sequence,
            receivedCrc = receivedCrc,
            calculatedCrc = calculatedCrc,
            isValid = true,
            errorReason = PacketErrorReason.OK,
            delayTicks = delayTicks,
            sampleCount = sampleCount,
            flags = flags,
            rawBytes = packetBytes
        )
        onRawPacketRecord(record)

        val config = if (activeSamplingConfig.sampleCount == sampleCount) {
            activeSamplingConfig
        } else {
            activeSamplingConfig.copy(sampleCount = sampleCount)
        }

        val decayBlock = DecayBlock(
            sequenceNumber = sequence,
            timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis(),
            delayTicks = delayTicks,
            delayUs = delayTicks * config.delayUnitUs,
            sampleSpacingUs = config.sampleSpacingUs,
            sampleCount = sampleCount,
            rawSamples = samples,
            flags = flags,
            samplingConfiguration = config
        )

        onDecayBlockParsed(decayBlock)
    }

    @Synchronized
    fun reset() {
        rxBuffer.reset()
        lastSequenceNumber = -1L
    }
}
