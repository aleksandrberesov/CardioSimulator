package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

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
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
    )
}

@Preview(showBackground = true, widthDp = 600, heightDp = 300)
@Composable
fun ChartCanvasPreview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.8f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.8f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    CardioSimulatorTheme {
        ChartCanvas(
            points = samplePoints,
            modifier = Modifier.fillMaxSize().padding(16.dp)
        )
    }
}
