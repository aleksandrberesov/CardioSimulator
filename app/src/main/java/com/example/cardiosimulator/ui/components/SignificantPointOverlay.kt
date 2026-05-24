package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.domain.EcgPointType
import com.example.cardiosimulator.domain.SignificantPoint
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb

/**
 * Renders labels for significant ECG points (P, Q, R, S, T) and provides
 * a UI to assign them to the currently selected sample.
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
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = primaryColor.toArgb()
    val fontSizePx = with(density) { 14.sp.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val baselineY = size.height / 2f
            
            significantPoints.forEach { pt ->
                if (pt.index in samples.indices) {
                    val sample = samples[pt.index]
                    val x = pt.index * stepX
                    val y = baselineY - (sample - baseline) * stepY
                    
                    // Draw label text using native canvas for simplicity with precise positioning
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = Paint().apply {
                            color = labelColor
                            textSize = fontSizePx
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.CENTER
                        }
                        // Draw label slightly above the point
                        drawText(pt.type.label, x, y - 20f, paint)
                    }
                    
                    // Optional: draw a small dot at the point
                    drawCircle(
                        color = primaryColor,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}
