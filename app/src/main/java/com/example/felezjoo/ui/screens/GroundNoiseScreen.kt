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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.models.GroundSpeed
import com.example.felezjoo.ui.components.TechnicalStatBadge
import com.example.felezjoo.ui.components.WaveformGraph
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroundNoiseScreen(viewModel: FelezJooViewModel) {
    val currentBlock by viewModel.currentBlock.collectAsState()
    val dspResult by viewModel.dspResult.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val progress by viewModel.groundCaptureProgress.collectAsState()
    val fv = dspResult?.featureVector

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("GROUND BALANCE & NOISE FLOOR LAB", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Mineral baseline curve tracking & statistical noise decomposition", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        // Waveform
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

        // Ground Balance Controls
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("GROUND BASELINE CALIBRATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabTertiary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.captureGround() },
                        colors = ButtonDefaults.buttonColors(containerColor = LabTertiary, contentColor = Color.Black),
                        modifier = Modifier.weight(1f).testTag("btn_grab_ground")
                    ) {
                        Text("PUMP / GRAB GROUND", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.resetGround() },
                        colors = ButtonDefaults.buttonColors(containerColor = LabSurfaceVariant, contentColor = Color.White),
                        modifier = Modifier.weight(1f).testTag("btn_reset_ground")
                    ) {
                        Text("RESET TO ZERO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (progress > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Calibrating Ground ($progress%)...", fontSize = 11.sp, color = LabTertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = LabTertiary,
                        trackColor = LabSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ground Tracking Speed Chips
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto Tracking Speed: ", fontSize = 11.sp, color = LabTextSecondary)
                    GroundSpeed.entries.forEach { spd ->
                        FilterChip(
                            selected = activeProfile.groundSpeed == spd,
                            onClick = {
                                viewModel.updateProfile(activeProfile.copy(groundSpeed = spd))
                            },
                            label = { Text(spd.name, fontSize = 9.sp) },
                            modifier = Modifier.padding(horizontal = 2.dp).height(26.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Statistical Noise Metrics
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ELECTRONIC & MINERAL NOISE STATISTICS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TechnicalStatBadge("NOISE RMS", "%.2f".format(fv?.noise ?: 0.0), "ADC", LabTertiary)
                    TechnicalStatBadge("NOISE MAD", "%.2f".format(fv?.noiseMad ?: 0.0), "ADC", LabSecondary)
                    TechnicalStatBadge("PEAK NOISE", "%.2f".format((fv?.noise ?: 0.0) * 3.0), "ADC", Color(0xFFFF5252))
                    TechnicalStatBadge("SNR RATIO", "%.1f".format(fv?.snr ?: 0.0), ":1", LabPrimary)
                    TechnicalStatBadge("GROUND OFFSET", "%.1f".format(fv?.groundDifference ?: 0.0), "ADC", Color(0xFFFFAB00))
                }
            }
        }
    }
}
