package com.example.cardiosimulator.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
) {
    val density = LocalDensity.current
    val pxPerMm = density.density * (160f / 25.4f)
    val previewScale = remember(pxPerMm) {
        PixelScale(
            pxPerMm = pxPerMm,
            paperSpeedMmPerSec = 25f,
            gainZoomY = 1.0f,
            cal = EcgCalibration(),
        )
    }

    val pxPerSec = previewScale.pxPerSec
    val stepX = previewScale.pxPerSample
    val dataWidthPx = points.values.size * stepX
    
    // Calculate loop duration: at least 1 second (HR=60), or longer if data exceeds 1s.
    // This keeps the horizontal speed constant at 25mm/s.
    val durationMs = max(1000, (dataWidthPx / pxPerSec * 1000).toInt())
    val periodPx = durationMs / 1000f * pxPerSec

    CompositionLocalProvider(LocalPixelScale provides previewScale) {
        val infiniteTransition = rememberInfiniteTransition(label = "PreviewScroll")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ScrollPhase"
        )

        Spacer(
            modifier = modifier
                .drawWithCache {
                    val stepY = previewScale.pxPerAdcCount
                    val baselineY = size.height / 2f
                    
                    // Use a Path for smoother rendering and to avoid "sets of dots" artifacts
                    val path = Path()
                    if (points.values.isNotEmpty()) {
                        path.moveTo(0f, baselineY - points.values[0] * stepY)
                        for (i in 1 until points.values.size) {
                            path.lineTo(i * stepX, baselineY - points.values[i] * stepY)
                        }
                    }

                    onDrawBehind {
                        val xOffset = phase * periodPx
                        val iterations = (size.width / periodPx).toInt() + 2
                        
                        for (i in -1..iterations) {
                            withTransform({
                                translate(left = xOffset + (i - 1) * periodPx)
                            }) {
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
        )
    }
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
    
    CardioSimulatorTheme {
        PreviewPane(
            points = Points(samples),
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )
    }
}
