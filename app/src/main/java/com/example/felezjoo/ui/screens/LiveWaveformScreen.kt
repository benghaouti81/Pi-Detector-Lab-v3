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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveWaveformScreen(viewModel: FelezJooViewModel) {
    val currentBlock by viewModel.currentBlock.collectAsState()
    val dspResult by viewModel.dspResult.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val fv = dspResult?.featureVector

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Top status row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OSCILLOSCOPE WAVEFORM (ETS 70-PTS)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
            Text(
                "SEQ: #${currentBlock.sequenceNumber} | DELAY: %.1f µs".format(currentBlock.delayUs),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = LabTextSecondary
            )
        }

        // Full Interactive Waveform Canvas
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                showControls = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Real-time Measurements
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TechnicalStatBadge("AMPLITUDE", "%.1f".format(fv?.amplitude ?: 0.0), "ADC", LabPrimary)
            TechnicalStatBadge("SNR", "%.1f".format(fv?.snr ?: 0.0), ":1", LabSecondary)
            TechnicalStatBadge("RESIDUAL RMS", "%.1f".format(fv?.rms ?: 0.0), "ADC", Color(0xFFFF4081))
            TechnicalStatBadge("A / B RATIO", "%.2f".format(fv?.aDivB ?: 0.0), "", LabTertiary)
            TechnicalStatBadge("B / C RATIO", "%.2f".format(fv?.bDivC ?: 0.0), "", LabTertiary)
            TechnicalStatBadge("CURVATURE", "%.2f".format(fv?.curvature ?: 0.0), "", Color(0xFFEA80FC))
            TechnicalStatBadge("TARGET ID", "${fv?.targetId ?: 0}", "", LabSecondary)
        }
    }
}
