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

/**
 * Renders a polyline of [points] across the chart area.
 *
 * Two scaling overrides exist for Phase 0a per-part calibration:
 * - [sampleRateHz] — when > 0, derives `pxPerSample` from this rate instead
 *   of the global default. Used so records sampled at e.g. 250 Hz render at
 *   the right speed.
 * - [samplesPerMv] — when > 0, scales sample values to pixels via this
 *   factor (`AMax/AValue`). Used so records exported with non-default
 *   `max`/`value` render at the right gain.
 *
 * When both are omitted the renderer falls back to the legacy global
 * calibration (still used by [com.example.cardiosimulator.ui.display.Lead]
 * and asset fixtures).
 */
@Composable
fun ChartCanvas(
    points: Points,
    modifier: Modifier = Modifier,
    sampleRateHz: Float = 0f,
    samplesPerMv: Float = 0f,
    color: Color = Color.Black,
) {
    val dataPoints = points.values
    if (dataPoints.size < 2) return
    val scale = LocalPixelScale.current

    Spacer(
        modifier = modifier
            .chartArea()
            .drawWithCache {
                val stepX = if (sampleRateHz > 0f) scale.pxPerSampleFor(sampleRateHz)
                            else scale.pxPerSample
                val stepY = if (samplesPerMv > 0f) scale.pxPerAdcCountFor(samplesPerMv)
                            else scale.pxPerAdcCount
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
                        color = color,
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
