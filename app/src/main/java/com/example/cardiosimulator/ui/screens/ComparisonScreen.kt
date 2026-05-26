package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.ui.panels.MonitorControlPanel
import com.example.cardiosimulator.ui.panels.RhythmChoosingDrawer

@Composable
fun ComparisonScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    rhythmViewModel: RhythmViewModel = viewModel(),
) {
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val comparisonWaveforms by rhythmViewModel.comparisonWaveforms.collectAsState()
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    
    // Comparison specific state
    var comparisonRhythms by remember { mutableStateOf(listOf<String>()) }
    val focusedLead = Lead.II // Default as per requirement

    // Force 6 rows, 1 column for comparison
    LaunchedEffect(Unit) {
        monitorViewModel.setSeriesCount(6)
        monitorViewModel.setSeriesScheme(SeriesScheme.OneColumn)
    }

    LaunchedEffect(comparisonRhythms, focusedLead) {
        if (comparisonRhythms.isNotEmpty()) {
            rhythmViewModel.loadComparisonWaveforms(comparisonRhythms, focusedLead)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val mode by monitorViewModel.monitorMode.collectAsState()
            
            Monitor(
                modifier = Modifier.weight(1f).padding(start = 24.dp),
                monitorViewModel = monitorViewModel,
            ) { rows, columns, scrollOffsetPx ->
                
                // Construct the list of rhythms to display (selected + up to 5 comparisons)
                val displayList = mutableListOf<com.example.cardiosimulator.domain.PathologyEntry?>()
                displayList.add(selectedRhythm)
                comparisonRhythms.forEach { compId ->
                    displayList.add(rhythms.find { it.id == compId })
                }
                // Fill the rest with nulls up to the grid count
                while (displayList.size < mode.count) {
                    displayList.add(null)
                }

                LeadsGrid(
                    rows = rows,
                    columns = columns,
                    itemCount = mode.count,
                    scrollOffsetPx = scrollOffsetPx
                ) { index, _, offset ->
                    val entry = displayList.getOrNull(index)
                    val leadPoints = if (index == 0) {
                        entry?.let { waveforms[focusedLead] }
                    } else {
                        entry?.let { comparisonWaveforms[it.id] }
                    } ?: Points(emptyList<Float>())

                    val title = entry?.let {
                        if (selectedLanguage == Language.RU) it.nameRu ?: it.titleEn else it.titleEn
                    } ?: if (index == 0) "" else stringResource(R.string.compare_select_rhythm)

                    LeadView(
                        points = leadPoints,
                        title = title,
                        isRunning = mode.isRunning,
                        scrollOffsetPx = offset
                    )
                }
            }
            
            MonitorControlPanel(
                viewModel = monitorViewModel,
                onStartStopClick = { isRunning ->
                    if (isRunning) {
                        appViewModel.sendStartCommand(selectedRhythm?.id, selectedRhythm?.titleEn)
                    } else {
                        appViewModel.sendStopCommand()
                    }
                }
            )
        }

        RhythmChoosingDrawer(
            appViewModel = appViewModel,
            rhythms = rhythms,
            selectedId = selectedRhythm?.id,
            onRhythmSelect = { 
                // In comparison mode, clicking a rhythm in the drawer adds it to the comparison list
                // up to 5 items. If we already have 5, maybe replace the last one.
                if (it.id != selectedRhythm?.id && !comparisonRhythms.contains(it.id)) {
                    val newList = comparisonRhythms.toMutableList()
                    if (newList.size >= 5) {
                        newList.removeLast()
                    }
                    newList.add(it.id)
                    comparisonRhythms = newList
                }
            },
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
