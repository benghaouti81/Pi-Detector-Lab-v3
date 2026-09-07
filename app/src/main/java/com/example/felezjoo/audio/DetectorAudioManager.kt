package com.example.felezjoo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.felezjoo.models.TargetClassification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

enum class AudioMode(val displayName: String) {
    SILENT("Silent / Mute"),
    VCO("Variable Pitch (VCO)"),
    BEEP_THRESHOLD("Beep on Threshold"),
    TARGET_ID_TONE("Target ID Tone"),
    FERROUS_NON_FERROUS("Iron / Non-Ferrous Tones")
}

class DetectorAudioManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var mode: AudioMode = AudioMode.VCO
    var isMuted: Boolean = false
    var audioThreshold: Double = 25.0
    var hapticEnabled: Boolean = true

    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null

    @Volatile
    private var targetFrequency: Double = 0.0

    @Volatile
    private var targetVolume: Float = 0.0f

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        startSynthesizer()
    }

    private fun startSynthesizer() {
        synthJob?.cancel()
        synthJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1024)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val pcmBuffer = ShortArray(512)
            var phase = 0.0

            while (isActive) {
                val freq = targetFrequency
                val vol = if (isMuted || mode == AudioMode.SILENT) 0.0f else targetVolume

                if (freq > 20.0 && vol > 0.01f) {
                    val phaseIncrement = (2.0 * Math.PI * freq) / sampleRate
                    for (i in pcmBuffer.indices) {
                        phase += phaseIncrement
                        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                        val sample = (sin(phase) * 32767.0 * vol).toInt().coerceIn(-32768, 32767).toShort()
                        pcmBuffer[i] = sample
                    }
                } else {
                    pcmBuffer.fill(0)
                }

                track.write(pcmBuffer, 0, pcmBuffer.size)
            }

            try {
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun updateTargetState(
        targetScore: Double,
        confidence: Double,
        ironScore: Double,
        targetId: Int,
        classification: TargetClassification
    ) {
        if (targetScore < audioThreshold || confidence < 20.0) {
            targetFrequency = 0.0
            targetVolume = 0.0f
            return
        }

        when (mode) {
            AudioMode.SILENT -> {
                targetFrequency = 0.0
                targetVolume = 0.0f
            }
            AudioMode.VCO -> {
                // Frequency scales from 250 Hz to 1800 Hz smoothly with target score
                val norm = (targetScore - audioThreshold) / (100.0 - audioThreshold)
                targetFrequency = (250.0 + (norm.coerceIn(0.0, 1.0) * 1550.0))
                targetVolume = (0.2f + 0.8f * (confidence / 100.0).toFloat()).coerceIn(0.0f, 1.0f)
            }
            AudioMode.BEEP_THRESHOLD -> {
                targetFrequency = 650.0
                targetVolume = 0.7f
            }
            AudioMode.TARGET_ID_TONE -> {
                // Tone pitch based on target ID (1..99) -> 300 Hz .. 1400 Hz
                targetFrequency = 300.0 + (targetId.coerceIn(1, 99) * 11.0)
                targetVolume = 0.7f
            }
            AudioMode.FERROUS_NON_FERROUS -> {
                // Iron: Low buzzing tone (180 Hz). Non-ferrous: High clear tone (950 Hz)
                targetFrequency = if (ironScore > 50.0) 180.0 else 950.0
                targetVolume = 0.75f
            }
        }

        if (hapticEnabled && confidence > 65.0 && targetScore > 55.0) {
            triggerHapticClick()
        }
    }

    fun triggerHapticClick() {
        if (!hapticEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (e: Exception) {
            // Ignore haptic failures
        }
    }

    fun triggerDisconnectHaptic() {
        if (!hapticEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(150)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun release() {
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
