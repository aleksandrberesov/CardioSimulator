package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.components.CalibrationPulse
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.components.SampleHandleOverlay
import com.example.cardiosimulator.ui.components.SignificantPointOverlay

/**
 * A version of [Lead] that includes draggable handles for editing raw samples
 * and an overlay for marking significant ECG points.
 *
 * The waveform is rendered at its natural pixel width and centered horizontally
 * within the parent so it sits in the middle of the monitor area instead of
 * starting at the left edge. It follows the global [LocalPixelScale] for both
 * horizontal and vertical scaling to maintain consistency with the live monitor.
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
    val points = Points(stream.samples.map { (it - baseline).toFloat() })
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
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calibration Symbol (positioned close to graphic)
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
            ) {
                CalibrationPulse(modifier = Modifier.fillMaxSize())
            }

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
}
