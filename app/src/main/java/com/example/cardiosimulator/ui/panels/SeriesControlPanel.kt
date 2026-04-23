package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.components.Label
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.utils.padWithFiveSpaces

import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun SeriesControlPanel(
    viewModel: MonitorViewModel,
    modifier: Modifier = Modifier,
    onRulerClick: () -> Unit = {},
    onPauseClick: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tab(
                text = "4x",
                onClick = { 
                    viewModel.setSeriesCount(4)
                    viewModel.setSeriesScheme(SeriesScheme.OneColumn)
                }
            )
            Tab(
                text = "12x",
                onClick = { 
                    viewModel.setSeriesCount(12)
                    viewModel.setSeriesScheme(SeriesScheme.Grid)
                }
            )
            Tab(
                text = "Compare",
                onClick = { viewModel.setSeriesScheme(SeriesScheme.TwoColumn) }
            )
            Tab(
                text = "25",
                subText = "mm/s",
                onClick = { /* TODO: Speed control */ }
            )
            Tab(
                text = "50",
                subText = "mm/s",
                onClick = { /* TODO: Speed control */ }
            )
            Tab(
                text = "Electrodes",
                onClick = { viewModel.toggleGridScheme() }
            )
            Tab(
                icon = Icons.Default.Straighten,
                iconModifier = Modifier.rotate(-45f),
                onClick = onRulerClick
            )
        }
        
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Label(
                text = "EOS",
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.Red,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 20.sp,
                borderWidth = 1.dp,
                borderColor = androidx.compose.ui.graphics.Color.Black,
                cornerRadius = 4.dp
            )
            Tab(
                icon = Icons.Default.Pause,
                onClick = onPauseClick
            )
            Label(
                text = "HR 160",
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                backgroundColor = androidx.compose.ui.graphics.Color.Black,
                fontSize = 20.sp,
                cornerRadius = 4.dp
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Tab(
                    text = "EMD/EBPA",
                    onClick = { }
                )
                Tab(
                    text = "Muscle",
                    onClick = { }
                )
                Tab(
                    icon = Icons.Default.Hub,
                    onClick = { viewModel.toggleGridScheme() },
                    borderWidth = 0.dp
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 100)
@Composable
fun SeriesControlPanelPreview() {
    CardioSimulatorTheme {
        SeriesControlPanel(viewModel = viewModel())
    }
}
