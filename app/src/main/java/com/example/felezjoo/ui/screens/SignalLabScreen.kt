package com.example.felezjoo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.ui.components.TechnicalStatBadge
import com.example.felezjoo.ui.components.WaveformGraph
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceHighlight
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalLabScreen(viewModel: FelezJooViewModel) {
    val currentBlock by viewModel.currentBlock.collectAsState()
    val dspResult by viewModel.dspResult.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val fv = dspResult?.featureVector

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("SIGNAL PROCESSING & DSP LABORATORY", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Real-time pulse induction decay analysis & feature vector extraction", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        // Waveform
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            WaveformGraph(
                rawSamples = currentBlock.rawSamples,
                filteredCurve = dspResult?.filteredCurve ?: DoubleArray(0),
                baselineCurve = dspResult?.baselineCurve ?: DoubleArray(0),
                groundCurve = dspResult?.groundCurve ?: DoubleArray(0),
                residualCurve = dspResult?.residualCurve ?: DoubleArray(0),
                firstDerivative = dspResult?.firstDerivative ?: DoubleArray(0),
                secondDerivative = dspResult?.secondDerivative ?: DoubleArray(0),
                profile = activeProfile,
                sampleSpacingUs = currentBlock.sampleSpacingUs,
                modifier = Modifier.fillMaxSize(),
                showControls = false
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Baseline / Ground Capture Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.captureAirBaseline() },
                colors = ButtonDefaults.buttonColors(containerColor = LabSurfaceVariant, contentColor = LabPrimary),
                modifier = Modifier.weight(1f).testTag("capture_baseline_button")
            ) {
                Text("CAPTURE BASELINE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.captureGround() },
                colors = ButtonDefaults.buttonColors(containerColor = LabSurfaceVariant, contentColor = LabTertiary),
                modifier = Modifier.weight(1f).testTag("capture_ground_button")
            ) {
                Text("CAPTURE GROUND", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.resetGround() },
                colors = ButtonDefaults.buttonColors(containerColor = LabSurfaceVariant, contentColor = Color.White),
                modifier = Modifier.weight(1f).testTag("reset_ground_button")
            ) {
                Text("RESET GROUND", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // DSP Score Weights Tuner
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("TARGET SCORE WEIGHTS (w1 .. w5)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                WeightSlider(
                    label = "wSignal (Amplitude)",
                    value = activeProfile.weightSignal.toFloat(),
                    onValueChange = { viewModel.updateProfile(activeProfile.copy(weightSignal = it.toDouble())) }
                )
                WeightSlider(
                    label = "wSnr (Signal-to-Noise)",
                    value = activeProfile.weightSnr.toFloat(),
                    onValueChange = { viewModel.updateProfile(activeProfile.copy(weightSnr = it.toDouble())) }
                )
                WeightSlider(
                    label = "wArea (Integration Area)",
                    value = activeProfile.weightArea.toFloat(),
                    onValueChange = { viewModel.updateProfile(activeProfile.copy(weightArea = it.toDouble())) }
                )
                WeightSlider(
                    label = "wShape (Decay Slope / Tau)",
                    value = activeProfile.weightShape.toFloat(),
                    onValueChange = { viewModel.updateProfile(activeProfile.copy(weightShape = it.toDouble())) }
                )
                WeightSlider(
                    label = "wPersistence (Multi-block Temporal)",
                    value = activeProfile.weightPersistence.toFloat(),
                    onValueChange = { viewModel.updateProfile(activeProfile.copy(weightPersistence = it.toDouble())) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Complete Feature Vector Table
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("EXTRACTED FEATURE VECTOR (26 METRICS)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                fv?.let { v ->
                    FeatureTableRow("Target Score", "%.1f / 100".format(v.targetScore), "Target Confidence", "%.1f%%".format(v.targetConfidence))
                    FeatureTableRow("Target ID (1..99)", "${v.targetId}", "Iron Score (0..100)", "%.1f".format(v.ironScore))
                    FeatureTableRow("Signal Amplitude", "%.2f ADC".format(v.amplitude), "SNR", "%.2f : 1".format(v.snr))
                    FeatureTableRow("Noise RMS", "%.2f ADC".format(v.noise), "Noise MAD", "%.2f ADC".format(v.noiseMad))
                    FeatureTableRow("Integration Area", "%.1f".format(v.area), "Normalized Area", "%.2f".format(v.areaNorm))
                    FeatureTableRow("Slope Window", "%.2f".format(v.slope), "Estimated Tau", "%.2f µs".format(v.estimatedTauUs))
                    FeatureTableRow("Early Tau", "%.2f µs".format(v.earlyTauUs), "Late Tau", "%.2f µs".format(v.lateTauUs))
                    FeatureTableRow("Early / Late Tau Ratio", "%.2f".format(v.tauRatio), "Curvature", "%.3f".format(v.curvature))
                    FeatureTableRow("Band A Integral", "%.1f".format(v.integralA), "Band B Integral", "%.1f".format(v.integralB))
                    FeatureTableRow("Band C Integral", "%.1f".format(v.integralC), "A - B Difference", "%.1f".format(v.aMinusB))
                    FeatureTableRow("B - C Difference", "%.1f".format(v.bMinusC), "A / B Ratio", "%.2f".format(v.aDivB))
                    FeatureTableRow("B / C Ratio", "%.2f".format(v.bDivC), "A / C Ratio", "%.2f".format(v.aDivC))
                    FeatureTableRow("Ground Difference", "%.2f ADC".format(v.groundDifference), "Persistence Score", "%.1f".format(v.persistence))
                }
            }
        }
    }
}

@Composable
private fun WeightSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = LabTextSecondary, modifier = Modifier.width(170.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.0f..1.0f,
            colors = SliderDefaults.colors(thumbColor = LabSecondary, activeTrackColor = LabSecondary),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("%.2f".format(value), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun FeatureTableRow(
    label1: String, val1: String,
    label2: String, val2: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Text(label1 + ": ", fontSize = 10.sp, color = LabTextMuted)
            Text(val1, fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(modifier = Modifier.weight(1f)) {
            Text(label2 + ": ", fontSize = 10.sp, color = LabTextMuted)
            Text(val2, fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider(color = LabBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
}
