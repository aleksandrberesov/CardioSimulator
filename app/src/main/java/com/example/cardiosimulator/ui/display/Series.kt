package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.data.Points
import androidx.compose.ui.Modifier

@Composable
fun Series(
    points: Points,
    modifier: Modifier = Modifier,
    title: String = "",
){
    val dataPoints = Points(List(6) { points.values }.flatten())
    Row(modifier = Modifier.seriesArea()) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(title)
        }
        Box(
            modifier = Modifier.weight(10f),
            contentAlignment = Alignment.Center
        ) {
            ChartCanvas(points = dataPoints, modifier = modifier, scaleY = 1f)
        }
    }
}
