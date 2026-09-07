package com.example.felezjoo.storage

import com.example.felezjoo.models.DecayBlock
import com.example.felezjoo.models.SamplingConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object DataExportHelper {

    fun generateCsv(blocks: List<DecayBlockEntity>): String {
        return buildString {
            // Header
            append("timestamp,sequence,delayTicks,delayUs,pulseRate,pulseWidth,")
            for (i in 0 until 70) {
                append("raw$i,")
            }
            appendLine("targetScore,confidence,ironScore,targetId,classification")

            // Rows
            for (b in blocks) {
                append("${b.timestamp},${b.sequenceNumber},${b.delayTicks},${b.delayUs},${b.pulseRate},${b.pulseWidthUs},")
                val sampleParts = b.rawSamplesCsv.split(",")
                for (i in 0 until 70) {
                    val s = sampleParts.getOrNull(i) ?: "0"
                    append("$s,")
                }
                appendLine("${b.targetScore},${b.confidence},${b.ironScore},${b.targetId},${b.classification}")
            }
        }
    }

    fun generateJson(session: SessionEntity, blocks: List<DecayBlockEntity>, events: List<TargetEventEntity>): String {
        return buildString {
            appendLine("{")
            appendLine("  \"session\": {")
            appendLine("    \"id\": \"${session.id}\",")
            appendLine("    \"name\": \"${session.name}\",")
            appendLine("    \"startTime\": ${session.startTimeMs},")
            appendLine("    \"endTime\": ${session.endTimeMs},")
            appendLine("    \"device\": \"${session.deviceName}\",")
            appendLine("    \"totalBlocks\": ${blocks.size},")
            appendLine("    \"totalEvents\": ${events.size}")
            appendLine("  },")
            appendLine("  \"events\": [")
            for ((idx, ev) in events.withIndex()) {
                append("    {\"id\": \"${ev.id}\", \"peakScore\": ${ev.peakScore}, \"confidence\": ${ev.peakConfidence}, \"ironScore\": ${ev.ironScore}, \"classification\": \"${ev.classification}\"}")
                if (idx < events.size - 1) append(",")
                appendLine()
            }
            appendLine("  ],")
            appendLine("  \"blocks\": [")
            for ((idx, b) in blocks.withIndex()) {
                append("    {\"seq\": ${b.sequenceNumber}, \"time\": ${b.timestamp}, \"score\": ${b.targetScore}, \"samples\": [${b.rawSamplesCsv}]}")
                if (idx < blocks.size - 1) append(",")
                appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }
    }
}

class ReplayEngine(
    private val scope: CoroutineScope,
    private val onBlockReplayed: (DecayBlock) -> Unit
) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _totalBlocks = MutableStateFlow(0)
    val totalBlocks: StateFlow<Int> = _totalBlocks.asStateFlow()

    var replaySpeed: Float = 1.0f // 0.25f, 0.5f, 1.0f, 2.0f, 4.0f
    private var replayJob: Job? = null
    private var recordedBlocks: List<DecayBlock> = emptyList()

    fun loadBlocks(entities: List<DecayBlockEntity>) {
        pause()
        recordedBlocks = entities.map { entity ->
            val sampleInts = entity.rawSamplesCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            val finalSamples = if (sampleInts.isNotEmpty()) sampleInts else IntArray(70)
            DecayBlock(
                sequenceNumber = entity.sequenceNumber,
                timestamp = entity.timestamp,
                pulseRate = entity.pulseRate,
                pulseWidthUs = entity.pulseWidthUs,
                delayTicks = entity.delayTicks,
                delayUs = entity.delayUs,
                sampleCount = finalSamples.size,
                rawSamples = finalSamples,
                samplingConfiguration = SamplingConfiguration(sampleCount = finalSamples.size)
            )
        }
        _totalBlocks.value = recordedBlocks.size
        _currentIndex.value = 0
    }

    fun play() {
        if (recordedBlocks.isEmpty()) return
        _isPlaying.value = true
        replayJob?.cancel()
        replayJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isPlaying.value) {
                val idx = _currentIndex.value
                if (idx >= recordedBlocks.size) {
                    _isPlaying.value = false
                    break
                }
                val block = recordedBlocks[idx]
                onBlockReplayed(block)
                _currentIndex.value = idx + 1

                val baseIntervalMs = 71L // ~14 fps
                val adjustedInterval = (baseIntervalMs / replaySpeed.coerceAtLeast(0.1f)).toLong().coerceAtLeast(10L)
                delay(adjustedInterval)
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        replayJob?.cancel()
        replayJob = null
    }

    fun restart() {
        pause()
        _currentIndex.value = 0
        if (recordedBlocks.isNotEmpty()) {
            onBlockReplayed(recordedBlocks[0])
        }
    }

    fun stepForward() {
        pause()
        val next = (_currentIndex.value + 1).coerceAtMost(recordedBlocks.size - 1)
        _currentIndex.value = next
        if (recordedBlocks.isNotEmpty()) {
            onBlockReplayed(recordedBlocks[next])
        }
    }

    fun stepBackward() {
        pause()
        val prev = (_currentIndex.value - 1).coerceAtLeast(0)
        _currentIndex.value = prev
        if (recordedBlocks.isNotEmpty()) {
            onBlockReplayed(recordedBlocks[prev])
        }
    }

    fun seekTo(index: Int) {
        val safeIdx = index.coerceIn(0, (recordedBlocks.size - 1).coerceAtLeast(0))
        _currentIndex.value = safeIdx
        if (recordedBlocks.isNotEmpty()) {
            onBlockReplayed(recordedBlocks[safeIdx])
        }
    }
}
