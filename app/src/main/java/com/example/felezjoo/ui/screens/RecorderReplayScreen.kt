package com.example.felezjoo.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.felezjoo.storage.DataExportHelper
import com.example.felezjoo.storage.SessionEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecorderReplayScreen(viewModel: FelezJooViewModel) {
    val isRecording by viewModel.isRecording.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val replayEngine = viewModel.replayEngine
    val isPlaying by replayEngine.isPlaying.collectAsState()
    val currentIndex by replayEngine.currentIndex.collectAsState()
    val totalBlocks by replayEngine.totalBlocks.collectAsState()

    val sessionsFlow = remember { viewModel.database.sessionDao().getAllSessions() }
    val sessionList by sessionsFlow.collectAsState(initial = emptyList())

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var newSessionName by remember { mutableStateOf("Test Run ${System.currentTimeMillis() % 10000}") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Text("DATA RECORDER & OFFLINE REPLAY ENGINE", fontSize = 14.sp, fontWeight = FontWeight.Black, color = LabPrimary)
        Text("Record raw decay blocks to SQLite, export CSV/JSON, and replay through DSP filters", fontSize = 11.sp, color = LabTextSecondary)

        Spacer(modifier = Modifier.height(10.dp))

        // Recording Control Panel
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isRecording) LabError else LabBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    if (isRecording) "RECORDING ACTIVE: ${activeSession?.name}" else "START NEW RECORDING SESSION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) LabError else LabSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRecording) {
                        OutlinedTextField(
                            value = newSessionName,
                            onValueChange = { newSessionName = it },
                            placeholder = { Text("Session Name...") },
                            modifier = Modifier.weight(1f).height(46.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = LabSurfaceVariant,
                                unfocusedContainerColor = LabSurfaceVariant,
                                focusedBorderColor = LabPrimary,
                                unfocusedBorderColor = LabBorder
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = {
                            if (isRecording) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording(newSessionName)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) LabError else LabSecondary,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.height(46.dp).testTag("record_session_toggle_btn")
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = if (isRecording) Color.White else Color.Red
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRecording) "STOP RECORD" else "REC", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Replay Engine Panel
        if (totalBlocks > 0) {
            Surface(
                color = LabSurface,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LabPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("REPLAY ENGINE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LabPrimary)
                        Text(
                            "BLOCK $currentIndex / $totalBlocks",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }

                    // Seek Slider
                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = { replayEngine.seekTo(it.toInt()) },
                        valueRange = 0f..totalBlocks.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = LabPrimary, activeTrackColor = LabPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Replay Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { replayEngine.restart() }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Restart", tint = Color.White)
                        }
                        IconButton(onClick = { replayEngine.stepBackward() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (isPlaying) replayEngine.pause() else replayEngine.play()
                            },
                            modifier = Modifier.size(40.dp).background(LabPrimary, RoundedCornerShape(20.dp))
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black
                            )
                        }
                        IconButton(onClick = { replayEngine.stepForward() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Speed Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Speed: ", fontSize = 10.sp, color = LabTextSecondary)
                        listOf(0.25f, 0.5f, 1.0f, 2.0f, 4.0f).forEach { spd ->
                            FilterChip(
                                selected = replayEngine.replaySpeed == spd,
                                onClick = { replayEngine.replaySpeed = spd },
                                label = { Text("${spd}x", fontSize = 9.sp) },
                                modifier = Modifier.padding(horizontal = 2.dp).height(26.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Saved Sessions List
        Text("SAVED SESSIONS (${sessionList.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = LabBackground,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (sessionList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No saved sessions. Record a test run to inspect data.", color = LabTextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                    items(sessionList, key = { it.id }) { s ->
                        Surface(
                            color = LabSurfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(s.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        Text("Device: ${s.deviceName}", fontSize = 10.sp, color = LabTextSecondary)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Load for Replay
                                        OutlinedButton(
                                            onClick = { viewModel.loadSessionForReplay(s) },
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("LOAD REPLAY", fontSize = 9.sp)
                                        }

                                        // Export CSV
                                        IconButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    val blocks = viewModel.database.decayBlockDao().getBlocksForSession(s.id)
                                                    val csv = DataExportHelper.generateCsv(blocks)
                                                    withContext(Dispatchers.Main) {
                                                        clipboardManager.setText(AnnotatedString(csv))
                                                        Toast.makeText(context, "CSV copied (${blocks.size} blocks)", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = LabPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
