package com.example.felezjoo.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.serial.CommandStatus
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
fun CommandConsoleScreen(viewModel: FelezJooViewModel) {
    val console = viewModel.commandConsole
    val history by console.commandHistory.collectAsState()
    var inputCommand by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Text("FIRMWARE COMMAND CONSOLE", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Hardware protocol CLI with state tracking & latency timing", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Command Action Chips
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("COMMON COMMAND PRESETS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LabTertiary)
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    console.quickCommands.forEach { cmd ->
                        OutlinedButton(
                            onClick = { console.send(cmd) },
                            modifier = Modifier.height(30.dp).testTag("quick_cmd_$cmd")
                        ) {
                            Text(cmd, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TRANSMISSION LOG (${history.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
            IconButton(
                onClick = { console.clearHistory() },
                modifier = Modifier.size(28.dp).testTag("clear_command_history_btn")
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear History", tint = Color.White)
            }
        }

        // Command Cards
        Surface(
            color = LabBackground,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No commands transmitted yet.", color = LabTextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                    items(history.reversed(), key = { it.id }) { item ->
                        Surface(
                            color = LabSurfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "> ${item.command}",
                                        color = LabPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    val statusColor = when (item.status) {
                                        CommandStatus.ACKNOWLEDGED -> LabSecondary
                                        CommandStatus.PENDING -> LabTertiary
                                        CommandStatus.FAILED, CommandStatus.TIMEOUT, CommandStatus.UNSUPPORTED -> LabError
                                    }

                                    Surface(
                                        color = statusColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor)
                                    ) {
                                        Text(
                                            item.status.name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (item.response.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "< ${item.response}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFD6E4F0)
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.formattedTime, fontSize = 9.sp, color = LabTextMuted)
                                    if (item.responseTimeMs > 0) {
                                        Text("${item.responseTimeMs} ms", fontSize = 9.sp, color = LabTextSecondary, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Command Entry Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputCommand,
                onValueChange = { inputCommand = it },
                placeholder = { Text("Command (e.g. SET:DELAY=12)...", fontSize = 11.sp, color = LabTextMuted) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("command_cli_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LabSurface,
                    unfocusedContainerColor = LabSurface,
                    focusedBorderColor = LabPrimary,
                    unfocusedBorderColor = LabBorder
                ),
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (inputCommand.isNotBlank()) {
                        console.send(inputCommand)
                        inputCommand = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(LabPrimary, RoundedCornerShape(8.dp))
                    .testTag("command_cli_send_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Command", tint = Color.Black)
            }
        }
    }
}
