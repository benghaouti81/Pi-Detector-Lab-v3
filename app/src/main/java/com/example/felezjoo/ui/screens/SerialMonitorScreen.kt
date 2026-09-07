package com.example.felezjoo.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.serial.SerialDirection
import com.example.felezjoo.serial.SerialDisplayMode
import com.example.felezjoo.serial.SerialFilter
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextSecondary

@Composable
fun SerialMonitorScreen(viewModel: FelezJooViewModel) {
    val monitor = viewModel.serialMonitor
    val entries by monitor.entries.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var filter by remember { mutableStateOf(SerialFilter.ALL) }
    var displayMode by remember { mutableStateOf(SerialDisplayMode.ASCII) }
    var autoScroll by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    var commandInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    val filteredEntries = remember(entries, filter) {
        when (filter) {
            SerialFilter.ALL -> entries
            SerialFilter.RX_ONLY -> entries.filter { it.direction == SerialDirection.RX }
            SerialFilter.TX_ONLY -> entries.filter { it.direction == SerialDirection.TX }
        }
    }

    LaunchedEffect(filteredEntries.size, autoScroll) {
        if (autoScroll && filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(filteredEntries.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Control Bar
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = filter == SerialFilter.ALL,
                            onClick = { filter = SerialFilter.ALL },
                            label = { Text("ALL", fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        FilterChip(
                            selected = filter == SerialFilter.RX_ONLY,
                            onClick = { filter = SerialFilter.RX_ONLY },
                            label = { Text("RX ONLY", fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        FilterChip(
                            selected = filter == SerialFilter.TX_ONLY,
                            onClick = { filter = SerialFilter.TX_ONLY },
                            label = { Text("TX ONLY", fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                isPaused = !isPaused
                                monitor.isPaused = isPaused
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Pause/Resume",
                                tint = if (isPaused) LabSecondary else Color.White
                            )
                        }
                        IconButton(
                            onClick = { monitor.clear() },
                            modifier = Modifier.size(28.dp).testTag("clear_serial_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                val text = monitor.exportLog()
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp).testTag("copy_serial_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = displayMode == SerialDisplayMode.ASCII,
                            onClick = { displayMode = SerialDisplayMode.ASCII },
                            label = { Text("ASCII", fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp)
                        )
                        FilterChip(
                            selected = displayMode == SerialDisplayMode.HEX,
                            onClick = { displayMode = SerialDisplayMode.HEX },
                            label = { Text("HEX", fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp)
                        )
                        FilterChip(
                            selected = displayMode == SerialDisplayMode.RAW,
                            onClick = { displayMode = SerialDisplayMode.RAW },
                            label = { Text("RAW", fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-scroll", fontSize = 10.sp, color = LabTextSecondary)
                        Checkbox(
                            checked = autoScroll,
                            onCheckedChange = { autoScroll = it },
                            colors = CheckboxDefaults.colors(checkedColor = LabPrimary),
                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Terminal Log View
        Surface(
            color = LabBackground,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No serial traffic recorded.", color = LabTextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .testTag("serial_terminal_list")
                ) {
                    items(filteredEntries, key = { it.id }) { item ->
                        val dirColor = if (item.direction == SerialDirection.TX) LabPrimary else LabSecondary
                        val displayText = item.getDisplayText(displayMode)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                "[${item.formattedTime}]",
                                color = LabTextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(82.dp)
                            )
                            Text(
                                "${item.direction}: ",
                                color = dirColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                displayText,
                                color = Color(0xFFD6E4F0),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Input command bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = { Text("Send ASCII command (e.g. PING, START)...", fontSize = 11.sp, color = LabTextMuted) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("serial_input_field"),
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
                    if (commandInput.isNotBlank()) {
                        viewModel.commandConsole.send(commandInput)
                        commandInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(LabPrimary, RoundedCornerShape(8.dp))
                    .testTag("serial_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.Black)
            }
        }
    }
}
