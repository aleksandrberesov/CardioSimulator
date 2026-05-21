package com.example.cardiosimulator.ui.display

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.ui.components.CalibrationPulse
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.components.SampleHandleOverlay

/**
 * A version of [Lead] that includes draggable handles for editing raw samples.
 */
@Composable
fun EditableLead(
    stream: LeadStream,
    baseline: Int,
    onSampleChanged: (index: Int, newValue: Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stream.lead.name
) {
    // Convert raw ADC to baseline-zeroed floats for the unified renderer.
    val points = Points(stream.samples.map { (it - baseline).toFloat() })

    Row(
        modifier = modifier.leadArea(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Calibration Symbol and Lead Name (identical to Lead.kt)
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
        ) {
            CalibrationPulse(modifier = Modifier.fillMaxSize())

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

        // Trace + Handle Overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            ChartCanvas(points = points, modifier = Modifier.fillMaxSize())
            
            SampleHandleOverlay(
                samples = stream.samples,
                baseline = baseline,
                onSampleChanged = onSampleChanged,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
