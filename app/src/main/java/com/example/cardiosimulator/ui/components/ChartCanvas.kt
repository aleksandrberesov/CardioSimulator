package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Shared projection from source units to screen pixels.
 */
fun projectDots(
    values: List<Float>,
    originX: Int,
    stepX: Float,
    stepY: Float,
    baselineY: Float
): List<Offset> = values.mapIndexed { i, v ->
    Offset((originX + i) * stepX, baselineY - v * stepY)
}

/**
 * Shared drawing routine for the waveform line.
 */
fun DrawScope.drawDots(dots: List<Offset>, color: Color) {
    if (dots.size < 2) return
    drawPoints(
        points = dots,
        pointMode = PointMode.Polygon,
        color = color,
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

/**
 * Renders [points] as a continuous line across the chart area.
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

                val dots = projectDots(dataPoints, originX = 0, stepX, stepY, baselineY)
                onDrawBehind {
                    drawDots(dots, color)
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
