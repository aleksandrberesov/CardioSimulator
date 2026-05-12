package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.components.CalibrationPulse
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

/**
 * Per-part calibration overrides passed to the renderer. When provided,
 * `pxPerSample` is derived from [sampleRateHz] and `pxPerAdcCount` from
 * [samplesPerMv] so records with non-default `max`/`value`/`duration`
 * render at their own gain and speed — see Phase 0a in the plan.
 */
data class PartCalibration(
    val sampleRateHz: Float = 0f,
    val samplesPerMv: Float = 0f,
)

@Composable
fun EditableLead(
    points: Points,
    onPointsChange: (Points) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    calibration: PartCalibration = PartCalibration(),
) {
    EditableLead(
        partNames = listOf(title),
        partPoints = listOf(points),
        partCalibration = listOf(calibration),
        onPartPointsChange = { _, newPoints -> onPointsChange(newPoints) },
        modifier = modifier,
        title = title,
        selectedPartIndex = if (selected) 0 else null,
        onPartClick = { onClick?.invoke() }
    )
}

@Composable
fun EditableLead(
    partNames: List<String>,
    partPoints: List<Points>,
    onPartPointsChange: (Int, Points) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    selectedPartIndex: Int? = null,
    onPartClick: ((Int) -> Unit)? = null,
    partCalibration: List<PartCalibration> = emptyList(),
) {
    val scale = LocalPixelScale.current
    val density = LocalDensity.current

    Row(
        modifier = Modifier.leadArea(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Calibration Symbol and Lead Name
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
        ) {
            // Drive the calibration symbol from the first part's per-record
            // gain so the 1 mV box matches the rendered waveform when the
            // record uses non-default `max`/`value`.
            val firstSpm = partCalibration.firstOrNull()?.samplesPerMv ?: 0f
            CalibrationPulse(
                modifier = Modifier.fillMaxSize(),
                samplesPerMv = firstSpm,
            )

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 45.dp, start = 8.dp)
            )
        }

        // Trace
        Box(
            modifier = modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            var currentSampleOffset = 0
            partPoints.forEachIndexed { index, part ->
                val cal = partCalibration.getOrNull(index) ?: PartCalibration()
                val effPxPerSample = if (cal.sampleRateHz > 0f)
                    scale.pxPerSampleFor(cal.sampleRateHz) else scale.pxPerSample
                val xOffset = with(density) { (currentSampleOffset * effPxPerSample).toDp() }
                val partWidth = with(density) { (part.values.size * effPxPerSample).toDp() }
                val isSelected = index == selectedPartIndex

                Box(
                    modifier = Modifier
                        .absoluteOffset(x = xOffset)
                        .width(partWidth)
                        .fillMaxHeight()
                        .background(if (isSelected) Color.Blue.copy(alpha = 0.1f) else Color.Transparent)
                        .run {
                            if (isSelected) border(1.dp, Color.Blue) else this
                        }
                        .clickable { onPartClick?.invoke(index) }
                ) {
                    ChartCanvas(
                        points = part,
                        modifier = Modifier.fillMaxSize(),
                        sampleRateHz = cal.sampleRateHz,
                        samplesPerMv = cal.samplesPerMv,
                    )
                }
                currentSampleOffset += part.values.size
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 600, heightDp = 150)
@Composable
fun EditableLeadPreview() {
    val sampleNames = listOf("P-Wave", "QRS-Complex")
    val samplePoints = listOf(
        Points(listOf(0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f)),
        Points(listOf(-0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f))
    )
    var selectedIndex by remember { mutableStateOf<Int?>(0) }
    val previewScale = PixelScale(
        pxPerMm = 6.3f,
        paperSpeedMmPerSec = 25f,
        gainZoomY = 1f,
        cal = EcgCalibration(),
    )
    CardioSimulatorTheme {
        CompositionLocalProvider(LocalPixelScale provides previewScale) {
            Box(modifier = Modifier.ekgGrid()) {
                EditableLead(
                    partNames = sampleNames,
                    partPoints = samplePoints,
                    onPartPointsChange = { _, _ -> },
                    selectedPartIndex = selectedIndex,
                    onPartClick = { selectedIndex = it },
                    title = "I",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
