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

        val sc = currentBlock.samplingConfiguration
        val maxUs = (sc.sampleCount * sc.sampleSpacingUs).toFloat().coerceAtLeast(50f)
        val (intStartIdx, intEndIdx) = activeProfile.getIntegrationIndices(sc)
        val (aStartIdx, aEndIdx) = activeProfile.getRegionAIndices(sc)
        val (bStartIdx, bEndIdx) = activeProfile.getRegionBIndices(sc)
        val (cStartIdx, cEndIdx) = activeProfile.getRegionCIndices(sc)

        // Boundary Sliders
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PHYSICAL TIME WINDOWS (0 .. %.0f µs)".format(maxUs), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                    Text("dt = %.2f µs (%s)".format(sc.sampleSpacingUs, sc.samplingMode), fontSize = 10.sp, color = LabTextSecondary, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Integration Window Slider
                RegionRangeSlider(
                    label = "Integration Window",
                    color = LabPrimary,
                    range = activeProfile.integrationStartUs.toFloat().coerceIn(0f, maxUs)..activeProfile.integrationEndUs.toFloat().coerceIn(0f, maxUs),
                    maxUs = maxUs,
                    sampleIndices = "S$intStartIdx .. S${(intEndIdx - 1).coerceAtLeast(intStartIdx)}",
                    onRangeChange = { r ->
                        viewModel.updateProfile(activeProfile.copy(integrationStartUs = r.start.toDouble(), integrationEndUs = r.endInclusive.toDouble()))
                    }
                )

                // Band A Slider
                RegionRangeSlider(
                    label = "Region A (Early)",
                    color = Color(0xFF00E5FF),
                    range = activeProfile.aStartUs.toFloat().coerceIn(0f, maxUs)..activeProfile.aEndUs.toFloat().coerceIn(0f, maxUs),
                    maxUs = maxUs,
                    sampleIndices = "S$aStartIdx .. S${(aEndIdx - 1).coerceAtLeast(aStartIdx)}",
                    onRangeChange = { r ->
                        viewModel.updateProfile(activeProfile.copy(regionAStartUs = r.start.toDouble(), regionAEndUs = r.endInclusive.toDouble()))
                    }
                )

                // Band B Slider
                RegionRangeSlider(
                    label = "Region B (Mid)",
                    color = Color(0xFF76FF03),
                    range = activeProfile.bStartUs.toFloat().coerceIn(0f, maxUs)..activeProfile.bEndUs.toFloat().coerceIn(0f, maxUs),
                    maxUs = maxUs,
                    sampleIndices = "S$bStartIdx .. S${(bEndIdx - 1).coerceAtLeast(bStartIdx)}",
                    onRangeChange = { r ->
                        viewModel.updateProfile(activeProfile.copy(regionBStartUs = r.start.toDouble(), regionBEndUs = r.endInclusive.toDouble()))
                    }
                )

                // Band C Slider
                RegionRangeSlider(
                    label = "Region C (Late)",
                    color = Color(0xFFFFD600),
                    range = activeProfile.cStartUs.toFloat().coerceIn(0f, maxUs)..activeProfile.cEndUs.toFloat().coerceIn(0f, maxUs),
                    maxUs = maxUs,
                    sampleIndices = "S$cStartIdx .. S${(cEndIdx - 1).coerceAtLeast(cStartIdx)}",
                    onRangeChange = { r ->
                        viewModel.updateProfile(activeProfile.copy(regionCStartUs = r.start.toDouble(), regionCEndUs = r.endInclusive.toDouble()))
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
    maxUs: Float,
    sampleIndices: String,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(135.dp)) {
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
            Text(sampleIndices, fontSize = 9.sp, color = LabTextSecondary, fontFamily = FontFamily.Monospace)
        }
        RangeSlider(
            value = range,
            onValueChange = onRangeChange,
            valueRange = 0f..maxUs,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "%.1f..%.1fµs".format(range.start, range.endInclusive),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            modifier = Modifier.width(76.dp)
        )
    }
}
