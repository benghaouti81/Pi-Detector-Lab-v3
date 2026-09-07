package com.example.felezjoo.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class DiagnosticLog(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val component: String,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

object SystemDiagnostics {
    private val _logs = MutableStateFlow<List<DiagnosticLog>>(emptyList())
    val logs: StateFlow<List<DiagnosticLog>> = _logs.asStateFlow()

    private val logList = mutableListOf<DiagnosticLog>()
    private const val MAX_LOGS = 500

    @Volatile
    var lastDspDurationMs: Long = 0L

    @Synchronized
    fun log(level: LogLevel, component: String, message: String) {
        val entry = DiagnosticLog(level = level, component = component, message = message)
        logList.add(entry)
        if (logList.size > MAX_LOGS) {
            logList.removeAt(0)
        }
        _logs.value = logList.toList()
    }

    fun debug(component: String, message: String) = log(LogLevel.DEBUG, component, message)
    fun info(component: String, message: String) = log(LogLevel.INFO, component, message)
    fun warn(component: String, message: String) = log(LogLevel.WARN, component, message)
    fun error(component: String, message: String) = log(LogLevel.ERROR, component, message)

    @Synchronized
    fun clearLogs() {
        logList.clear()
        _logs.value = emptyList()
    }

    fun exportLogsText(): String {
        return buildString {
            appendLine("=== FELEZJOO PI 2.0 DIAGNOSTICS LOG ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Total Entries: ${logList.size}")
            appendLine("----------------------------------------")
            for (l in logList) {
                appendLine("[${l.formattedTime}] [${l.level}] [${l.component}] ${l.message}")
            }
        }
    }
}
