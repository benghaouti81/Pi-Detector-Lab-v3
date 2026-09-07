package com.example.felezjoo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextSecondary

@Composable
fun RollingHistoryGraph(
    historyScores: List<Double>,
    historySnr: List<Double>,
    historyNoise: List<Double>,
    maxHistoryPoints: Int = 64,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TEMPORAL HISTORY (SWEEP SIGNATURE)", fontSize = 10.sp, color = LabTextMuted, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(8.dp).height(2.dp).background(LabPrimary))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Score", fontSize = 9.sp, color = LabTextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.width(8.dp).height(2.dp).background(LabSecondary))
                Spacer(modifier = Modifier.width(3.dp))
                Text("SNR", fontSize = 9.sp, color = LabTextSecondary)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(LabBackground, RoundedCornerShape(6.dp))
                .padding(4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0 || h <= 0) return@Canvas

                // Grid lines
                drawLine(LabBorder.copy(alpha = 0.3f), Offset(0f, h * 0.25f), Offset(w, h * 0.25f), 1f)
                drawLine(LabBorder.copy(alpha = 0.3f), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 1f)
                drawLine(LabBorder.copy(alpha = 0.3f), Offset(0f, h * 0.75f), Offset(w, h * 0.75f), 1f)

                if (historyScores.size >= 2) {
                    val count = historyScores.size
                    val pathScore = Path()
                    val pathSnr = Path()

                    for (i in 0 until count) {
                        val x = (i.toFloat() / (maxHistoryPoints - 1).coerceAtLeast(1)) * w
                        val scoreNorm = (historyScores[i] / 100.0).coerceIn(0.0, 1.0)
                        val yScore = h - (scoreNorm.toFloat() * h)

                        val snrVal = historySnr.getOrNull(i) ?: 0.0
                        val snrNorm = (snrVal / 25.0).coerceIn(0.0, 1.0)
                        val ySnr = h - (snrNorm.toFloat() * h)

                        if (i == 0) {
                            pathScore.moveTo(x, yScore)
                            pathSnr.moveTo(x, ySnr)
                        } else {
                            pathScore.lineTo(x, yScore)
                            pathSnr.lineTo(x, ySnr)
                        }
                    }

                    drawPath(pathSnr, LabSecondary.copy(alpha = 0.7f), style = Stroke(width = 1.5f))
                    drawPath(pathScore, LabPrimary, style = Stroke(width = 2.0f))
                }
            }
        }
    }
}
