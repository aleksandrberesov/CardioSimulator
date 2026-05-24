package com.example.cardiosimulator.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.domain.EcgPointType
import com.example.cardiosimulator.domain.SignificantPoint
import java.util.Locale

/**
 * Renders labels for significant ECG points and draws interval measurements
 * (P duration, PQ interval, QRS duration, ST segment, T duration, QT interval).
 */
@Composable
fun SignificantPointOverlay(
    samples: IntArray,
    baseline: Int,
    significantPoints: List<SignificantPoint>,
    modifier: Modifier = Modifier
) {
    val scale = LocalPixelScale.current
    val density = LocalDensity.current
    
    val stepX = scale.pxPerSample
    val stepY = scale.pxPerAdcCount
    val sampleRate = scale.cal.sampleRateHz
    
    val primaryColor = Color(0xFFD32F2F) // Deep Red for points and boundaries
    val intervalColor = Color(0xFF1976D2) // Bright Blue for intervals
    val segmentColor = Color(0xFF388E3C) // Green for segments (like ST)
    
    val fontSizePx = with(density) { 14.sp.toPx() } // Slightly larger font

    // Pre-resolve strings to avoid @Composable invocation inside Canvas
    val qrsLabel = stringResource(R.string.ecg_interval_qrs)
    val prLabel = stringResource(R.string.ecg_interval_pr)
    val stLabel = stringResource(R.string.ecg_interval_st)
    val pLabel = stringResource(R.string.ecg_interval_p)
    val tLabel = stringResource(R.string.ecg_interval_t)
    val qtLabel = stringResource(R.string.ecg_interval_qt)
    val rrFormat = stringResource(R.string.ecg_rr_value_format)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baselineY = size.height / 2f
            
            // 1. Draw individual point markers and labels
            significantPoints.forEach { pt ->
                if (pt.index in samples.indices) {
                    val sample = samples[pt.index]
                    val x = pt.index * stepX
                    val y = baselineY - (sample - baseline) * stepY
                    
                    // Draw vertical boundary lines for start/end points
                    if (pt.type.name.endsWith("_START") || pt.type.name.endsWith("_END")) {
                        drawLine(
                            color = primaryColor.copy(alpha = 0.6f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.5.dp.toPx() // Thicker boundary line
                        )
                    }

                    // Only draw text labels for peaks (P, Q, R, S, T), not for boundaries
                    if (!pt.type.name.endsWith("_START") && !pt.type.name.endsWith("_END")) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = Paint().apply {
                                color = primaryColor.toArgb()
                                textSize = fontSizePx
                                typeface = Typeface.DEFAULT_BOLD
                                textAlign = Paint.Align.CENTER
                                // Add a subtle shadow for better contrast
                                setShadowLayer(3f, 0f, 0f, Color.White.toArgb())
                            }
                            
                            val cleanLabel = pt.type.name.replace("_PEAK", "")
                            drawText(cleanLabel, x, y - 20f, paint)
                        }
                    }
                    
                    // Larger, more prominent circle
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                    // White inner dot for extra contrast
                    drawCircle(
                        color = Color.White,
                        radius = 1.5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            // 2. Draw Intervals and Segments
            val pointsMap = significantPoints.associateBy { it.type }
            
            /**
             * @param label The text label to display.
             * @param y Absolute Y position.
             * @param color Color of the interval marker.
             * @param isBelow If true, text is drawn below the line.
             */
            fun drawInterval(
                startType: EcgPointType, 
                endType: EcgPointType, 
                label: String, 
                y: Float, 
                color: Color,
                isBelow: Boolean = false
            ) {
                val start = pointsMap[startType]?.index ?: return
                val end = pointsMap[endType]?.index ?: return
                if (start >= end) return
                
                val x1 = start * stepX
                val x2 = end * stepX
                val duration = (end - start) / sampleRate
                
                // Draw horizontal bar with more prominent brackets
                val bracketSize = 8f
                drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = 3.dp.toPx())
                drawLine(color, Offset(x1, y - bracketSize), Offset(x1, y + bracketSize), strokeWidth = 3.dp.toPx())
                drawLine(color, Offset(x2, y - bracketSize), Offset(x2, y + bracketSize), strokeWidth = 3.dp.toPx())
                
                drawContext.canvas.nativeCanvas.apply {
                    val paint = Paint().apply {
                        this.color = color.toArgb()
                        textSize = fontSizePx
                        typeface = Typeface.MONOSPACE
                        textAlign = Paint.Align.CENTER
                        setShadowLayer(4f, 0f, 0f, Color.White.toArgb())
                    }
                    val text = String.format(Locale.US, "%s %.3fs", label, duration)
                    val textY = if (isBelow) y + fontSizePx + 5f else y - 12f
                    drawText(text, (x1 + x2) / 2f, textY, paint)
                }
            }

            // Define intervals with vertical positions matching the reference scheme
            
            // 1. QRS Complex (At the top of the wave)
            val rPeak = pointsMap[EcgPointType.R_PEAK]
            val qrsY = if (rPeak != null) {
                baselineY - (samples[rPeak.index] - baseline) * stepY - 40f
            } else 40f
            drawInterval(EcgPointType.QRS_START, EcgPointType.QRS_END, qrsLabel, qrsY, Color(0xFFD32F2F))

            // 2. Segments (Slightly above baseline)
            drawInterval(EcgPointType.P_END, EcgPointType.QRS_START, prLabel, baselineY - 40f, segmentColor)
            drawInterval(EcgPointType.QRS_END, EcgPointType.T_START, stLabel, baselineY - 40f, Color(0xFF7B1FA2)) // Purple for ST

            // 3. Wave durations (Above their respective peaks)
            val pPeak = pointsMap[EcgPointType.P_PEAK]
            val pY = if (pPeak != null) baselineY - (samples[pPeak.index] - baseline) * stepY - 30f else baselineY - 60f
            drawInterval(EcgPointType.P_START, EcgPointType.P_END, pLabel, pY, intervalColor)

            val tPeak = pointsMap[EcgPointType.T_PEAK]
            val tY = if (tPeak != null) baselineY - (samples[tPeak.index] - baseline) * stepY - 30f else baselineY - 60f
            drawInterval(EcgPointType.T_START, EcgPointType.T_END, tLabel, tY, intervalColor)

            // 4. Long Intervals (Below baseline)
            drawInterval(EcgPointType.P_START, EcgPointType.QRS_START, prLabel, baselineY + 60f, Color(0xFFE64A19), isBelow = true) // Orange
            drawInterval(EcgPointType.QRS_START, EcgPointType.T_END, qtLabel, baselineY + 100f, intervalColor, isBelow = true)

            // 5. R-R Intervals (Distance between R peaks)
            val rPeaks = significantPoints.filter { it.type == EcgPointType.R_PEAK }.sortedBy { it.index }
            rPeaks.windowed(2).forEach { (r1, r2) ->
                val x1 = r1.index * stepX
                val x2 = r2.index * stepX
                val duration = (r2.index - r1.index) / sampleRate
                
                // Draw at the very top
                val y = 30f
                val color = Color(0xFF2E7D32) // Dark Green for R-R
                
                drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = 3.dp.toPx())
                val bracketSize = 8f
                drawLine(color, Offset(x1, y - bracketSize), Offset(x1, y + bracketSize), strokeWidth = 3.dp.toPx())
                drawLine(color, Offset(x2, y - bracketSize), Offset(x2, y + bracketSize), strokeWidth = 3.dp.toPx())

                drawContext.canvas.nativeCanvas.apply {
                    val paint = Paint().apply {
                        this.color = color.toArgb()
                        textSize = fontSizePx
                        typeface = Typeface.MONOSPACE
                        textAlign = Paint.Align.CENTER
                        setShadowLayer(4f, 0f, 0f, Color.White.toArgb())
                    }
                    val text = String.format(Locale.US, rrFormat, duration)
                    drawText(text, (x1 + x2) / 2f, y + fontSizePx + 5f, paint)
                }
            }
        }
    }
}
