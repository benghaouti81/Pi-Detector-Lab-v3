package com.example.felezjoo.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Session ${System.currentTimeMillis() % 10000}",
    val startTimeMs: Long = System.currentTimeMillis(),
    val endTimeMs: Long = System.currentTimeMillis(),
    val deviceName: String = "FelezJoo PI Research",
    val totalBlocks: Int = 0,
    val totalEvents: Int = 0,
    val notes: String = ""
) : Serializable

@Entity(tableName = "decay_blocks")
data class DecayBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val sequenceNumber: Long,
    val timestamp: Long,
    val delayTicks: Int,
    val delayUs: Double,
    val pulseRate: Int,
    val pulseWidthUs: Int,
    val rawSamplesCsv: String,
    val targetScore: Double,
    val confidence: Double,
    val ironScore: Double,
    val targetId: Int,
    val classification: String
) : Serializable

@Entity(tableName = "target_events")
data class TargetEventEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val sessionId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val peakScore: Double,
    val peakConfidence: Double,
    val ironScore: Double,
    val targetId: Int,
    val classification: String,
    val notes: String = ""
) : Serializable

@Entity(tableName = "experiments")
data class ExperimentEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val targetName: String,
    val material: String,
    val size: String,
    val depthCm: Double,
    val coilHeightCm: Double,
    val groundType: String,
    val notes: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
