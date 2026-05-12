package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.LocalPixelScale

/**
 * A standard ECG calibration pulse: 1 mV tall, 200 ms wide.
 */
@Composable
fun CalibrationPulse(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    strokeWidthDp: Float = 1.5f,
    /**
     * If > 0, draws the pulse 1-mV-tall using the part's own samples-per-mV
     * mapping (`AMax/AValue`) instead of the global gain. Keeps the
     * calibration symbol consistent with the rendered waveform when the
     * record uses non-default `max`/`value`.
     */
    samplesPerMv: Float = 0f,
) {
    val scale = LocalPixelScale.current
    Canvas(modifier = modifier) {
        val baseline = size.height / 2f

        // Standard ECG calibration pulse: 1 mV tall, 200 ms wide.
        val pulseHeight = if (samplesPerMv > 0f) scale.pxPerSourceUnitFor(samplesPerMv) * samplesPerMv
                          else 1f * scale.pxPerMv
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

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidthDp.dp.toPx())
        )
    }
}
