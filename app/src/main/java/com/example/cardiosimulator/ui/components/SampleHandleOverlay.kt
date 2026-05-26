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
    isEditable: Boolean = true
) {
    val scale = LocalPixelScale.current
    val density = LocalDensity.current
    
    val selectedRadiusPx = with(density) { 5.dp.toPx() }
    val strokeWidthPx = with(density) { 1.dp.toPx() }

    val stepX = scale.pxPerSample
    val handleColor = if (isEditable) Color.Red else Color.Gray

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(samples, stepX) {
                var lastIndex = -1
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Only handle selection if there's exactly one pointer active
                        // to avoid interfering with multi-touch gestures like zoom.
                        val change = if (event.changes.size == 1) event.changes.first() else null
                        
                        if (change != null && change.pressed) {
                            val sampleIndex = (change.position.x / stepX).roundToInt().coerceIn(samples.indices)
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
        
        // Only draw the selected handle
        if (selectedIndex != null && selectedIndex in samples.indices) {
            val sample = samples[selectedIndex]
            val x = selectedIndex * stepX
            val y = baselineY - (sample - baseline) * stepY

            drawCircle(
                color = handleColor,
                radius = selectedRadiusPx,
                center = Offset(x, y),
                style = Stroke(width = strokeWidthPx)
            )

            // Draw a cross inside
            val arm = selectedRadiusPx * 0.7f
            drawLine(
                color = handleColor,
                start = Offset(x - arm, y),
                end = Offset(x + arm, y),
                strokeWidth = strokeWidthPx
            )
            drawLine(
                color = handleColor,
                start = Offset(x, y - arm),
                end = Offset(x, y + arm),
                strokeWidth = strokeWidthPx
            )
        }
    }
}
