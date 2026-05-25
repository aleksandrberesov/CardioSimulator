package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.Lead as DomainLead
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.MonitorControlPanel
import com.example.cardiosimulator.ui.panels.RhythmChoosingDrawer
import com.example.cardiosimulator.ui.panels.RhythmChoosingPanel
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun TeachingScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    rhythmViewModel: RhythmViewModel = viewModel(),
) {
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val significantPoints by rhythmViewModel.significantPoints.collectAsState()

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().middleSectionCenter(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val mode by monitorViewModel.monitorMode.collectAsState()
            Monitor(
                modifier = Modifier.weight(1f).padding(start = 24.dp),
                monitorViewModel = monitorViewModel,
            ) { rows, columns ->
                LeadsGrid(
                    rows = rows,
                    columns = columns,
                    itemCount = mode.count,
                ) { _, lead ->
                    val leadPoints = lead?.let { waveforms[it] }
                        ?.takeIf { it.values.size >= 2 }
                        ?: Points(emptyList<Float>())
                    LeadView(
                        points = leadPoints,
                        title = lead?.name ?: "",
                        isRunning = mode.isRunning,
                        significantPoints = significantPoints
                    )
                }
            }
        }

        RhythmChoosingDrawer(
            appViewModel = appViewModel,
            rhythms = rhythms,
            selectedId = selectedRhythm?.id,
            onRhythmSelect = { rhythmViewModel.selectRhythm(it.id) },
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
