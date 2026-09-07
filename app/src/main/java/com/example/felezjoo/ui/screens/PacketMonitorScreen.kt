package com.example.felezjoo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun PacketMonitorScreen(viewModel: FelezJooViewModel) {
    val packetMonitor = viewModel.packetMonitor
    val records by packetMonitor.records.collectAsState()
    val selectedRecord by packetMonitor.selectedRecord.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("BINARY PACKET PROTOCOL MONITOR", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
                Text("Inspection of 162-byte RAW_BLOCK packets & CRC-16-CCITT", fontSize = 11.sp, color = LabTextSecondary)
            }

            IconButton(
                onClick = { packetMonitor.clear() },
                modifier = Modifier.size(28.dp).testTag("clear_packet_monitor_btn")
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear Packets", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Two Pane layout: Upper is list of packets, Lower is Detailed Inspector
        Surface(
            color = LabBackground,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (selectedRecord != null) 0.55f else 1.0f)
        ) {
            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No binary packets received yet.", color = LabTextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                    items(records.reversed(), key = { it.id }) { rec ->
                        val isSel = selectedRecord?.id == rec.id
                        Surface(
                            color = if (isSel) LabSurfaceVariant else LabSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) LabPrimary else LabBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { packetMonitor.selectRecord(rec) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (rec.isValid) LabSecondary.copy(alpha = 0.2f) else LabError.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (rec.isValid) LabSecondary else LabError)
                                    ) {
                                        Text(
                                            if (rec.isValid) "CRC OK" else "CRC ERR",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (rec.isValid) LabSecondary else LabError,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "SEQ #${rec.sequenceNumber}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${rec.rawBytes.size} bytes | ${rec.sampleCount} pts",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = LabTextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(rec.formattedTime, fontSize = 9.sp, color = LabTextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Detailed Inspector Panel
        selectedRecord?.let { sel ->
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LabPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PACKET INSPECTOR: SEQ #${sel.sequenceNumber}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LabPrimary
                        )

                        IconButton(
                            onClick = { packetMonitor.selectRecord(null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Header: 0xF5 0x5A", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                        Text("Length: ${sel.rawBytes.size} B", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                        Text("Delay: ${sel.delayTicks} ticks", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                        Text("Samples: ${sel.sampleCount}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                        Text(
                            "CRC Calc: 0x%04X | Pkt: 0x%04X".format(sel.calculatedCrc, sel.packetCrc),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (sel.isValid) LabSecondary else LabError,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("HEX DUMP (FIRST 64 BYTES):", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LabTextMuted)
                    Spacer(modifier = Modifier.height(2.dp))

                    val hexDump = sel.rawBytes.take(64).chunked(16).joinToString("\n") { chunk ->
                        chunk.joinToString(" ") { "%02X".format(it) }
                    }

                    Text(
                        hexDump,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = LabPrimary,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
