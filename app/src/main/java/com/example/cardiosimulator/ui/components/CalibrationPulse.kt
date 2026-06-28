package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.LocalPixelScale

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache

import com.example.cardiosimulator.ui.theme.EcgTraceTeal

/**
 * A standard ECG calibration pulse: 1 mV tall, 200 ms wide.
 */
@Composable
fun CalibrationPulse(
    modifier: Modifier = Modifier,
    color: Color = EcgTraceTeal,
    strokeWidthDp: Float = 1.5f,
) {
    val scale = LocalPixelScale.current
    Spacer(modifier = modifier.drawWithCache {
        val baseline = size.height / 2f

        // Standard ECG calibration pulse: 1 mV tall, 200 ms wide.
        val pulseHeight = 1f * scale.pxPerMv
        val pulseWidth = 0.2f * scale.pxPerSec
        val startX = 8.dp.toPx()
        val wingWidth = 4.dp.toPx()

        val path = Path().apply {
            moveTo(startX, baseline)
            lineTo(startX + wingWidth, baseline)
            lineTo(startX + wingWidth, baseline - pulseHeight)
            lineTo(startX + wingWidth + pulseWidth, baseline - pulseHeight)
            lineTo(startX + wingWidth + pulseWidth, baseline)
            lineTo(startX + wingWidth + pulseWidth + wingWidth, baseline)
        }

        onDrawBehind {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidthDp.dp.toPx() / scale.zoom)
            )
        }
    })
}
