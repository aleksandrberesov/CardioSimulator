package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.ui.components.CalibrationPulse
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.theme.TextPrimary
import com.example.cardiosimulator.ui.theme.palette

const val LEAD_IN_DP = 8f
const val PULSE_WING_DP = 4f
const val PULSE_SECONDS = 0.2f
const val TITLE_GAP_DP = 4f
const val TITLE_AREA_DP = 32f
const val TITLE_CLEARANCE_DP = 18f
const val TITLE_LIFT_DP = 10f
const val TRACE_GAP_BASE_DP = 3f
const val TRACE_GAP_SECONDS = 0.05f

fun PixelScale.traceLeftPx(density: androidx.compose.ui.unit.Density): Float {
    val leadIn = with(density) { LEAD_IN_DP.dp.toPx() }
    val pulseWing = with(density) { PULSE_WING_DP.dp.toPx() }
    val pulseWidth = PULSE_SECONDS * pxPerSec
    val titleGap = with(density) { TITLE_GAP_DP.dp.toPx() }
    val titleClearance = with(density) { TITLE_CLEARANCE_DP.dp.toPx() }
    val traceGapBase = with(density) { TRACE_GAP_BASE_DP.dp.toPx() }
    val traceGapSpeed = TRACE_GAP_SECONDS * pxPerSec

    return leadIn + 2 * pulseWing + pulseWidth + titleGap + titleClearance + traceGapBase + traceGapSpeed
}

@Composable
fun Lead(
    points: Points,
    modifier: Modifier = Modifier,
    title: String = "",
    isRunning: Boolean = false,
    xOffsetPx: Float = 0f,
    gridScheme: GridScheme = GridScheme.Yellow,
    isCompareMode: Boolean = false,
    significantPoints: List<com.example.cardiosimulator.domain.SignificantPoint> = emptyList(),
    showImpulseLabels: Boolean = false,
    artifacts: Set<com.example.cardiosimulator.domain.EcgArtifact> = emptySet(),
    filterType: com.example.cardiosimulator.domain.EcgFilterType = com.example.cardiosimulator.domain.EcgFilterType.NONE,
    calibration: com.example.cardiosimulator.data.EcgCalibration = com.example.cardiosimulator.data.EcgCalibration()
){
    val traceColor = gridScheme.palette().trace
    val processedPoints = androidx.compose.runtime.remember(points, artifacts, filterType, calibration) {
        val active = com.example.cardiosimulator.domain.EcgArtifact.entries.filter { it != com.example.cardiosimulator.domain.EcgArtifact.None && it in artifacts }
        if ((active.isEmpty() && filterType == com.example.cardiosimulator.domain.EcgFilterType.NONE) || points.values.size < 50) {
            points
        } else {
            try {
                val signal = points.values.map { it.toDouble() }.toDoubleArray()
                val samplingRate = calibration.sampleRateHz.toDouble()

                if (active.isNotEmpty()) {
                    val refPp = com.example.cardiosimulator.signals.biosppy.EcgArtifactGenerator.peakToPeak(signal)
                    var seed = (title.hashCode() * 31)
                    for (kind in active) {
                        val noise = com.example.cardiosimulator.signals.biosppy.EcgArtifactGenerator.generate(signal.size, kind, samplingRate, refPp, 1.0, seed++)
                        for (i in signal.indices) signal[i] += noise[i]
                    }
                }

                val filtered = com.example.cardiosimulator.signals.biosppy.EcgFilters.apply(signal, filterType, samplingRate)
                Points(filtered.map { it.toFloat() })
            } catch (e: Exception) {
                points
            }
        }
    }

    val scale = LocalPixelScale.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val traceLeftPx = androidx.compose.runtime.remember(scale, density) { scale.traceLeftPx(density) }
    val traceLeftDp = with(density) { traceLeftPx.toDp() }

    Row(
        modifier = modifier.leadArea(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Calibration + Title area
        Box(
            modifier = Modifier
                .width(traceLeftDp)
                .fillMaxHeight()
        ) {
            // Pulse
            CalibrationPulse(modifier = Modifier.fillMaxSize(), color = traceColor)

            // Title (floats right of pulse, above isoline)
            if (!isCompareMode && title.isNotEmpty()) {
                val pulseRightPx = with(density) {
                    LEAD_IN_DP.dp.toPx() + 2f * PULSE_WING_DP.dp.toPx() + PULSE_SECONDS * scale.pxPerSec
                }
                val titleLeftDp = with(density) { (pulseRightPx + TITLE_GAP_DP.dp.toPx()).toDp() }

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    color = traceColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = titleLeftDp)
                        // Lifted: center shifted up by (lift + half font size) so bottom is roughly at lift.
                        .offset(y = (-TITLE_LIFT_DP - 7f).dp)
                        .width(TITLE_AREA_DP.dp)
                )
            }
        }

        // Trace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            PreviewPane(
                points = processedPoints,
                modifier = Modifier.fillMaxSize(),
                isRunning = isRunning,
                externalXOffsetPx = xOffsetPx,
                gridScheme = gridScheme,
                color = traceColor
            )

            if (showImpulseLabels && significantPoints.isNotEmpty()) {
                com.example.cardiosimulator.ui.components.SignificantPointOverlay(
                    points = processedPoints,
                    significantPoints = significantPoints,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isCompareMode && title.isNotEmpty()) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.sp,
                    color = traceColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 2.dp)
                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 600, heightDp = 150)
@Composable
fun LeadPreview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    val previewScale = PixelScale(
        pxPerMm = 6.3f,
        paperSpeedMmPerSec = 25f,
        gainZoomY = 1f,
        cal = EcgCalibration(),
    )
    CardioSimulatorTheme {
        CompositionLocalProvider(LocalPixelScale provides previewScale) {
            Box(modifier = Modifier.ekgGrid()) {
                Lead(
                    points = samplePoints,
                    title = "I",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
