package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

@Composable
fun Lead(
    points: Points,
    modifier: Modifier = Modifier,
    title: String = "",
){
    val scale = LocalPixelScale.current
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
            Canvas(modifier = Modifier.fillMaxSize()) {
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

                drawPath(
                    path = path,
                    color = Color.Black,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

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
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ChartCanvas(points = points, modifier = modifier)
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
