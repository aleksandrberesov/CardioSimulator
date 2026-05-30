package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.cardiosimulator.data.LocalPixelScale
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * An overlay that handles freehand tracing gestures.
 * In Trace mode, it captures drag events and converts them into ADC sample updates.
 */
@Composable
fun TraceOverlay(
    sampleCount: Int,
    baseline: Int,
    onStrokeStart: () -> Unit,
    onTrace: (Map<Int, Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalPixelScale.current
    val stepX = scale.pxPerSample
    val stepY = scale.pxPerAdcCount

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(sampleCount, stepX, stepY) {
                detectDragGestures(
                    onDragStart = { onStrokeStart() },
                    onDrag = { change, dragAmount ->
                        val currentX = change.position.x
                        val currentY = change.position.y
                        val prevX = currentX - dragAmount.x
                        val prevY = currentY - dragAmount.y

                        val baselineY = size.height / 2f

                        val updates = mutableMapOf<Int, Int>()
                        
                        val startIdx = (min(prevX, currentX) / stepX).roundToInt().coerceIn(0, sampleCount - 1)
                        val endIdx = (max(prevX, currentX) / stepX).roundToInt().coerceIn(0, sampleCount - 1)

                        if (startIdx == endIdx) {
                            val value = (baseline + (baselineY - currentY) / stepY).roundToInt()
                            updates[startIdx] = value
                        } else {
                            // Linear interpolation for skipped columns to ensure a continuous trace
                            for (i in startIdx..endIdx) {
                                val fraction = if (endIdx == startIdx) 0f else (i.toFloat() - (prevX / stepX)) / (currentX / stepX - prevX / stepX)
                                val interpolatedY = prevY + fraction * (currentY - prevY)
                                val value = (baseline + (baselineY - interpolatedY) / stepY).roundToInt()
                                updates[i] = value
                            }
                        }
                        
                        if (updates.isNotEmpty()) {
                            onTrace(updates)
                        }
                    }
                )
            }
    ) {
        // This overlay is just for input capture.
        // The resulting trace is rendered by the underlying ChartCanvas.
    }
}
