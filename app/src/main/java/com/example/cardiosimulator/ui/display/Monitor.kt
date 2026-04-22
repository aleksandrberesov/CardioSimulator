package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.panels.SeriesControlPanel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme


@Composable
fun Monitor(
    points: Points,
    modifier: Modifier = Modifier,
    count: Int = 1,
    gridScheme: GridScheme = GridScheme.Pink
){
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(10f)
                .ekgGrid(gridScheme)
        ) {
            repeat(count) { index ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Series(
                        points = points,
                        modifier = modifier,
                        title = (index + 1).toString()
                    )
                }
            }
        }
        Box(
            modifier = Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            SeriesControlPanel()
        }
    }
}

@Preview(name = "Pink Scheme", showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MonitorPinkPreview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    CardioSimulatorTheme {
        Monitor(
            points = samplePoints,
            count = 3,
            gridScheme = GridScheme.Pink
        )
    }
}

@Preview(name = "Blue/Gray Scheme", showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MonitorBluePreview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    CardioSimulatorTheme {
        Monitor(
            points = samplePoints,
            count = 3,
            gridScheme = GridScheme.BlueGray
        )
    }
}
