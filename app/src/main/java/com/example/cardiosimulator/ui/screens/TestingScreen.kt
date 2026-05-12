package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Lead
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.MonitorControlPanel
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun TestingScreen(
    viewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    rhythmViewModel: RhythmViewModel = viewModel()
){

    LaunchedEffect(Unit) {
        monitorViewModel.setSeriesCount(12)
        monitorViewModel.setSeriesScheme(SeriesScheme.Grid)
    }

    Row(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.weight(4f).middleSectionLeft(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val mode by monitorViewModel.monitorMode.collectAsState()
            Monitor(
                modifier = Modifier.weight(1f),
                monitorViewModel = monitorViewModel,
            ) { rows, columns ->
                LeadsGrid(
                    rows = rows,
                    columns = columns,
                    itemCount = mode.count
                ) { _, lead ->
                    Lead(
                        points = Points(emptyList<Float>()),
                        title = lead?.name ?: ""
                    )
                }
            }
            MonitorControlPanel(
                viewModel = monitorViewModel,
                onStartStopClick = { isRunning ->
                    if (isRunning) {
                        viewModel.sendStartCommand()
                    } else {
                        viewModel.sendStopCommand()
                    }
                }
            )
        }
        Box(
            modifier = Modifier.weight(1f).middleSectionCenter(),
            contentAlignment = Alignment.Center
        ) {

        }
    }
}
