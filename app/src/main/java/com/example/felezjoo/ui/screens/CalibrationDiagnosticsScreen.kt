package com.example.felezjoo.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.diagnostics.SystemDiagnostics
import com.example.felezjoo.diagnostics.UsbTroubleshooter
import com.example.felezjoo.ui.components.TechnicalStatBadge
import com.example.felezjoo.usb.UsbConnectionState
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabError
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalibrationDiagnosticsScreen(viewModel: FelezJooViewModel) {
    val currentBlock by viewModel.currentBlock.collectAsState()
    val usbState by viewModel.usbState.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val stats = viewModel.usbStats
    val logs by SystemDiagnostics.logs.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Compute Health checks
    val isConnected = usbState == UsbConnectionState.CONNECTED ||
            usbState == UsbConnectionState.HANDSHAKE ||
            usbState == UsbConnectionState.READY ||
            usbState == UsbConnectionState.STREAMING

    val maxAdc = currentBlock.rawSamples.maxOrNull() ?: 0
    val isAdcSaturation = maxAdc >= 1023
    val isAdcZero = maxAdc <= 5

    val steps = UsbTroubleshooter.generateSteps(
        deviceFound = selectedDevice != null || viewModel.availableUsbDevices.value.isNotEmpty(),
        hasPermission = selectedDevice?.let { viewModel.usbManager.hasPermission(it) } ?: false,
        isConnected = isConnected,
        rxCount = stats.rxBytes,
        validPacketCount = stats.validPackets,
        crcErrors = stats.crcErrors
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("SYSTEM CALIBRATION & DIAGNOSTICS HEALTH", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Hardware signal bounds, throughput telemetry, and guided troubleshooter", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        // Hardware Health Metrics
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("HARDWARE HEALTH & BANDWIDTH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TechnicalStatBadge("DSP TIME", "${SystemDiagnostics.lastDspDurationMs}", "ms", LabSecondary)
                    TechnicalStatBadge("PEAK ADC", "$maxAdc", if (isAdcSaturation) "(SATURATED)" else "/ 1023", if (isAdcSaturation) LabError else LabPrimary)
                    TechnicalStatBadge("ADC INTEGRITY", if (isAdcSaturation) "CLIPPING" else if (isAdcZero) "ZERO LEVEL" else "NORMAL", "", if (isAdcSaturation || isAdcZero) LabError else LabSecondary)
                    TechnicalStatBadge("CRC CORRUPTIONS", "${stats.crcErrors}", "", if (stats.crcErrors > 0) LabError else LabTextMuted)
                    TechnicalStatBadge("SEQUENCE GAPS", "${stats.sequenceGaps}", "", if (stats.sequenceGaps > 0) LabError else LabTextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // USB Troubleshooting Checklist
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("GUIDED HARDWARE TROUBLESHOOTER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabTertiary)
                Spacer(modifier = Modifier.height(8.dp))

                steps.forEach { step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            if (step.isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (step.isOk) LabSecondary else LabError,
                            modifier = Modifier.size(20.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(step.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            Text(step.description, fontSize = 10.sp, color = LabTextSecondary)
                            if (!step.isOk) {
                                Text(step.actionGuide, fontSize = 10.sp, color = LabTertiary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Diagnostics Log Viewer
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
                    Text("INTERNAL DIAGNOSTICS LOG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                    IconButton(
                        onClick = {
                            val text = SystemDiagnostics.exportLogsText()
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Diagnostics exported", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp).testTag("copy_diagnostics_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = LabBackground,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(160.dp).padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp)
                    ) {
                        logs.takeLast(50).forEach { log ->
                            val color = when (log.level.name) {
                                "ERROR" -> LabError
                                "WARN" -> LabTertiary
                                "INFO" -> LabSecondary
                                else -> LabTextMuted
                            }
                            Text(
                                "[${log.formattedTime}] [${log.level}] [${log.component}] ${log.message}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }
}
