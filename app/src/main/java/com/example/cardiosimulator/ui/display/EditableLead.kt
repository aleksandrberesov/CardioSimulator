package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.components.SampleHandleOverlay
import com.example.cardiosimulator.ui.components.SignificantPointOverlay

/**
 * A version of [Lead] that includes draggable handles for editing raw samples
 * and an overlay for marking significant ECG points.
 *
 * The waveform is rendered at its natural pixel width and centered horizontally
 * within the parent so it sits in the middle of the monitor area instead of
 * starting at the left edge.
 */
@Composable
fun EditableLead(
    stream: LeadStream,
    significantPoints: List<SignificantPoint>,
    baseline: Int,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onIndexSelected: ((Int) -> Unit)? = null,
    isEditable: Boolean = true
) {
    // Convert raw ADC to baseline-zeroed floats for the unified renderer.
    // Valid ADC range is 0..2048; samples above 1463 (or below 0) are dropped
    // as NaN so the waveform breaks across them instead of being drawn.
    val points = Points(
        stream.samples.map { adc ->
            if (adc !in 0..1463) Float.NaN else (adc - baseline).toFloat()
        }
    )
    val scale = LocalPixelScale.current
    val density = LocalDensity.current

    val waveformWidthDp = with(density) {
        (stream.samples.size * scale.pxPerSample).toDp()
    }

    Box(
        modifier = modifier
            .leadArea()
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        // Trace + overlays sized to the natural waveform width so the
        // centering container can place them in the middle of the monitor.
        Box(
            modifier = Modifier
                .width(waveformWidthDp)
                .fillMaxHeight()
        ) {
            ChartCanvas(points = points, modifier = Modifier.fillMaxSize())

            SignificantPointOverlay(
                points = points,
                significantPoints = significantPoints,
                modifier = Modifier.fillMaxSize()
            )

            SampleHandleOverlay(
                samples = stream.samples,
                baseline = baseline,
                selectedIndex = selectedIndex,
                onIndexSelected = onIndexSelected,
                isEditable = isEditable,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
