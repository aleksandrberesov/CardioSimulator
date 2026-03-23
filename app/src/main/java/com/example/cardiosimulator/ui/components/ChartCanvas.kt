package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.cardiosimulator.data.Points

@Composable
fun ChartCanvas(
    points: Points,
    modifier: Modifier = Modifier,
    scaleY: Float = 1f
) {
    val dataPoints = points.values

    Canvas(modifier = modifier.chartArea()
    ) {
        if (dataPoints.size < 2) return@Canvas

        val minVal = dataPoints.minOrNull() ?: 0f
        val maxVal = dataPoints.maxOrNull() ?: 1f
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val stepX = size.width / (dataPoints.size - 1).coerceAtLeast(1)

        for (i in 0 until dataPoints.size - 1) {
            val startX = i * stepX
            val endX = (i + 1) * stepX

            val startY = size.height - ((dataPoints[i] - minVal) / range) * size.height * scaleY
            val endY = size.height - ((dataPoints[i + 1] - minVal) / range) * size.height * scaleY

            drawLine(
                color = Color.Black,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 3f
            )
        }
    }
}
