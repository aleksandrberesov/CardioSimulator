package com.example.cardiosimulator.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import kotlin.math.max

/**
 * HR=60 looping preview pane. Renders [points] as a continuous line that scrolls
 * left-to-right at one beat per second. Used by the editor footer to show
 * how the current segment/series sounds at a standard rate.
 */
@Composable
fun PreviewPane(
    points: Points,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    isRunning: Boolean = true,
) {
    if (points.values.size < 2) return
    val scale = LocalPixelScale.current

    val pxPerSec = scale.pxPerSec
    val stepX = scale.pxPerSample
    val dataWidthPx = points.values.size * stepX

    // Calculate loop duration: at least 1 second (HR=60), or longer if data exceeds 1s.
    // This keeps the horizontal speed constant at the speed defined in scale.
    val durationMs = max(1000, (dataWidthPx / pxPerSec * 1000).toInt())
    val periodPx = durationMs / 1000f * pxPerSec

    val infiniteTransition = rememberInfiniteTransition(label = "PreviewScroll")
    val phase by if (isRunning) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ScrollPhase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Spacer(
        modifier = modifier
            .chartArea()
            .clipToBounds()
            .drawWithCache {
                val stepY = scale.pxPerAdcCount
                val baselineY = size.height / 2f

                val path = projectPath(points.values, stepX, stepY, baselineY)

                onDrawBehind {
                    val xOffset = -phase * periodPx
                    val iterations = (size.width / periodPx).toInt() + 2

                    for (i in 0..iterations) {
                        withTransform({
                            translate(left = xOffset + i * periodPx)
                        }) {
                            drawWaveform(path, color)
                        }
                    }
                }
            }
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 100)
@Composable
fun PreviewPanePreview() {
    val samples = mutableListOf<Float>()
    for (i in 0 until 500) {
        val v = when {
            i in 200..210 -> (i - 200) * 50f
            i in 211..220 -> 500f - (i - 211) * 60f
            i in 221..230 -> -100f + (i - 221) * 10f
            else -> 0f
        }
        samples.add(v)
    }
    
    val previewScale = PixelScale(
        pxPerMm = 6.3f,
        paperSpeedMmPerSec = 25f,
        gainZoomY = 1f,
        cal = EcgCalibration(),
    )
    CardioSimulatorTheme {
        CompositionLocalProvider(LocalPixelScale provides previewScale) {
            PreviewPane(
                points = Points(samples),
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )
        }
    }
}
