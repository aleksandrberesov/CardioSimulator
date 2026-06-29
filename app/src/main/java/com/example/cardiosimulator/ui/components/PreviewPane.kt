package com.example.cardiosimulator.ui.components


import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.theme.EcgTraceTeal
import kotlinx.coroutines.isActive
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
    color: Color = EcgTraceTeal,
    isRunning: Boolean = true,
    externalXOffsetPx: Float? = null,
    gridScheme: GridScheme = GridScheme.Yellow,
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

    val phaseState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isRunning, durationMs, externalXOffsetPx) {
        if (isRunning && externalXOffsetPx == null) {
            var lastTime = 0L
            while (isActive) {
                withFrameNanos { frameTime ->
                    if (lastTime != 0L) {
                        val deltaMs = (frameTime - lastTime) / 1_000_000f
                        phaseState.floatValue = (phaseState.floatValue + deltaMs / durationMs) % 1f
                    }
                    lastTime = frameTime
                }
            }
        }
    }
    val phase = phaseState.floatValue

    Spacer(
        modifier = modifier
            .chartArea()
            .clipToBounds()
            .drawWithCache {
                val stepY = scale.pxPerAdcCount
                val baselineY = size.height / 2f

                val path = projectPath(points.values, stepX, stepY, baselineY)

                onDrawBehind {
                    val strokeWidth = 1.5.dp.toPx() / scale.zoom
                    if (gridScheme == GridScheme.Blank) {
                        // Sweep mode: waveform is stationary, revealed by the carrier moving across.
                        val dist = if (externalXOffsetPx != null) -externalXOffsetPx else (phase * periodPx)
                        val carrierX = dist % size.width
                        val gapWidth = 4.dp.toPx()
                        val gapEnd = carrierX + gapWidth

                        // Offset that anchors the continuous 'dist' to the moving 'carrierX'
                        // This ensures the signal at any X corresponds to the 'dist' when the 
                        // carrier was at that X.
                        val baseScroll = carrierX - dist
                        val iterations = (size.width / periodPx).toInt() + 2

                        if (gapEnd < size.width) {
                            // Normal case: Draw new data [0, carrierX] and old data [gapEnd, width]
                            clipRect(right = carrierX) {
                                val s = baseScroll % periodPx
                                for (i in 0..iterations) {
                                    withTransform({ translate(left = s + i * periodPx) }) {
                                        drawWaveform(path, color, strokeWidth)
                                    }
                                }
                            }
                            clipRect(left = gapEnd) {
                                val s = (baseScroll + size.width) % periodPx
                                for (i in 0..iterations) {
                                    withTransform({ translate(left = s + i * periodPx) }) {
                                        drawWaveform(path, color, strokeWidth)
                                    }
                                }
                            }
                        } else {
                            // Wrap-around case: The gap is currently at the start of the screen.
                            val wrappedGapEnd = gapEnd - size.width
                            clipRect(left = wrappedGapEnd, right = carrierX) {
                                val s = baseScroll % periodPx
                                for (i in 0..iterations) {
                                    withTransform({ translate(left = s + i * periodPx) }) {
                                        drawWaveform(path, color, strokeWidth)
                                    }
                                }
                            }
                        }

                        // Draw the carrier highlight (the leading edge/pen)
                        drawRect(
                            color = Color.White.copy(alpha = 0.4f),
                            topLeft = Offset(carrierX, 0f),
                            size = Size(2.dp.toPx(), size.height)
                        )
                    } else {
                        // Standard mode: scrolling waveform
                        val xOffset = externalXOffsetPx ?: (-phase * periodPx)
                        val scroll = xOffset % periodPx
                        val iterations = (size.width / periodPx).toInt() + 2

                        for (i in 0..iterations) {
                            withTransform({
                                translate(left = scroll + i * periodPx)
                            }) {
                                drawWaveform(path, color, strokeWidth)
                            }
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
        val v = when (i) {
            in 200..210 -> (i - 200) * 50f
            in 211..220 -> 500f - (i - 211) * 60f
            in 221..230 -> -100f + (i - 221) * 10f
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
