package com.example.felezjoo.serial

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommandConsoleManager(
    private val scope: CoroutineScope,
    private val onSendCommand: suspend (String) -> Boolean
) {
    private val _commandHistory = MutableStateFlow<List<CommandRecord>>(emptyList())
    val commandHistory: StateFlow<List<CommandRecord>> = _commandHistory.asStateFlow()

    private val historyList = mutableListOf<CommandRecord>()
    private val recentInputStrings = mutableListOf<String>()

    val quickCommands = listOf(
        "PING",
        "START",
        "STOP",
        "CONFIG?",
        "STATUS",
        "SET:DELAY=10",
        "SET:PULSE=150",
        "SET:FREQ=200",
        "RESET"
    )

    fun send(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        if (!recentInputStrings.contains(trimmed)) {
            recentInputStrings.add(trimmed)
        }

        val record = CommandRecord(command = trimmed, status = CommandStatus.PENDING)
        synchronized(this) {
            historyList.add(record)
            _commandHistory.value = historyList.toList()
        }

        scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val success = onSendCommand(trimmed)
            if (!success) {
                record.status = CommandStatus.FAILED
                record.response = "Failed to transmit"
                notifyUpdated()
                return@launch
            }

            // Await response or timeout
            val timeoutMs = 2500L
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (record.status != CommandStatus.PENDING) {
                    notifyUpdated()
                    return@launch
                }
                delay(50L)
            }

            if (record.status == CommandStatus.PENDING) {
                record.status = CommandStatus.TIMEOUT
                record.response = "No response within ${timeoutMs}ms"
                notifyUpdated()
            }
        }
    }

    @Synchronized
    fun onResponseReceived(responseText: String) {
        val pending = historyList.lastOrNull { it.status == CommandStatus.PENDING }
        if (pending != null) {
            pending.response = responseText
            pending.responseTimeMs = System.currentTimeMillis() - pending.timestamp
            pending.status = if (responseText.startsWith("ERR") || responseText.startsWith("ERROR")) {
                CommandStatus.FAILED
            } else {
                CommandStatus.ACKNOWLEDGED
            }
            notifyUpdated()
        }
    }

    @Synchronized
    fun clearHistory() {
        historyList.clear()
        _commandHistory.value = emptyList()
    }

    private fun notifyUpdated() {
        _commandHistory.value = historyList.toList()
    }
}
