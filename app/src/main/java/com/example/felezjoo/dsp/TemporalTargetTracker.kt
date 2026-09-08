package com.example.felezjoo.dsp

import com.example.felezjoo.models.FeatureVector
import com.example.felezjoo.models.TargetClassification
import com.example.felezjoo.models.TargetEvent
import java.util.UUID

enum class TrackerState {
    NO_TARGET,
    CANDIDATE,
    CONFIRMED,
    LOST
}

data class TrackerOutput(
    val state: TrackerState,
    val activeEvent: TargetEvent?,
    val finalizedEvent: TargetEvent?
)

/**
 * Robust temporal target tracker that models a coil sweep over a target.
 * Prevents event spam by consolidating consecutive frame detections into a single
 * coherent TargetEvent.
 */
class TemporalTargetTracker {

    var state: TrackerState = TrackerState.NO_TARGET
        private set

    private var candidateCount = 0
    private var quietCount = 0
    private var activeEvent: TargetEvent? = null

    val requiredCandidateFrames = 2
    val requiredQuietFramesToLose = 3

    fun reset() {
        state = TrackerState.NO_TARGET
        candidateCount = 0
        quietCount = 0
        activeEvent = null
    }

    /**
     * Process a frame's feature vector and update tracker state.
     */
    fun processFrame(
        featureVector: FeatureVector,
        classification: TargetClassification,
        timestampMs: Long = System.currentTimeMillis(),
        updateState: Boolean = true
    ): TrackerOutput {
        val score = featureVector.targetScore
        val conf = featureVector.targetConfidence

        val isTargetFrame = (score >= 40.0 && conf >= 35.0) &&
                (classification == TargetClassification.FERROUS_LIKELY ||
                 classification == TargetClassification.NON_FERROUS_LIKELY ||
                 classification == TargetClassification.POSSIBLE_TARGET ||
                 classification == TargetClassification.STABLE_TARGET ||
                 classification == TargetClassification.NON_FERROUS ||
                 classification == TargetClassification.IRON)

        if (!updateState) {
            // Read-only snapshot
            return TrackerOutput(state, activeEvent, null)
        }

        var finalized: TargetEvent? = null

        when (state) {
            TrackerState.NO_TARGET -> {
                if (isTargetFrame) {
                    candidateCount = 1
                    quietCount = 0
                    state = TrackerState.CANDIDATE
                    activeEvent = TargetEvent(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = timestampMs,
                        endTimeMs = timestampMs,
                        peakScore = score,
                        peakConfidence = conf,
                        ironScore = featureVector.ironScore,
                        targetId = featureVector.targetId,
                        classification = classification,
                        bestFeatureVector = featureVector,
                        blockCount = 1
                    )
                }
            }

            TrackerState.CANDIDATE -> {
                if (isTargetFrame) {
                    candidateCount++
                    quietCount = 0
                    updateActiveEvent(featureVector, classification, timestampMs)
                    if (candidateCount >= requiredCandidateFrames) {
                        state = TrackerState.CONFIRMED
                    }
                } else {
                    quietCount++
                    if (quietCount >= 2) {
                        // False alarm or noise spike
                        state = TrackerState.NO_TARGET
                        activeEvent = null
                    }
                }
            }

            TrackerState.CONFIRMED -> {
                if (isTargetFrame) {
                    quietCount = 0
                    updateActiveEvent(featureVector, classification, timestampMs)
                } else {
                    quietCount++
                    if (quietCount >= requiredQuietFramesToLose) {
                        // Target has exited coil detection envelope
                        finalized = activeEvent
                        state = TrackerState.LOST
                    }
                }
            }

            TrackerState.LOST -> {
                if (isTargetFrame) {
                    // New target entering immediately
                    candidateCount = 1
                    quietCount = 0
                    state = TrackerState.CANDIDATE
                    activeEvent = TargetEvent(
                        id = UUID.randomUUID().toString(),
                        startTimeMs = timestampMs,
                        endTimeMs = timestampMs,
                        peakScore = score,
                        peakConfidence = conf,
                        ironScore = featureVector.ironScore,
                        targetId = featureVector.targetId,
                        classification = classification,
                        bestFeatureVector = featureVector,
                        blockCount = 1
                    )
                } else {
                    state = TrackerState.NO_TARGET
                    activeEvent = null
                }
            }
        }

        return TrackerOutput(state, activeEvent, finalized)
    }

    private fun updateActiveEvent(
        fv: FeatureVector,
        classification: TargetClassification,
        timestampMs: Long
    ) {
        val current = activeEvent ?: return
        val currentPeak = current.peakScore

        val isHigher = fv.targetScore > currentPeak
        val newBest = if (isHigher) fv else current.bestFeatureVector
        val newClassification = if (isHigher) classification else current.classification

        activeEvent = current.copy(
            endTimeMs = timestampMs,
            peakScore = maxOf(current.peakScore, fv.targetScore),
            peakConfidence = maxOf(current.peakConfidence, fv.targetConfidence),
            ironScore = if (isHigher) fv.ironScore else current.ironScore,
            targetId = if (isHigher && fv.targetId > 0) fv.targetId else current.targetId,
            classification = newClassification,
            bestFeatureVector = newBest,
            blockCount = current.blockCount + 1
        )
    }
}
