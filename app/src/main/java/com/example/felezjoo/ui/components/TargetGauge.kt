package com.example.felezjoo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.models.TargetClassification
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabError
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceHighlight
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextPrimary
import com.example.ui.theme.LabTextSecondary

@Composable
fun TargetStatusHeader(
    targetScore: Double,
    confidence: Double,
    ironScore: Double,
    targetId: Int,
    classification: TargetClassification,
    modifier: Modifier = Modifier
) {
    val badgeColor by animateColorAsState(
        targetValue = when (classification) {
            TargetClassification.NO_TARGET -> LabTextMuted
            TargetClassification.POSSIBLE_TARGET -> LabTertiary
            TargetClassification.STABLE_TARGET -> LabSecondary
            TargetClassification.IRON -> LabError
            TargetClassification.NON_FERROUS -> LabPrimary
            TargetClassification.UNCERTAIN -> LabTertiary
        },
        label = "BadgeColor"
    )

    Surface(
        color = LabSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Classification Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = classification.label,
                        color = badgeColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 1.sp
                    )
                }

                // Target ID Box
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TARGET ID", fontSize = 10.sp, color = LabTextMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (targetScore > 20.0 && targetId > 0) "%02d".format(targetId) else "--",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = if (targetId > 0) LabTertiary else LabTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Score Meters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Target Score
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TARGET SCORE", fontSize = 11.sp, color = LabTextSecondary, fontWeight = FontWeight.SemiBold)
                        Text("%.0f / 100".format(targetScore), fontSize = 12.sp, color = LabPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (targetScore / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = LabPrimary,
                        trackColor = LabSurfaceVariant
                    )
                }

                // Confidence
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CONFIDENCE", fontSize = 11.sp, color = LabTextSecondary, fontWeight = FontWeight.SemiBold)
                        Text("%.0f%%".format(confidence), fontSize = 12.sp, color = LabSecondary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (confidence / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = LabSecondary,
                        trackColor = LabSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Iron Score bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "EXP. IRON SCORE: ",
                    fontSize = 10.sp,
                    color = LabTextMuted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "%.0f ( %s )".format(
                        ironScore,
                        when {
                            ironScore < 25.0 -> "NON-FERROUS"
                            ironScore < 50.0 -> "UNCERTAIN"
                            ironScore < 75.0 -> "LIKELY FERROUS"
                            else -> "STRONG FERROUS"
                        }
                    ),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (ironScore > 50.0) LabError else LabTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TechnicalStatBadge(
    label: String,
    value: String,
    unit: String = "",
    color: Color = LabPrimary,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LabSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label.uppercase(), fontSize = 9.sp, color = LabTextMuted, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(unit, fontSize = 10.sp, color = LabTextSecondary, modifier = Modifier.padding(bottom = 1.dp))
                }
            }
        }
    }
}
