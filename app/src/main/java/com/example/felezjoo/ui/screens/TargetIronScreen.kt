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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.models.TargetClassification
import com.example.felezjoo.ui.components.TargetStatusHeader
import com.example.felezjoo.ui.components.TechnicalStatBadge
import com.example.felezjoo.ui.components.WaveformGraph
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabError
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetIronScreen(viewModel: FelezJooViewModel) {
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
        Text("TARGET DISCRIMINATION & IRON IDENTIFIER", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Multi-criteria ferrous classification & decay ratio analysis", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        // Target Header
        TargetStatusHeader(
            targetScore = fv?.targetScore ?: 0.0,
            confidence = fv?.targetConfidence ?: 0.0,
            ironScore = fv?.ironScore ?: 0.0,
            targetId = fv?.targetId ?: 0,
            classification = dspResult?.targetClassification ?: TargetClassification.NO_TARGET
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Mini Waveform
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
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

        // Iron Criteria Diagnostic Panel
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("FERROUS PHYSICAL SIGNATURE INDICATORS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabTertiary)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Iron targets characteristically exhibit rapid initial magnetic dissipation (steep early slope), leading to high A/B ratios, low B/C ratios, and steep early curvature.",
                    fontSize = 11.sp,
                    color = LabTextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TechnicalStatBadge("A / B RATIO", "%.2f".format(fv?.aDivB ?: 0.0), if ((fv?.aDivB ?: 0.0) > 2.8) "(Iron signature)" else "", if ((fv?.aDivB ?: 0.0) > 2.8) LabError else LabSecondary)
                    TechnicalStatBadge("B / C RATIO", "%.2f".format(fv?.bDivC ?: 0.0), if ((fv?.bDivC ?: 0.0) < 1.2) "(Rapid drop)" else "", LabPrimary)
                    TechnicalStatBadge("CURVATURE", "%.3f".format(fv?.curvature ?: 0.0), "", Color(0xFFEA80FC))
                    TechnicalStatBadge(
                        "REGRESSION TAU",
                        if (fv?.isTauValid == true) "%.1f".format(fv.estimatedTauUs) else "--",
                        if (fv?.isTauValid == true) "µs (R²=%.2f)".format(fv.tauFitR2) else "(No fit)",
                        if (fv?.isTauValid == true) LabSecondary else Color.Gray
                    )
                    TechnicalStatBadge("ENERGY A", "%.1f".format(fv?.energyA ?: 0.0), "V²·µs", LabPrimary)
                    TechnicalStatBadge("ENERGY C", "%.1f".format(fv?.energyC ?: 0.0), "V²·µs", LabSecondary)
                    TechnicalStatBadge("GROUND ADAPT", if (fv?.isGroundFrozen == true) "FROZEN" else "TRACKING", fv?.groundFreezeReason ?: "", if (fv?.isGroundFrozen == true) LabError else LabSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Target ID Physical Measurements Panel
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("DETERMINISTIC TARGET CLASSIFICATION & ID", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Zero synthetic/random seeds. Target ID is derived purely from measured decay physics (tau & energy ratios).", fontSize = 11.sp, color = LabTextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                val idDisplay = if ((fv?.targetScore ?: 0.0) >= 30.0 && (fv?.targetId ?: 0) > 0) "%02d".format(fv?.targetId) else "--"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PHYSICAL TAU FIT:", fontSize = 11.sp, color = LabTextSecondary)
                        Text(
                            if (fv?.isTauValid == true) "%.1f µs (R²=%.2f)".format(fv.estimatedTauUs, fv.tauFitR2) else "--",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                    Column {
                        Text("DECAY RATIO (A/B):", fontSize = 11.sp, color = LabTextSecondary)
                        Text(
                            "%.2f".format(fv?.aDivB ?: 0.0),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ACTIVE TARGET ID:", fontSize = 11.sp, color = LabTertiary)
                        Text(
                            idDisplay,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = LabTertiary
                        )
                    }
                }
            }
        }
    }
}
