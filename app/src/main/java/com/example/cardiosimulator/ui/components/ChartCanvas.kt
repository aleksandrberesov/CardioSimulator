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
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ChartCanvas(
    points: Points,
    modifier: Modifier = Modifier,
) {
    val dataPoints = points.values
    if (dataPoints.size < 2) return
    val scale = LocalPixelScale.current

    Spacer(
        modifier = modifier
            .chartArea()
            .drawWithCache {
                val stepX = scale.pxPerSample
                val stepY = scale.pxPerAdcCount
                val baselineY = size.height / 2f

                val path = Path().apply {
                    for (i in dataPoints.indices) {
                        val x = i * stepX
                        val y = baselineY - (dataPoints[i] * stepY)
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
    val previewScale = PixelScale(
        pxPerMm = 6.3f,
        paperSpeedMmPerSec = 25f,
        gainZoomY = 1f,
        cal = EcgCalibration(),
    )
    CardioSimulatorTheme {
        CompositionLocalProvider(LocalPixelScale provides previewScale) {
            ChartCanvas(
                points = samplePoints,
                modifier = Modifier.fillMaxSize().padding(16.dp)
            )
        }
    }
}
