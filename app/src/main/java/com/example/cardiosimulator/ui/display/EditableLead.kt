package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

private const val EDITOR_ADC_RANGE = 512f

// Vertical breathing room reserved at each edge so the selection handle circle
// (radius 5dp + 1dp stroke in SampleHandleOverlay) stays fully visible when a
// sample sits at the edge of the visible range, instead of being clipped in half.
private val EDITOR_VERTICAL_MARGIN = 6.dp

/**
 * A version of [Lead] that includes draggable handles for editing raw samples
 * and an overlay for marking significant ECG points.
 *
 * The waveform is rendered at its natural pixel width and centered horizontally
 * within the parent so it sits in the middle of the monitor area instead of
 * starting at the left edge. Unlike the live monitor, the editor uses a
 * fit-to-view vertical scale that focuses on a typical ECG range (~3 mV or 800 ADC
 * counts) to ensure comfortable editing, regardless of the medical calibration.
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

    BoxWithConstraints(
        modifier = modifier
            .leadArea()
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        // Rescale gainZoomY so the full 0..2048 ADC range fits in the lead
        // height minus a margin at each edge. baselineY stays at height/2 and
        // 1024 is the midpoint, so only the 0/2048 extremes pull inward by the
        // margin, keeping the selection handle from being clipped at the edge.
        val heightPx = with(density) { maxHeight.toPx() }
        val marginPx = with(density) { EDITOR_VERTICAL_MARGIN.toPx() }
        val drawableHeight = (heightPx - 2f * marginPx).coerceAtLeast(0f)
        val fitScale = if (drawableHeight > 0f && scale.pxPerAdcCount > 0f) {
            val targetPxPerAdcCount = drawableHeight / EDITOR_ADC_RANGE
            scale.copy(gainZoomY = scale.gainZoomY * targetPxPerAdcCount / scale.pxPerAdcCount)
        } else {
            scale
        }

        CompositionLocalProvider(LocalPixelScale provides fitScale) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calibration Symbol (moved close to graphic)
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
}
