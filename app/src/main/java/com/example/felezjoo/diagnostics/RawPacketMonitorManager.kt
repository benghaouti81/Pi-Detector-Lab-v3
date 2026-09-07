package com.example.felezjoo.diagnostics

import com.example.felezjoo.protocol.RawPacketRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RawPacketMonitorManager(private val maxRecords: Int = 200) {

    private val _records = MutableStateFlow<List<RawPacketRecord>>(emptyList())
    val records: StateFlow<List<RawPacketRecord>> = _records.asStateFlow()

    private val _selectedRecord = MutableStateFlow<RawPacketRecord?>(null)
    val selectedRecord: StateFlow<RawPacketRecord?> = _selectedRecord.asStateFlow()

    var isPaused: Boolean = false
    private val recordList = mutableListOf<RawPacketRecord>()

    @Synchronized
    fun addRecord(record: RawPacketRecord) {
        if (isPaused) return
        recordList.add(record)
        if (recordList.size > maxRecords) {
            recordList.removeAt(0)
        }
        _records.value = recordList.toList()
    }

    fun selectRecord(record: RawPacketRecord?) {
        _selectedRecord.value = record
    }

    @Synchronized
    fun clear() {
        recordList.clear()
        _records.value = emptyList()
        _selectedRecord.value = null
    }
}
