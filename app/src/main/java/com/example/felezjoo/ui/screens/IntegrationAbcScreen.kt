package com.example.felezjoo.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntegrationAbcScreen(viewModel: FelezJooViewModel) {
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
        Text("INTEGRATION WINDOW & A/B/C REGIONS", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Interactive sample window boundaries and multi-band decay integrals", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        // Waveform preview with bands enabled
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
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

        // Boundary Sliders
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("SAMPLE WINDOW BOUNDARIES (0 .. 69)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                // Integration Window Slider
                RegionRangeSlider(
                    label = "Integration Window",
                    color = LabPrimary,
                    range = activeProfile.integrationStartSample.toFloat()..activeProfile.integrationEndSample.toFloat(),
                    onRangeChange = { r ->
                        viewModel.updateProfile(activeProfile.copy(integrationStartSample = r.start.toInt(), integrationEndSample = r.endInclusive.toInt()))
                    }
                )

                // Band A Slider
                RegionRangeSlider(
                    label = "Region A (Early)",
                    color = Color(0xFF00E5FF),
                    range = activeProfile.aStartSample.toFloat()..activeProfile.aEndSample.toFloat(),
                    onRangeChange = { r ->
                        viewModel.updateProfile(activeProfile.copy(aStartSample = r.start.toInt(), aEndSample = r.endInclusive.toInt()))
                    }
                )

                // Band B Slider
                RegionRangeSlider(
                    label = "Region B (Mid)",
                    color = Color(0xFF76FF03),
                    range = activeProfile.bStartSample.toFloat()..activeProfile.bEndSample.toFloat(),
                    onRangeChange = { r ->
                        viewModel.updateProfile(activeProfile.copy(bStartSample = r.start.toInt(), bEndSample = r.endInclusive.toInt()))
                    }
                )

                // Band C Slider
                RegionRangeSlider(
                    label = "Region C (Late)",
                    color = Color(0xFFFFD600),
                    range = activeProfile.cStartSample.toFloat()..activeProfile.cEndSample.toFloat(),
                    onRangeChange = { r ->
                        viewModel.updateProfile(activeProfile.copy(cStartSample = r.start.toInt(), cEndSample = r.endInclusive.toInt()))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Multi-Band Integral Statistics
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("INTEGRALS & DISCRIMINATION RATIOS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TechnicalStatBadge("INTEGRAL A", "%.1f".format(fv?.integralA ?: 0.0), "", Color(0xFF00E5FF))
                    TechnicalStatBadge("INTEGRAL B", "%.1f".format(fv?.integralB ?: 0.0), "", Color(0xFF76FF03))
                    TechnicalStatBadge("INTEGRAL C", "%.1f".format(fv?.integralC ?: 0.0), "", Color(0xFFFFD600))
                    TechnicalStatBadge("A - B", "%.1f".format(fv?.aMinusB ?: 0.0), "", LabSecondary)
                    TechnicalStatBadge("B - C", "%.1f".format(fv?.bMinusC ?: 0.0), "", LabSecondary)
                    TechnicalStatBadge("A / B RATIO", "%.2f".format(fv?.aDivB ?: 0.0), "", LabTertiary)
                    TechnicalStatBadge("B / C RATIO", "%.2f".format(fv?.bDivC ?: 0.0), "", LabTertiary)
                    TechnicalStatBadge("A / C RATIO", "%.2f".format(fv?.aDivC ?: 0.0), "", LabTertiary)
                }
            }
        }
    }
}

@Composable
private fun RegionRangeSlider(
    label: String,
    color: Color,
    range: ClosedFloatingPointRange<Float>,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp))
        RangeSlider(
            value = range,
            onValueChange = onRangeChange,
            valueRange = 0f..69f,
            steps = 68,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "[${range.start.toInt()} .. ${range.endInclusive.toInt()}]",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            modifier = Modifier.width(60.dp)
        )
    }
}
