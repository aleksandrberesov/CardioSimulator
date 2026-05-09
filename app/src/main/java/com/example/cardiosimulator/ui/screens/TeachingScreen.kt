package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.RhythmChoosingPanel
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun TeachingScreen(viewModel: AppViewModel){
    val monitorViewModel: MonitorViewModel = viewModel()
    val rhythms by viewModel.rhythms.collectAsState()
    val selectedRhythm by viewModel.selectedRhythm.collectAsState()
    val waveforms by viewModel.waveforms.collectAsState()

    LaunchedEffect(Unit) {
        monitorViewModel.setSeriesCount(12)
        monitorViewModel.setSeriesScheme(SeriesScheme.Grid)
    }

    Row(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
    ) {
        Box(
            modifier = Modifier.weight(1f).middleSectionLeft(),
            contentAlignment = Alignment.TopStart
        ) {
            RhythmChoosingPanel(
                rhythms = rhythms,
                selectedPathology = selectedRhythm?.pathology,
                onRhythmSelect = { viewModel.selectRhythm(it.pathology) },
            )
        }
        Box(
            modifier = Modifier.weight(4f).middleSectionCenter(),
            contentAlignment = Alignment.Center
        ) {
            Monitor(
                monitorViewModel = monitorViewModel,
                waveformsByLead = waveforms,
                onStartStopClick = { isRunning ->
                    if (isRunning) {
                        viewModel.sendStartCommand(selectedRhythm?.pathology)
                    } else {
                        viewModel.sendStopCommand()
                    }
                }
            )
        }
    }
}
