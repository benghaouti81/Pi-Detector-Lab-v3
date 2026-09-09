package com.example.felezjoo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.audio.AudioMode
import com.example.felezjoo.models.PolarityDetectionQuality
import com.example.felezjoo.models.PolarityMode
import com.example.felezjoo.models.WaveformPolarity
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabError
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextSecondary

@Composable
fun DetectorControlsScreen(viewModel: FelezJooViewModel) {
    val currentBlock by viewModel.currentBlock.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val samplingConfig by viewModel.samplingConfig.collectAsState()
    val polarityDetectionResult by viewModel.polarityDetectionResult.collectAsState()
    val dspResult by viewModel.dspResult.collectAsState()
    val audioManager = viewModel.audioManager

    var pulseRateHz by remember { mutableIntStateOf(currentBlock.pulseRate) }
    var pulseWidthUs by remember { mutableIntStateOf(currentBlock.pulseWidthUs) }
    var delayTicks by remember { mutableIntStateOf(currentBlock.delayTicks) }

    var targetThreshold by remember { mutableFloatStateOf(activeProfile.targetThreshold.toFloat()) }
    var confidenceThreshold by remember { mutableFloatStateOf(activeProfile.confidenceThreshold.toFloat()) }
    var ironRejectThreshold by remember { mutableFloatStateOf(activeProfile.ironRejectThreshold.toFloat()) }
    var audioThreshold by remember { mutableFloatStateOf(audioManager.audioThreshold.toFloat()) }

    var hapticState by remember { mutableStateOf(audioManager.hapticEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("DETECTOR TRANSMITTER & SOUND CONTROLS", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Hardware timing generator and acoustic feedback synthesizer", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        // Hardware Pulse Generation
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("COIL PULSE TIMING & POWER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                // Pulse Frequency
                ControlSlider(
                    label = "Pulse Rate",
                    value = pulseRateHz.toFloat(),
                    range = 50f..500f,
                    unit = "Hz",
                    onValueChange = { pulseRateHz = it.toInt() }
                )

                // Pulse Width
                ControlSlider(
                    label = "Pulse Width",
                    value = pulseWidthUs.toFloat(),
                    range = 40f..300f,
                    unit = "µs",
                    onValueChange = { pulseWidthUs = it.toInt() }
                )

                // Delay Ticks
                ControlSlider(
                    label = "Delay Ticks",
                    value = delayTicks.toFloat(),
                    range = 1f..40f,
                    unit = "ticks",
                    onValueChange = { delayTicks = it.toInt() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.setPulseRateHz(pulseRateHz)
                        viewModel.setPulseWidthUs(pulseWidthUs)
                        viewModel.setDelayTicks(delayTicks)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LabPrimary, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().testTag("apply_detector_hardware_btn")
                ) {
                    Text("TRANSMIT HARDWARE CONFIGURATION", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Waveform Polarity (AFE Inverting/Non-inverting and Auto Detection)
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth().testTag("waveform_polarity_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("WAVEFORM POLARITY & AFE COUPLING", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                    val activeSign = dspResult?.effectivePolarity?.sign ?: samplingConfig.polarity.sign
                    Text(
                        text = if (activeSign > 0) "ACTIVE: NORMAL (+)" else "ACTIVE: INVERTED (-)",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (activeSign > 0) LabSecondary else LabTertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Controls AFE decay normalization so eddy current decays are positive for regression.",
                    fontSize = 11.sp,
                    color = LabTextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Polarity Mode Selection:", fontSize = 11.sp, color = LabTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PolarityMode.entries.forEach { mode ->
                        FilterChip(
                            selected = samplingConfig.polarityMode == mode,
                            onClick = { viewModel.setPolarityMode(mode) },
                            label = { Text(mode.displayName, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).height(34.dp).testTag("polarity_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-detection status display
                Surface(
                    color = LabSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val detectedPol = polarityDetectionResult.detectedPolarity
                            val detectedStr = when (detectedPol) {
                                WaveformPolarity.POSITIVE -> "NORMAL (+)"
                                WaveformPolarity.NEGATIVE -> "INVERTED (-)"
                                null -> "UNKNOWN / NOISE"
                            }
                            Text(
                                text = "Detected: $detectedStr",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (detectedPol) {
                                    WaveformPolarity.POSITIVE -> LabSecondary
                                    WaveformPolarity.NEGATIVE -> LabTertiary
                                    null -> LabTextSecondary
                                }
                            )

                            val qualityColor = when (polarityDetectionResult.quality) {
                                PolarityDetectionQuality.HIGH -> LabSecondary
                                PolarityDetectionQuality.MEDIUM -> LabPrimary
                                PolarityDetectionQuality.LOW -> LabTertiary
                                PolarityDetectionQuality.NONE -> LabTextSecondary
                            }
                            Text(
                                text = "Quality: ${polarityDetectionResult.quality.displayName}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = qualityColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = polarityDetectionResult.reason,
                            fontSize = 10.sp,
                            color = LabTextSecondary
                        )

                        if (polarityDetectionResult.detectedPolarity != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "+Fit: R²=%.2f (pts=%d) | -Fit: R²=%.2f (pts=%d)".format(
                                    polarityDetectionResult.positiveR2,
                                    polarityDetectionResult.positiveValidSamples,
                                    polarityDetectionResult.negativeR2,
                                    polarityDetectionResult.negativeValidSamples
                                ),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = LabTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.runPolarityDetection() },
                        modifier = Modifier.weight(1f).testTag("detect_polarity_btn")
                    ) {
                        Text("DETECT POLARITY", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { viewModel.applyDetectedPolarity() },
                        enabled = polarityDetectionResult.detectedPolarity != null,
                        colors = ButtonDefaults.buttonColors(containerColor = LabSecondary, contentColor = Color.Black),
                        modifier = Modifier.weight(1f).testTag("apply_polarity_btn")
                    ) {
                        Text("ADOPT DETECTED", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Detection & Discrimination Thresholds
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("DETECTION & DISCRIMINATION THRESHOLDS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabTertiary)
                Spacer(modifier = Modifier.height(8.dp))

                ControlSlider(
                    label = "Target Threshold",
                    value = targetThreshold,
                    range = 10f..80f,
                    unit = "/ 100",
                    onValueChange = {
                        targetThreshold = it
                        viewModel.updateProfile(activeProfile.copy(targetThreshold = it.toDouble()))
                    }
                )

                ControlSlider(
                    label = "Confidence Min",
                    value = confidenceThreshold,
                    range = 10f..80f,
                    unit = "%",
                    onValueChange = {
                        confidenceThreshold = it
                        viewModel.updateProfile(activeProfile.copy(confidenceThreshold = it.toDouble()))
                    }
                )

                ControlSlider(
                    label = "Iron Reject",
                    value = ironRejectThreshold,
                    range = 20f..90f,
                    unit = "/ 100",
                    onValueChange = {
                        ironRejectThreshold = it
                        viewModel.updateProfile(activeProfile.copy(ironRejectThreshold = it.toDouble()))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Audio Synthesizer Mode
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ACOUSTIC FEEDBACK & HAPTICS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                ControlSlider(
                    label = "Audio Threshold",
                    value = audioThreshold,
                    range = 5f..80f,
                    unit = "/ 100",
                    onValueChange = {
                        audioThreshold = it
                        audioManager.audioThreshold = it.toDouble()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Sound Mode:", fontSize = 11.sp, color = LabTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AudioMode.entries.forEach { m ->
                        FilterChip(
                            selected = audioManager.mode == m,
                            onClick = { audioManager.mode = m },
                            label = { Text(m.displayName, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().height(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Vibration Click:", fontSize = 12.sp, color = Color.White)
                    Switch(
                        checked = hapticState,
                        onCheckedChange = {
                            hapticState = it
                            audioManager.hapticEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = LabSecondary)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = LabTextSecondary, modifier = Modifier.width(120.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = LabPrimary, activeTrackColor = LabPrimary),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "%.0f %s".format(value, unit),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            modifier = Modifier.width(65.dp)
        )
    }
}
