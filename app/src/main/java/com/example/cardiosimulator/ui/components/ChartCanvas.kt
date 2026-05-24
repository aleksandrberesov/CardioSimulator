package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

/**
 * Shared projection from source units to screen pixels.
 * Builds a [Path] for efficient rendering.
 */
fun projectPath(
    values: List<Float>,
    stepX: Float,
    stepY: Float,
    baselineY: Float
): Path {
    val path = Path()
    if (values.isEmpty()) return path
    path.moveTo(0f, baselineY - values[0] * stepY)
    for (i in 1 until values.size) {
        path.lineTo(i * stepX, baselineY - values[i] * stepY)
    }
    return path
}

/**
 * Shared drawing routine for the waveform line using a cached Path.
 */
fun DrawScope.drawWaveform(path: Path, color: Color) {
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    )
}

/**
 * Renders [points] as a continuous line across the chart area.
 * Internally reads [LocalPixelScale.current] for projection.
 */
@Composable
fun ChartCanvas(
    points: Points,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
) {
    val dataPoints = points.values
    if (dataPoints.size < 2) return
    val scale = LocalPixelScale.current

    Spacer(
        modifier = modifier
            .chartArea()
            .clipToBounds()
            .drawWithCache {
                val stepX = scale.pxPerSample
                val stepY = scale.pxPerAdcCount
                val baselineY = size.height / 2f

                val path = projectPath(dataPoints, stepX, stepY, baselineY)
                
                onDrawBehind {
                    drawWaveform(path, color)
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
