package com.example.felezjoo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.models.TargetClassification
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabError
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary

@Composable
fun FieldModeScreen(viewModel: FelezJooViewModel) {
    val dspResult by viewModel.dspResult.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val audioManager = viewModel.audioManager
    val fv = dspResult?.featureVector
    val classification = dspResult?.targetClassification ?: TargetClassification.NO_TARGET

    val classColor = when (classification) {
        TargetClassification.STABLE_TARGET -> LabSecondary
        TargetClassification.IRON -> LabError
        TargetClassification.POSSIBLE_TARGET -> LabTertiary
        TargetClassification.NON_FERROUS -> LabPrimary
        TargetClassification.NO_TARGET -> Color.Gray
        else -> LabTertiary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top High-Contrast Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(classColor.copy(alpha = 0.25f))
                .border(2.dp, classColor, RoundedCornerShape(12.dp))
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = classification.label,
                color = classColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }

        // Massive Target ID Circle / Box
        Surface(
            color = LabSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, LabBorder),
            modifier = Modifier
                .size(200.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("TARGET ID", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text(
                    text = if ((fv?.targetScore ?: 0.0) > 20.0 && (fv?.targetId ?: 0) > 0) "%02d".format(fv?.targetId) else "--",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = if ((fv?.targetId ?: 0) > 0) LabTertiary else Color.Gray
                )
            }
        }

        // Giant Target Score Bar
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TARGET SCORE", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    "%.0f / 100".format(fv?.targetScore ?: 0.0),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = LabPrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ((fv?.targetScore ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp)),
                color = LabPrimary,
                trackColor = LabSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CONFIDENCE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("%.0f%%".format(fv?.targetConfidence ?: 0.0), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LabSecondary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { ((fv?.targetConfidence ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                color = LabSecondary,
                trackColor = LabSurfaceVariant
            )
        }

        // Giant Field Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (isStreaming) viewModel.stopStreaming() else viewModel.startStreaming()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) LabError else LabSecondary,
                    contentColor = Color.Black
                ),
                modifier = Modifier.weight(1f).height(56.dp).testTag("field_stream_btn")
            ) {
                Icon(if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isStreaming) "STOP" else "START DETECTING", fontSize = 15.sp, fontWeight = FontWeight.Black)
            }

            IconButton(
                onClick = { audioManager.isMuted = !audioManager.isMuted },
                modifier = Modifier
                    .size(56.dp)
                    .background(LabSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, LabBorder, RoundedCornerShape(12.dp))
                    .testTag("field_mute_btn")
            ) {
                Icon(
                    if (audioManager.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Mute Toggle",
                    tint = if (audioManager.isMuted) LabError else LabPrimary
                )
            }
        }
    }
}
