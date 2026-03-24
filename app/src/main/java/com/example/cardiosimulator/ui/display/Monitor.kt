package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.panels.SeriesControlPanel


@Composable
fun Monitor(
    points: Points,
    modifier: Modifier = Modifier,
    count: Int = 1
){
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().weight(10f)) {
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
