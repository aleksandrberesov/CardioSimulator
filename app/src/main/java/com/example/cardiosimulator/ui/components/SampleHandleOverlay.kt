package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.LocalPixelScale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * An overlay that places draggable handles over rendered ECG samples.
 * On drag, it mutates the raw ADC value of the nearest sample.
 */
@Composable
fun SampleHandleOverlay(
    samples: IntArray,
    baseline: Int,
    onSampleChanged: (index: Int, newValue: Int) -> Unit,
    modifier: Modifier = Modifier,
    handleColor: Color = Color.Blue.copy(alpha = 0.5f)
) {
    val scale = LocalPixelScale.current
    val density = LocalDensity.current
    
    // Minimum visual spacing between handles to avoid clutter.
    val minHandleSpacingPx = with(density) { 8.dp.toPx() }
    val handleRadiusPx = with(density) { 3.dp.toPx() }

    val stepX = scale.pxPerSample
    val stepY = scale.pxPerAdcCount
    
    // Subsampling: only draw handles if they are spaced enough.
    val stride = if (stepX < minHandleSpacingPx) {
        ceil(minHandleSpacingPx / stepX).toInt().coerceAtLeast(1)
    } else {
        1
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(samples, stride, stepX, stepY) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val x = change.position.x
                        val sampleIndex = (x / stepX).roundToInt().coerceIn(samples.indices)
                        
                        val currentAdc = samples[sampleIndex]
                        // y = baselineY - (sample - baseline) * stepY
                        // dy = -dsample * stepY => dsample = -dy / stepY
                        val deltaAdc = (-dragAmount.y / stepY).roundToInt()
                        
                        if (deltaAdc != 0) {
                            onSampleChanged(sampleIndex, currentAdc + deltaAdc)
                        }
                    }
                )
            }
    ) {
        val baselineY = size.height / 2f
        
        for (i in samples.indices step stride) {
            val sample = samples[i]
            val x = i * stepX
            val y = baselineY - (sample - baseline) * stepY
            
            drawCircle(
                color = handleColor,
                radius = handleRadiusPx,
                center = Offset(x, y)
            )
        }
    }
}
