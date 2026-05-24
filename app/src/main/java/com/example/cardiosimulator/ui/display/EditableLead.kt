package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.components.SampleHandleOverlay

/**
 * A version of [Lead] that includes draggable handles for editing raw samples.
 */
@Composable
fun EditableLead(
    stream: LeadStream,
    baseline: Int,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onIndexSelected: ((Int) -> Unit)? = null
) {
    // Convert raw ADC to baseline-zeroed floats for the unified renderer.
    val points = Points(stream.samples.map { (it - baseline).toFloat() })

    Row(
        modifier = modifier.leadArea(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Trace + Handle Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            ChartCanvas(points = points, modifier = Modifier.fillMaxSize())
            
            SampleHandleOverlay(
                samples = stream.samples,
                baseline = baseline,
                selectedIndex = selectedIndex,
                onIndexSelected = onIndexSelected,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
