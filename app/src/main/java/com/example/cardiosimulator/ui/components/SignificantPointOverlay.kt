package com.example.cardiosimulator.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = Paint().apply {
                            color = primaryColor.toArgb()
                            textSize = fontSizePx
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.CENTER
                            // Add a subtle shadow for better contrast
                            setShadowLayer(3f, 0f, 0f, Color.White.toArgb())
                        }
                        
                        val cleanLabel = pt.type.name
                            .replace("_PEAK", "")
                            .replace("_START", "s")
                            .replace("_END", "e")
                        
                        drawText(cleanLabel, x, y - 20f, paint)
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
             * @param yPos Absolute Y position if positive, or relative to baseline if specified via another method.
             *             Let's use a simpler approach: yBase + offset.
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
                    val text = String.format(Locale.US, "%s: %.3fs", label, duration)
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
            drawInterval(EcgPointType.QRS_START, EcgPointType.QRS_END, "QRS", qrsY, Color(0xFFD32F2F))

            // 2. Segments (Slightly above baseline)
            drawInterval(EcgPointType.P_END, EcgPointType.QRS_START, "PR Seg", baselineY - 40f, segmentColor)
            drawInterval(EcgPointType.QRS_END, EcgPointType.T_START, "ST Seg", baselineY - 40f, Color(0xFF7B1FA2)) // Purple for ST

            // 3. Wave durations (Above their respective peaks)
            val pPeak = pointsMap[EcgPointType.P_PEAK]
            val pY = if (pPeak != null) baselineY - (samples[pPeak.index] - baseline) * stepY - 30f else baselineY - 60f
            drawInterval(EcgPointType.P_START, EcgPointType.P_END, "P", pY, intervalColor)

            val tPeak = pointsMap[EcgPointType.T_PEAK]
            val tY = if (tPeak != null) baselineY - (samples[tPeak.index] - baseline) * stepY - 30f else baselineY - 60f
            drawInterval(EcgPointType.T_START, EcgPointType.T_END, "T", tY, intervalColor)

            // 4. Long Intervals (Below baseline)
            drawInterval(EcgPointType.P_START, EcgPointType.QRS_START, "PR Int", baselineY + 60f, Color(0xFFE64A19), isBelow = true) // Orange
            drawInterval(EcgPointType.QRS_START, EcgPointType.T_END, "QT Int", baselineY + 100f, intervalColor, isBelow = true)
        }
    }
}
