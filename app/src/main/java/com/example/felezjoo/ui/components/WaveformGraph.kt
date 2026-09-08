package com.example.felezjoo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.models.DspProfile
import com.example.ui.theme.BandAColor
import com.example.ui.theme.BandBColor
import com.example.ui.theme.BandCColor
import com.example.ui.theme.IntegrationWindowColor
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceHighlight
import com.example.ui.theme.WaveBaseline
import com.example.ui.theme.WaveCurvature
import com.example.ui.theme.WaveDerivative
import com.example.ui.theme.WaveFiltered
import com.example.ui.theme.WaveGround
import com.example.ui.theme.WaveRaw
import com.example.ui.theme.WaveResidual
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaveformGraph(
    rawSamples: IntArray,
    filteredCurve: DoubleArray,
    baselineCurve: DoubleArray,
    groundCurve: DoubleArray,
    residualCurve: DoubleArray,
    firstDerivative: DoubleArray,
    secondDerivative: DoubleArray,
    profile: DspProfile,
    samplingConfiguration: com.example.felezjoo.models.SamplingConfiguration? = null,
    sampleSpacingUs: Double? = null,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    normalizedResidualCurve: DoubleArray = residualCurve
) {
    var showRaw by remember { mutableStateOf(true) }
    var showFiltered by remember { mutableStateOf(true) }
    var showBaseline by remember { mutableStateOf(false) }
    var showGround by remember { mutableStateOf(true) }
    var showResidual by remember { mutableStateOf(true) }
    var showNormalizedResidual by remember { mutableStateOf(false) }
    var showDerivative by remember { mutableStateOf(false) }
    var showCurvature by remember { mutableStateOf(false) }
    var showBands by remember { mutableStateOf(true) }

    var selectedCursorIndex by remember { mutableIntStateOf(-1) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    val sampleCount = rawSamples.size.coerceAtLeast(1)
    val textMeasurer = rememberTextMeasurer()

    val effectiveConfig = samplingConfiguration
        ?: com.example.felezjoo.models.SamplingConfiguration(
            sampleCount = sampleCount,
            sampleSpacingUs = sampleSpacingUs ?: 0.0
        )
    val effectiveSpacingUs = if (sampleSpacingUs != null && sampleSpacingUs > 0.0) {
        sampleSpacingUs
    } else {
        effectiveConfig.sampleSpacingUs
    }

    Column(modifier = modifier) {
        if (showControls) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CurveToggleChip("RAW", WaveRaw, showRaw) { showRaw = it }
                CurveToggleChip("FILTERED", WaveFiltered, showFiltered) { showFiltered = it }
                CurveToggleChip("RESIDUAL", WaveResidual, showResidual) { showResidual = it }
                CurveToggleChip("NORM RES", Color(0xFFFF9100), showNormalizedResidual) { showNormalizedResidual = it }
                CurveToggleChip("GROUND", WaveGround, showGround) { showGround = it }
                CurveToggleChip("BASELINE", WaveBaseline, showBaseline) { showBaseline = it }
                CurveToggleChip("D1 (SLOPE)", WaveDerivative, showDerivative) { showDerivative = it }
                CurveToggleChip("D2 (CURV)", WaveCurvature, showCurvature) { showCurvature = it }
                CurveToggleChip("A/B/C & INT", Color(0xFF00E5FF), showBands) { showBands = it }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale * 1.3f).coerceAtMost(5.0f) },
                        modifier = Modifier.size(32.dp).testTag("zoom_in_button")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                    }
                    IconButton(
                        onClick = { zoomScale = (zoomScale / 1.3f).coerceAtLeast(0.5f) },
                        modifier = Modifier.size(32.dp).testTag("zoom_out_button")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                    }
                    IconButton(
                        onClick = { zoomScale = 1.0f; selectedCursorIndex = -1 },
                        modifier = Modifier.size(32.dp).testTag("reset_graph_button")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom", tint = Color.White)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(LabBackground, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .pointerInput(sampleCount) {
                    detectTapGestures { offset ->
                        val w = size.width
                        val paddingLeft = 50f
                        val graphWidth = w - paddingLeft - 20f
                        if (offset.x >= paddingLeft && offset.x <= w - 20f && graphWidth > 0) {
                            val relX = (offset.x - paddingLeft) / graphWidth
                            selectedCursorIndex = (relX * sampleCount).toInt().coerceIn(0, sampleCount - 1)
                        } else {
                            selectedCursorIndex = -1
                        }
                    }
                }
                .pointerInput(sampleCount) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val w = size.width
                        val paddingLeft = 50f
                        val graphWidth = w - paddingLeft - 20f
                        if (change.position.x >= paddingLeft && change.position.x <= w - 20f && graphWidth > 0) {
                            val relX = (change.position.x - paddingLeft) / graphWidth
                            selectedCursorIndex = (relX * sampleCount).toInt().coerceIn(0, sampleCount - 1)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().testTag("waveform_canvas")) {
                val paddingLeft = 50f
                val paddingBottom = 30f
                val paddingTop = 20f
                val paddingRight = 20f

                val graphWidth = size.width - paddingLeft - paddingRight
                val graphHeight = size.height - paddingTop - paddingBottom
                if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

                // Compute Y range dynamically
                var minY = 0.0
                var maxY = 1024.0

                if (!showRaw && showResidual) {
                    minY = -50.0
                    maxY = 250.0
                }

                // Adjust for zoom
                val ySpan = (maxY - minY) / zoomScale
                val curMinY = minY
                val curMaxY = curMinY + ySpan

                fun mapX(index: Int): Float {
                    return paddingLeft + (index.toFloat() / (sampleCount - 1).coerceAtLeast(1)) * graphWidth
                }

                fun mapY(value: Double): Float {
                    val norm = (value - curMinY) / (curMaxY - curMinY)
                    return paddingTop + (1.0f - norm.toFloat()) * graphHeight
                }

                // Draw Grid
                val gridStroke = Stroke(width = 1f)
                val gridColor = LabBorder.copy(alpha = 0.5f)

                // Horizontal grid lines (ADC values)
                val ySteps = 5
                for (step in 0..ySteps) {
                    val yVal = curMinY + step * (curMaxY - curMinY) / ySteps
                    val yPos = mapY(yVal)
                    drawLine(gridColor, Offset(paddingLeft, yPos), Offset(size.width - paddingRight, yPos), strokeWidth = 1f)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${yVal.toInt()}",
                        topLeft = Offset(4f, yPos - 8f),
                        style = TextStyle(color = LabBorder, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    )
                }

                // Vertical grid lines (Time / Sample indices)
                val xSteps = 7
                for (step in 0..xSteps) {
                    val sIdx = (step * (sampleCount - 1) / xSteps)
                    val xPos = mapX(sIdx)
                    val label = if (effectiveSpacingUs > 0.0) "%.0fµs".format(sIdx * effectiveSpacingUs) else "S$sIdx"
                    drawLine(gridColor, Offset(xPos, paddingTop), Offset(xPos, size.height - paddingBottom), strokeWidth = 1f)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(xPos - 12f, size.height - paddingBottom + 4f),
                        style = TextStyle(color = LabBorder, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    )
                }

                // Draw Region Bands (A, B, C & Integration Window)
                if (showBands) {
                    val (aStart, aEnd) = profile.getRegionAIndices(effectiveConfig)
                    val aStartX = mapX(aStart.coerceIn(0, sampleCount - 1))
                    val aEndX = mapX((aEnd - 1).coerceIn(0, sampleCount - 1))
                    drawRect(BandAColor, Offset(aStartX, paddingTop), Size(max(1f, aEndX - aStartX), graphHeight))

                    val (bStart, bEnd) = profile.getRegionBIndices(effectiveConfig)
                    val bStartX = mapX(bStart.coerceIn(0, sampleCount - 1))
                    val bEndX = mapX((bEnd - 1).coerceIn(0, sampleCount - 1))
                    drawRect(BandBColor, Offset(bStartX, paddingTop), Size(max(1f, bEndX - bStartX), graphHeight))

                    val (cStart, cEnd) = profile.getRegionCIndices(effectiveConfig)
                    val cStartX = mapX(cStart.coerceIn(0, sampleCount - 1))
                    val cEndX = mapX((cEnd - 1).coerceIn(0, sampleCount - 1))
                    drawRect(BandCColor, Offset(cStartX, paddingTop), Size(max(1f, cEndX - cStartX), graphHeight))

                    // Integration window top stripe
                    val (intStart, intEnd) = profile.getIntegrationIndices(effectiveConfig)
                    val intStartX = mapX(intStart.coerceIn(0, sampleCount - 1))
                    val intEndX = mapX((intEnd - 1).coerceIn(0, sampleCount - 1))
                    drawRect(IntegrationWindowColor, Offset(intStartX, paddingTop), Size(max(1f, intEndX - intStartX), graphHeight))
                    drawLine(Color(0xFF00E5FF), Offset(intStartX, paddingTop + 2f), Offset(intEndX, paddingTop + 2f), strokeWidth = 3f)
                }

                // Plot Curves
                if (showBaseline && baselineCurve.isNotEmpty()) {
                    drawDataCurve(baselineCurve, ::mapX, ::mapY, WaveBaseline, strokeWidth = 1.5f)
                }
                if (showGround && groundCurve.isNotEmpty()) {
                    drawDataCurve(groundCurve, ::mapX, ::mapY, WaveGround, strokeWidth = 2.0f)
                }
                if (showRaw && rawSamples.isNotEmpty()) {
                    val rawDoubles = DoubleArray(rawSamples.size) { rawSamples[it].toDouble() }
                    drawDataCurve(rawDoubles, ::mapX, ::mapY, WaveRaw.copy(alpha = 0.6f), strokeWidth = 1.8f)
                }
                if (showFiltered && filteredCurve.isNotEmpty()) {
                    drawDataCurve(filteredCurve, ::mapX, ::mapY, WaveFiltered, strokeWidth = 2.5f)
                }
                if (showResidual && residualCurve.isNotEmpty()) {
                    drawDataCurve(residualCurve, ::mapX, ::mapY, WaveResidual, strokeWidth = 2.0f)
                }
                if (showNormalizedResidual && normalizedResidualCurve.isNotEmpty()) {
                    drawDataCurve(normalizedResidualCurve, ::mapX, ::mapY, Color(0xFFFF9100), strokeWidth = 2.0f)
                }
                if (showDerivative && firstDerivative.isNotEmpty()) {
                    // Scaled for visual comparison
                    val scaledD1 = DoubleArray(firstDerivative.size) { firstDerivative[it] * 4.0 + 200.0 }
                    drawDataCurve(scaledD1, ::mapX, ::mapY, WaveDerivative, strokeWidth = 1.5f)
                }
                if (showCurvature && secondDerivative.isNotEmpty()) {
                    val scaledD2 = DoubleArray(secondDerivative.size) { secondDerivative[it] * 8.0 + 150.0 }
                    drawDataCurve(scaledD2, ::mapX, ::mapY, WaveCurvature, strokeWidth = 1.5f)
                }

                // Draw Cursor line and values
                if (selectedCursorIndex in 0 until sampleCount) {
                    val cursorX = mapX(selectedCursorIndex)
                    drawLine(Color.White, Offset(cursorX, paddingTop), Offset(cursorX, size.height - paddingBottom), strokeWidth = 1.5f)

                    // Draw dot on filtered curve
                    val curVal = filteredCurve.getOrNull(selectedCursorIndex) ?: rawSamples.getOrNull(selectedCursorIndex)?.toDouble() ?: 0.0
                    val cursorY = mapY(curVal).coerceIn(paddingTop, size.height - paddingBottom)
                    drawCircle(Color.White, radius = 5f, center = Offset(cursorX, cursorY))
                    drawCircle(WaveFiltered, radius = 3f, center = Offset(cursorX, cursorY))
                }
            }

            // Cursor Tooltip Overlay
            if (selectedCursorIndex in 0 until sampleCount) {
                val idx = selectedCursorIndex
                val timeStr = if (effectiveSpacingUs > 0.0) "  (%.1f µs)".format(idx * effectiveSpacingUs) else ""
                val rawVal = rawSamples.getOrNull(idx) ?: 0
                val fltVal = filteredCurve.getOrNull(idx) ?: 0.0
                val resVal = residualCurve.getOrNull(idx) ?: 0.0
                val gndVal = groundCurve.getOrNull(idx) ?: 0.0

                Surface(
                    color = LabSurfaceHighlight.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LabBorder),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Cursor: Sample #$idx$timeStr",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("RAW ADC: $rawVal", color = WaveRaw, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("FILTERED: %.1f".format(fltVal), color = WaveFiltered, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("RESIDUAL: %.1f".format(resVal), color = WaveResidual, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("GROUND:   %.1f".format(gndVal), color = WaveGround, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawDataCurve(
    data: DoubleArray,
    mapX: (Int) -> Float,
    mapY: (Double) -> Float,
    color: Color,
    strokeWidth: Float
) {
    if (data.size < 2) return
    val path = Path()
    path.moveTo(mapX(0), mapY(data[0]))
    for (i in 1 until data.size) {
        path.lineTo(mapX(i), mapY(data[i]))
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun CurveToggleChip(
    label: String,
    color: Color,
    selected: Boolean,
    onSelectedChanged: (Boolean) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChanged(!selected) },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.25f),
            selectedLabelColor = Color.White,
            containerColor = LabSurface,
            labelColor = Color.Gray
        ),
        modifier = Modifier.height(28.dp)
    )
}
