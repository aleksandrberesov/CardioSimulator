package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.example.cardiosimulator.data.Points

@Composable
fun ChartCanvas(
    points: Points,
    modifier: Modifier = Modifier
) {
    val dataPoints = points.values

    Canvas(modifier = modifier.chartArea()
    ) {
        if (dataPoints.isEmpty()) return@Canvas

        val maxVal = dataPoints.maxOrNull() ?: 1f
        val barWidth = size.width / dataPoints.size

        dataPoints.forEachIndexed { index, value ->
            val barHeight = (value / maxVal) * size.height
            drawRect(
                color = Color.Blue,
                topLeft = Offset(index * barWidth, size.height - barHeight),
                size = Size(barWidth * 0.8f, barHeight)
            )
        }
    }
}
