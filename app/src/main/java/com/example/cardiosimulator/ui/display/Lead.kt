package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.ui.components.CalibrationPulse
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.theme.TextPrimary

@Composable
fun Lead(
    points: Points,
    modifier: Modifier = Modifier,
    title: String = "",
    isRunning: Boolean = false,
    xOffsetPx: Float = 0f,
    gridScheme: GridScheme = GridScheme.Pink,
    isCompareMode: Boolean = false,
    significantPoints: List<com.example.cardiosimulator.domain.SignificantPoint> = emptyList(),
    showImpulseLabels: Boolean = false,
    filterType: com.example.cardiosimulator.domain.EcgFilterType = com.example.cardiosimulator.domain.EcgFilterType.NONE,
    calibration: com.example.cardiosimulator.data.EcgCalibration = com.example.cardiosimulator.data.EcgCalibration()
){
    val processedPoints = androidx.compose.runtime.remember(points, filterType) {
        if (filterType == com.example.cardiosimulator.domain.EcgFilterType.NONE || points.values.size < 50) {
            points
        } else {
            try {
                val signal = points.values.map { it.toDouble() }.toDoubleArray()
                val samplingRate = calibration.sampleRateHz.toDouble()
                val filtered = when (filterType) {
                    com.example.cardiosimulator.domain.EcgFilterType.LOWPASS ->
                        com.example.cardiosimulator.signals.biosppy.Filter.filterSignal(signal, "butter", "lowpass", 4, doubleArrayOf(25.0), samplingRate)
                    com.example.cardiosimulator.domain.EcgFilterType.HIGHPASS ->
                        com.example.cardiosimulator.signals.biosppy.Filter.filterSignal(signal, "butter", "highpass", 4, doubleArrayOf(3.0), samplingRate)
                    com.example.cardiosimulator.domain.EcgFilterType.BANDPASS ->
                        com.example.cardiosimulator.signals.biosppy.Filter.filterSignal(signal, "butter", "bandpass", 4, doubleArrayOf(3.0, 25.0), samplingRate)
                    else -> signal
                }
                Points(filtered.map { it.toFloat() })
            } catch (e: Exception) {
                points
            }
        }
    }

    Row(
        modifier = modifier.leadArea(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label Strip
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (!isCompareMode && title.isNotEmpty()) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    color = com.example.cardiosimulator.ui.theme.EcgTraceTeal,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Calibration Pulse
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
        ) {
            CalibrationPulse(modifier = Modifier.fillMaxSize())
        }

        // Trace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            PreviewPane(
                points = processedPoints,
                modifier = Modifier.fillMaxSize(),
                isRunning = isRunning,
                externalXOffsetPx = xOffsetPx,
                gridScheme = gridScheme
            )

            if (showImpulseLabels && significantPoints.isNotEmpty()) {
                com.example.cardiosimulator.ui.components.SignificantPointOverlay(
                    points = processedPoints,
                    significantPoints = significantPoints,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isCompareMode && title.isNotEmpty()) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.sp,
                    color = com.example.cardiosimulator.ui.theme.EcgTraceTeal,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 2.dp)
                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 600, heightDp = 150)
@Composable
fun LeadPreview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    val previewScale = PixelScale(
        pxPerMm = 6.3f,
        paperSpeedMmPerSec = 25f,
        gainZoomY = 1f,
        cal = EcgCalibration(),
    )
    CardioSimulatorTheme {
        CompositionLocalProvider(LocalPixelScale provides previewScale) {
            Box(modifier = Modifier.ekgGrid()) {
                Lead(
                    points = samplePoints,
                    title = "I",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
