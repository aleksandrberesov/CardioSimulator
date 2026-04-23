package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.MonitorModeModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.panels.SeriesControlPanel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import kotlin.math.ceil


@Composable
fun Monitor(
    points: Points,
    modifier: Modifier = Modifier,
    monitorViewModel: MonitorViewModel = viewModel()
){
    val mode by monitorViewModel.monitorMode.collectAsState()

    val columns = when (mode.seriesScheme) {
        SeriesScheme.OneColumn -> 1
        SeriesScheme.TwoColumn -> 2
        SeriesScheme.Grid -> 4
    }

    val rows = ceil(mode.count.toFloat() / columns).toInt()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(10f)
                .ekgGrid(mode.gridScheme)
        ) {
            repeat(rows) { rowIndex ->
                Row(modifier = Modifier.weight(1f)) {
                    repeat(columns) { colIndex ->
                        val itemIndex = rowIndex * columns + colIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (itemIndex < mode.count) {
                                Series(
                                    points = points,
                                    modifier = modifier,
                                    title = (itemIndex + 1).toString()
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            SeriesControlPanel(viewModel = monitorViewModel)
        }
    }
}

@Preview(name = "1 Column - 12 Leads", showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MonitorOneColumn12Preview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    CardioSimulatorTheme {
        val vm = MonitorViewModel()
        vm.setSeriesCount(12)
        vm.setSeriesScheme(SeriesScheme.OneColumn)
        vm.setGridScheme(GridScheme.Pink)
        Monitor(
            points = samplePoints,
            monitorViewModel = vm
        )
    }
}

@Preview(name = "2 Columns - 12 Leads", showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MonitorTwoColumn12Preview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    CardioSimulatorTheme {
        val vm = MonitorViewModel()
        vm.setSeriesCount(12)
        vm.setSeriesScheme(SeriesScheme.TwoColumn)
        vm.setGridScheme(GridScheme.BlueGray)
        Monitor(
            points = samplePoints,
            monitorViewModel = vm
        )
    }
}

@Preview(name = "Grid (4 Col) - 12 Leads", showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MonitorGrid12Preview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    CardioSimulatorTheme {
        val vm = MonitorViewModel()
        vm.setSeriesCount(12)
        vm.setSeriesScheme(SeriesScheme.Grid)
        vm.setGridScheme(GridScheme.Pink)
        Monitor(
            points = samplePoints,
            monitorViewModel = vm
        )
    }
}
