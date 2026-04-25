package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun TestingScreen(viewModel: AppViewModel){
    val monitorViewModel: MonitorViewModel = viewModel()

    LaunchedEffect(Unit) {
        monitorViewModel.setSeriesCount(12)
        monitorViewModel.setSeriesScheme(SeriesScheme.Grid)
    }

    Row(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
    ) {
        Box(
            modifier = Modifier.weight(4f).middleSectionLeft(),
            contentAlignment = Alignment.TopStart
        ) {
            Monitor(
                points = viewModel.points,
                appViewModel = viewModel,
                monitorViewModel = monitorViewModel
            )
        }
        Box(
            modifier = Modifier.weight(1f).middleSectionCenter(),
            contentAlignment = Alignment.Center
        ) {

        }
    }
}
