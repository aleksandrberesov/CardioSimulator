package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.LocalPixelScale
import kotlin.math.roundToInt

/**
 * An overlay that places draggable handles over rendered ECG samples.
 * On drag, it mutates the raw ADC value of the nearest sample.
 */
@Composable
fun SampleHandleOverlay(
    samples: IntArray,
    baseline: Int,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onIndexSelected: ((Int) -> Unit)? = null,
    scrollOffsetPx: Float? = null,
) {
    val scale = LocalPixelScale.current
    val density = LocalDensity.current
    
    val selectedRadiusPx = with(density) { 5.dp.toPx() }
    val strokeWidthPx = with(density) { 1.dp.toPx() }

    val stepX = scale.pxPerSample

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(samples, stepX, scrollOffsetPx) {
                var lastIndex = -1
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Only handle selection if there's exactly one pointer active
                        val change = if (event.changes.size == 1) event.changes.first() else null
                        
                        if (change != null && change.pressed) {
                            val xOffset = scrollOffsetPx ?: 0f
                            // Adjust for scroll offset and loop
                            val dataWidthPx = samples.size * stepX
                            val adjustedX = (change.position.x - xOffset) % dataWidthPx
                            val finalX = if (adjustedX < 0) adjustedX + dataWidthPx else adjustedX
                            
                            val sampleIndex = (finalX / stepX).roundToInt().coerceIn(samples.indices)
                            if (sampleIndex != lastIndex) {
                                onIndexSelected?.invoke(sampleIndex)
                                lastIndex = sampleIndex
                            }
                        } else {
                            lastIndex = -1
                        }
                    }
                }
            }
    ) {
        val baselineY = size.height / 2f
        val stepY = scale.pxPerAdcCount
        val xOffset = scrollOffsetPx ?: 0f
        
        // Only draw the selected handle
        if (selectedIndex != null && selectedIndex in samples.indices) {
            val sample = samples[selectedIndex]
            val dataWidthPx = samples.size * stepX
            val rawX = selectedIndex * stepX
            
            // Draw handle with looping support
            val iterations = if (scrollOffsetPx != null) (size.width / dataWidthPx).toInt() + 2 else 1
            for (i in (if (scrollOffsetPx != null) -1 else 0) until iterations) {
                val x = rawX + (xOffset % dataWidthPx) + i * dataWidthPx
                if (x < -selectedRadiusPx || x > size.width + selectedRadiusPx) continue
                
                val y = baselineY - (sample - baseline) * stepY

                drawCircle(
                    color = Color.Red,
                    radius = selectedRadiusPx,
                    center = Offset(x, y),
                    style = Stroke(width = strokeWidthPx)
                )

                // Draw a cross inside
                val arm = selectedRadiusPx * 0.7f
                drawLine(
                    color = Color.Red,
                    start = Offset(x - arm, y),
                    end = Offset(x + arm, y),
                    strokeWidth = strokeWidthPx
                )
                drawLine(
                    color = Color.Red,
                    start = Offset(x, y - arm),
                    end = Offset(x, y + arm),
                    strokeWidth = strokeWidthPx
                )
            }
        }
    }
}
