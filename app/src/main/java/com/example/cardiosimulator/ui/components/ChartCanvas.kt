package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.cardiosimulator.data.Points
import kotlin.io.path.moveTo

@Composable
fun ChartCanvas(
    points: Points,
    modifier: Modifier = Modifier,
    scaleY: Float = 1f
) {
    val dataPoints = points.values
    if (dataPoints.size < 2) return

    Spacer(
        modifier = modifier
            .chartArea()
            .drawWithCache {
                val minVal = dataPoints.minOrNull() ?: 0f
                val maxVal = dataPoints.maxOrNull() ?: 1f
                val range = (maxVal - minVal).coerceAtLeast(1f)
                val stepX = size.width / (dataPoints.size - 1).coerceAtLeast(1)

                val path = Path().apply {
                    for (i in dataPoints.indices) {
                        val x = i * stepX
                        val y = size.height - ((dataPoints[i] - minVal) / range) * size.height * scaleY
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                onDrawBehind {
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(
                            width = 3f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
    )
}