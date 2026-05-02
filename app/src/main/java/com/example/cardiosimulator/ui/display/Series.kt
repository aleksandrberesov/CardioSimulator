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
import com.example.cardiosimulator.data.AdcScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

@Composable
fun Series(
    points: Points,
    modifier: Modifier = Modifier,
    title: String = "",
    scale: AdcScale = AdcScale()
){
    Row(
        modifier = Modifier.seriesArea(),
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
                
                // 1mV is standardly 200 ADC units in our system
                // 200ms is standardly 50 samples
                val pulseHeight = 200f * scale.verticalPixelsPerUnit
                val pulseWidth = 50f * scale.horizontalPixelsPerSample
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
                    .padding(top = 45.dp, start = 8.dp) // Positioned under the pulse
            )
        }

        // Trace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ChartCanvas(points = points, modifier = modifier, scale = scale)
        }
    }
}

@Preview(showBackground = true, widthDp = 600, heightDp = 150)
@Composable
fun SeriesPreview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    CardioSimulatorTheme {
        Box(modifier = Modifier.ekgGrid()) {
            Series(
                points = samplePoints,
                title = "I",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
