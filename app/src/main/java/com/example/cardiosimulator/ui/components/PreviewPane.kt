package com.example.cardiosimulator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.Points

/**
 * HR=60 looping preview pane. Renders [points] as a polyline that scrolls
 * left-to-right at one beat per second. Used by the editor footer to show
 * how the current segment/series sounds at a standard rate. Mirrors RP5's
 * `RecalcTumbPoints` / `RecalcTumbBlocks` sensor-window logic.
 */
@Composable
fun PreviewPane(
    points: Points,
    sampleRateHz: Float,
    samplesPerMv: Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF388E3C),
) {
    val scale = LocalPixelScale.current
    val transition = rememberInfiniteTransition(label = "preview-loop")
    // 1-second period mirrors a 60 bpm cadence.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Box(
        modifier = modifier
            .height(80.dp)
            .background(Color(0xFFF7F7F7))
            .clipToBounds()
            .drawWithCache {
                val stepX = if (sampleRateHz > 0f) scale.pxPerSampleFor(sampleRateHz) else scale.pxPerSample
                val stepY = if (samplesPerMv > 0f) scale.pxPerSourceUnitFor(samplesPerMv) else scale.pxPerAdcCount
                val baselineY = size.height / 2f
                val total = points.values.size
                if (total < 2) {
                    return@drawWithCache onDrawBehind { /* nothing */ }
                }
                // Build a path that's shifted left by phase * total samples
                val offset = (phase * total).toInt()
                val path = Path().apply {
                    for (i in 0 until total) {
                        val srcIdx = (i + offset) % total
                        val x = i * stepX
                        val y = baselineY - (points.values[srcIdx] * stepY)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                onDrawBehind {
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
    ) {
        Text(
            text = "Preview @ 60 bpm",
            style = MaterialTheme.typography.labelSmall,
            color = Color.DarkGray,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        )
    }
}

/**
 * Small paper-grid legend overlay. Drawn in the top-left corner of the
 * editor canvas so the user can see the current paper scale.
 */
@Composable
fun PaperGridLegend(
    paperSpeedMmPerSec: Float,
    gainMmPerMv: Float,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "$gainMmPerMv mm/mV · $paperSpeedMmPerSec mm/s",
        style = MaterialTheme.typography.labelSmall,
        color = Color.DarkGray,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
