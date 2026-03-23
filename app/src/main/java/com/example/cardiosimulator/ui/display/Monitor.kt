package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cardiosimulator.data.Points


@Composable
fun Monitor(
    points: Points,
    modifier: Modifier = Modifier,
    count: Int = 1
){
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(count) { index ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Series(
                    points = points,
                    modifier = modifier,
                    index = index + 1
                )
            }
        }
    }
}
