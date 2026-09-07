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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabError
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlgorithmCompareScreen(viewModel: FelezJooViewModel) {
    val currentBlock by viewModel.currentBlock.collectAsState()

    // Run block simultaneously through all built-in profiles
    val profiles = remember { DspProfile.BUILT_IN_PROFILES }
    val results = remember(currentBlock) {
        profiles.map { p ->
            p to viewModel.dspPipeline.processBlock(currentBlock, p)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("MULTI-ALGORITHM BENCHMARK & COMPARISON", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Simultaneous evaluation of identical pulse decay through competing DSP pipelines", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        results.forEach { (profile, res) ->
            val fv = res.featureVector
            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(profile.name.uppercase(), fontWeight = FontWeight.Black, fontSize = 13.sp, color = LabPrimary)
                        Text(
                            res.targetClassification.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = when (res.targetClassification.name) {
                                "STABLE_TARGET" -> LabSecondary
                                "IRON" -> LabError
                                "NO_TARGET" -> Color.Gray
                                else -> LabTertiary
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricBlock("Score", "%.1f".format(fv.targetScore), LabPrimary)
                        MetricBlock("Conf", "%.0f%%".format(fv.targetConfidence), LabSecondary)
                        MetricBlock("Iron", "%.0f".format(fv.ironScore), if (fv.ironScore > 50) LabError else Color.White)
                        MetricBlock("Target ID", "${fv.targetId}", LabTertiary)
                        MetricBlock("SNR", "%.1f".format(fv.snr), LabSecondary)
                        MetricBlock("Noise", "%.2f".format(fv.noise), Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = LabTextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
    }
}
