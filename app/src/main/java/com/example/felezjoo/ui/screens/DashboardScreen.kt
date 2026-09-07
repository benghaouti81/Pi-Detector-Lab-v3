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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.felezjoo.models.DspProfile
import com.example.felezjoo.models.TargetClassification
import com.example.felezjoo.simulation.SimulationTargetType
import com.example.felezjoo.ui.components.RollingHistoryGraph
import com.example.felezjoo.ui.components.TargetStatusHeader
import com.example.felezjoo.ui.components.TechnicalStatBadge
import com.example.felezjoo.ui.components.WaveformGraph
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabError
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(viewModel: FelezJooViewModel) {
    val dspResult by viewModel.dspResult.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isSimulation by viewModel.isSimulationMode.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val usbState by viewModel.usbState.collectAsState()
    val historyScores by viewModel.historyScores.collectAsState()
    val historySnr by viewModel.historySnr.collectAsState()
    val historyNoise by viewModel.historyNoise.collectAsState()
    val currentBlock by viewModel.currentBlock.collectAsState()

    val fv = dspResult?.featureVector

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // Target & Classification Banner
        TargetStatusHeader(
            targetScore = fv?.targetScore ?: 0.0,
            confidence = fv?.targetConfidence ?: 0.0,
            ironScore = fv?.ironScore ?: 0.0,
            targetId = fv?.targetId ?: 0,
            classification = dspResult?.targetClassification ?: TargetClassification.NO_TARGET
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Controls & Streaming Bar
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stream Button
                Button(
                    onClick = {
                        if (isStreaming) viewModel.stopStreaming() else viewModel.startStreaming()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming) LabError else LabSecondary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("stream_toggle_button")
                ) {
                    Icon(
                        if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isStreaming) "STOP STREAM" else "START STREAM", fontWeight = FontWeight.Bold)
                }

                // Profile Selector Dropdown
                var profileMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = profileMenuExpanded,
                    onExpandedChange = { profileMenuExpanded = !profileMenuExpanded },
                    modifier = Modifier.width(170.dp)
                ) {
                    TextField(
                        value = activeProfile.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("profile_selector"),
                        colors = ExposedDropdownMenuDefaults.textFieldColors(
                            focusedContainerColor = LabSurfaceVariant,
                            unfocusedContainerColor = LabSurfaceVariant
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
                    )
                    ExposedDropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false }
                    ) {
                        DspProfile.BUILT_IN_PROFILES.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name, fontSize = 12.sp) },
                                onClick = {
                                    viewModel.updateProfile(p)
                                    profileMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Simulation Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SIM", fontSize = 11.sp, color = LabTextSecondary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = isSimulation,
                        onCheckedChange = { viewModel.toggleSimulation(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LabPrimary,
                            checkedTrackColor = LabPrimary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("simulation_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live Waveform Mini-Lab (Height ~240dp)
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

        Spacer(modifier = Modifier.height(10.dp))

        // Rolling History Sweep Signature
        RollingHistoryGraph(
            historyScores = historyScores,
            historySnr = historySnr,
            historyNoise = historyNoise,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Numerical Metrics Grid
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TechnicalStatBadge("SIGNAL AMP", "%.1f".format(fv?.amplitude ?: 0.0), "ADC", LabPrimary)
            TechnicalStatBadge("SNR", "%.1f".format(fv?.snr ?: 0.0), ":1", LabSecondary)
            TechnicalStatBadge("NOISE RMS", "%.2f".format(fv?.noise ?: 0.0), "ADC", LabTertiary)
            TechnicalStatBadge("GROUND DIFF", "%.1f".format(fv?.groundDifference ?: 0.0), "ADC", Color(0xFFFFAB00))
            TechnicalStatBadge("INTEG AREA", "%.0f".format(fv?.area ?: 0.0), "", LabPrimary)
            TechnicalStatBadge("PERSISTENCE", "%.0f".format(fv?.persistence ?: 0.0), "%", LabSecondary)
            TechnicalStatBadge("DELAY", "${currentBlock.delayTicks}", "ticks (%.1fµs)".format(currentBlock.delayUs), LabPrimary)
            TechnicalStatBadge("ETS FRAMING", "14×5", "70 pts", Color(0xFF40C4FF))
        }

        if (isSimulation) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = LabSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("SIMULATION TARGET GENERATOR", fontSize = 11.sp, color = LabTertiary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SimulationTargetType.entries.forEach { targetType ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.simulationEngine.targetType = targetType
                                },
                                modifier = Modifier.height(30.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (viewModel.simulationEngine.targetType == targetType) LabPrimary else LabTextSecondary
                                )
                            ) {
                                Text(targetType.displayName, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
