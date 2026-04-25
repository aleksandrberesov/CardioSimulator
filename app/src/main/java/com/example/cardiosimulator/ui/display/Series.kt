package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.components.ChartCanvas
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

@Composable
fun Series(
    points: Points,
    modifier: Modifier = Modifier,
    title: String = "",
){
    Row(modifier = Modifier.seriesArea()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 8.dp, start = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.Black
            )
        }
        Box(
            modifier = Modifier
                .weight(15f),
            contentAlignment = Alignment.Center
        ) {
            ChartCanvas(points = points, modifier = modifier, scaleY = 1f)
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
