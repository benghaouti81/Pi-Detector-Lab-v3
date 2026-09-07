package com.example.felezjoo.serial

import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SerialDirection {
    TX, RX
}

enum class SerialDisplayMode {
    ASCII, HEX, RAW
}

enum class SerialFilter {
    ALL, RX_ONLY, TX_ONLY
}

data class SerialLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: SerialDirection,
    val text: String,
    val hexDump: String = "",
    val isBinary: Boolean = false
) : Serializable {

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    fun getDisplayText(mode: SerialDisplayMode): String {
        return when (mode) {
            SerialDisplayMode.ASCII -> text
            SerialDisplayMode.HEX -> if (hexDump.isNotEmpty()) hexDump else text.toByteArray().joinToString(" ") { "%02X".format(it) }
            SerialDisplayMode.RAW -> "[$formattedTime] ${direction.name}: $text ${if (hexDump.isNotEmpty()) "[$hexDump]" else ""}"
        }
    }
}

enum class CommandStatus {
    PENDING,
    ACKNOWLEDGED,
    FAILED,
    TIMEOUT,
    UNSUPPORTED
}

data class CommandRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val command: String,
    var status: CommandStatus = CommandStatus.PENDING,
    var response: String = "",
    var responseTimeMs: Long = 0L
) : Serializable {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}
