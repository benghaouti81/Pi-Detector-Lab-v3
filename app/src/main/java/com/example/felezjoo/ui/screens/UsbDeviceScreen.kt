package com.example.felezjoo.ui.screens

import android.hardware.usb.UsbDevice
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.felezjoo.ui.components.TechnicalStatBadge
import com.example.felezjoo.usb.UsbConnectionState
import com.example.felezjoo.usb.UsbDriverDetector
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
fun UsbDeviceScreen(viewModel: FelezJooViewModel) {
    val usbState by viewModel.usbState.collectAsState()
    val usbStatusMessage by viewModel.usbStatusMessage.collectAsState()
    val availableDevices by viewModel.availableUsbDevices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val stats = viewModel.usbStats

    var selectedBaud by remember { mutableIntStateOf(115200) }
    val baudRates = listOf(9600, 19200, 38400, 57600, 115200, 230400, 250000, 500000, 1000000)

    val isConnected = usbState == UsbConnectionState.CONNECTED ||
            usbState == UsbConnectionState.HANDSHAKE ||
            usbState == UsbConnectionState.READY ||
            usbState == UsbConnectionState.STREAMING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("USB HARDWARE & DEVICE DIAGNOSTICS", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("USB Host CDC-ACM / UART driver and stream statistics", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        // State Machine Card
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
                    Text("STATE: ${usbState.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = when (usbState) {
                        UsbConnectionState.READY, UsbConnectionState.STREAMING -> LabSecondary
                        UsbConnectionState.ERROR, UsbConnectionState.DEVICE_REMOVED -> LabError
                        UsbConnectionState.DISCONNECTED -> LabTextMuted
                        else -> LabTertiary
                    })

                    Button(
                        onClick = { viewModel.refreshUsbDevices() },
                        colors = ButtonDefaults.buttonColors(containerColor = LabSurfaceVariant, contentColor = Color.White),
                        modifier = Modifier.height(32.dp).testTag("refresh_usb_button")
                    ) {
                        Text("REFRESH", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(usbStatusMessage, fontSize = 11.sp, color = LabTextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Device Selection & Hardware Specs
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("ATTACHED HARDWARE PORTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LabTertiary)
                Spacer(modifier = Modifier.height(8.dp))

                if (availableDevices.isEmpty()) {
                    Text("No USB devices attached via OTG.", fontSize = 12.sp, color = LabTextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Plug in Arduino Leonardo or USB-UART adapter via USB-OTG cable.", fontSize = 11.sp, color = LabTextSecondary)
                } else {
                    availableDevices.forEach { dev ->
                        val driverType = UsbDriverDetector.identifyDriver(dev)
                        val isSel = selectedDevice?.deviceId == dev.deviceId
                        val hasPerm = viewModel.usbManager.hasPermission(dev)

                        Surface(
                            color = if (isSel) LabSurfaceVariant else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) LabPrimary else LabBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(driverType.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        Text("VID: 0x%04X | PID: 0x%04X".format(dev.vendorId, dev.productId), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = LabTextSecondary)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.selectUsbDevice(dev)
                                            viewModel.requestUsbPermission(dev)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (hasPerm) LabSecondary else LabPrimary,
                                            contentColor = Color.Black
                                        ),
                                        modifier = Modifier.height(32.dp).testTag("connect_device_${dev.deviceId}")
                                    ) {
                                        Text(if (hasPerm) "CONNECT" else "REQUEST PERM", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Baud Rate Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("UART Baud Rate:", fontSize = 12.sp, color = LabTextSecondary)

                    var baudMenuExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = baudMenuExpanded,
                        onExpandedChange = { baudMenuExpanded = !baudMenuExpanded },
                        modifier = Modifier.width(160.dp)
                    ) {
                        TextField(
                            value = "$selectedBaud bps",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = baudMenuExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = ExposedDropdownMenuDefaults.textFieldColors(
                                focusedContainerColor = LabSurfaceVariant,
                                unfocusedContainerColor = LabSurfaceVariant
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White)
                        )
                        ExposedDropdownMenu(
                            expanded = baudMenuExpanded,
                            onDismissRequest = { baudMenuExpanded = false }
                        ) {
                            baudRates.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text("$b bps", fontSize = 11.sp) },
                                    onClick = {
                                        selectedBaud = b
                                        baudMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Connection Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            selectedDevice?.let { viewModel.connectUsb(it, selectedBaud) }
                        },
                        enabled = selectedDevice != null && !isConnected,
                        colors = ButtonDefaults.buttonColors(containerColor = LabSecondary, contentColor = Color.Black),
                        modifier = Modifier.weight(1f).testTag("usb_connect_btn")
                    ) {
                        Text("OPEN PORT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.disconnectUsb() },
                        enabled = isConnected,
                        colors = ButtonDefaults.buttonColors(containerColor = LabError, contentColor = Color.White),
                        modifier = Modifier.weight(1f).testTag("usb_disconnect_btn")
                    ) {
                        Text("CLOSE PORT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hardware Command Buttons
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("QUICK HARDWARE COMMANDS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.commandConsole.send("PING") },
                        modifier = Modifier.height(32.dp).testTag("cmd_ping")
                    ) {
                        Text("PING", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.commandConsole.send("CONFIG?") },
                        modifier = Modifier.height(32.dp).testTag("cmd_config")
                    ) {
                        Text("CONFIG?", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.commandConsole.send("STATUS") },
                        modifier = Modifier.height(32.dp).testTag("cmd_status")
                    ) {
                        Text("STATUS", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.commandConsole.send("START") },
                        modifier = Modifier.height(32.dp).testTag("cmd_start")
                    ) {
                        Text("START", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.commandConsole.send("STOP") },
                        modifier = Modifier.height(32.dp).testTag("cmd_stop")
                    ) {
                        Text("STOP", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.commandConsole.send("RESET") },
                        modifier = Modifier.height(32.dp).testTag("cmd_reset")
                    ) {
                        Text("RESET MCU", fontSize = 10.sp, color = LabError)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Counters & Telemetry
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
                    Text("STREAM & TELEMETRY COUNTERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
                    OutlinedButton(
                        onClick = { stats.reset() },
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("RESET COUNTERS", fontSize = 9.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TechnicalStatBadge("RX BYTES", "${stats.rxBytes}", "bytes", LabPrimary)
                    TechnicalStatBadge("TX BYTES", "${stats.txBytes}", "bytes", LabPrimary)
                    TechnicalStatBadge("VALID PACKETS", "${stats.validPackets}", "", LabSecondary)
                    TechnicalStatBadge("INVALID PKTS", "${stats.invalidPackets}", "", if (stats.invalidPackets > 0) LabError else LabTextMuted)
                    TechnicalStatBadge("CRC ERRORS", "${stats.crcErrors}", "", if (stats.crcErrors > 0) LabError else LabTextMuted)
                    TechnicalStatBadge("SEQUENCE GAPS", "${stats.sequenceGaps}", "", if (stats.sequenceGaps > 0) LabError else LabTextMuted)
                    TechnicalStatBadge("RECONNECTS", "${stats.reconnectCount}", "", LabTertiary)
                }
            }
        }
    }
}
