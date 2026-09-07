package com.example.felezjoo.serial

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SerialMonitorManager(private val maxEntries: Int = 800) {

    private val _entries = MutableStateFlow<List<SerialLogEntry>>(emptyList())
    val entries: StateFlow<List<SerialLogEntry>> = _entries.asStateFlow()

    var isPaused: Boolean = false
    var filter: SerialFilter = SerialFilter.ALL
    var displayMode: SerialDisplayMode = SerialDisplayMode.ASCII
    var autoScroll: Boolean = true

    private val entryList = mutableListOf<SerialLogEntry>()

    @Synchronized
    fun addEntry(direction: SerialDirection, text: String, hexDump: String = "", isBinary: Boolean = false) {
        if (isPaused) return
        val entry = SerialLogEntry(
            direction = direction,
            text = text,
            hexDump = hexDump,
            isBinary = isBinary
        )
        entryList.add(entry)
        if (entryList.size > maxEntries) {
            entryList.removeAt(0)
        }
        _entries.value = entryList.toList()
    }

    @Synchronized
    fun addBytes(direction: SerialDirection, bytes: ByteArray, length: Int) {
        if (isPaused || length <= 0) return
        val slice = bytes.copyOf(length)
        val hexDump = slice.take(64).joinToString(" ") { "%02X".format(it) } + if (length > 64) "..." else ""
        val ascii = buildString {
            for (i in 0 until minOf(length, 128)) {
                val b = slice[i].toInt() and 0xFF
                if (b in 32..126) append(b.toChar()) else append('.')
            }
        }
        addEntry(direction, ascii, hexDump, isBinary = true)
    }

    @Synchronized
    fun clear() {
        entryList.clear()
        _entries.value = emptyList()
    }

    fun exportLog(): String {
        return buildString {
            appendLine("=== FELEZJOO PI SERIAL MONITOR EXPORT ===")
            appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Entries: ${entryList.size}")
            appendLine("----------------------------------------")
            for (e in entryList) {
                appendLine("[${e.formattedTime}] [${e.direction}] ${e.text} | HEX: ${e.hexDump}")
            }
        }
    }
}
