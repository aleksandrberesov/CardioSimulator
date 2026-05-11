package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.display.EditableLead
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.RhythmChoosingPanel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun EditorScreen(
    viewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel()
) {
    val rhythms by viewModel.rhythms.collectAsState()
    val selectedRhythm by viewModel.selectedRhythm.collectAsState()
    val waveforms by viewModel.waveforms.collectAsState()
    val allSeries by viewModel.allSeries.collectAsState()
    val allParts by viewModel.allParts.collectAsState()
    var selectedLeads by remember { mutableStateOf(setOf(Lead.II)) }
    var focusedLead by remember { mutableStateOf<Lead?>(Lead.II) }
    var selectedPartIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedLeads) {
        monitorViewModel.setSeriesCount(selectedLeads.size)
        monitorViewModel.setSeriesScheme(SeriesScheme.OneColumn)
        if (focusedLead !in selectedLeads) {
            focusedLead = selectedLeads.firstOrNull()
        }
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

        Column(
            modifier = Modifier.weight(4f).middleSectionCenter(),
        ) {
            // Selector for choosing Leads
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Lead.entries.forEach { lead ->
                    val isSelected = lead in selectedLeads
                    Tab(
                        text = lead.name,
                        onClick = {
                            selectedLeads = if (isSelected) {
                                if (selectedLeads.size > 1) selectedLeads - lead else selectedLeads
                            } else {
                                selectedLeads + lead
                            }
                        },
                        backgroundColor = if (isSelected)
                            MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }

            Monitor(
                modifier = Modifier.fillMaxWidth().weight(5f),
                monitorViewModel = monitorViewModel,
            ) { rows, columns ->
                LeadsGrid(
                    rows = rows,
                    columns = columns,
                    itemCount = selectedLeads.size,
                    leadOrder = selectedLeads.toList()
                ) { _, lead ->
                    if (lead != null) {
                        val isFocused = lead == focusedLead

                        val seriesId = selectedRhythm?.seriesIdentityByLead?.get(lead)
                        val series = allSeries.find { it.identy == seriesId }
                        val refs = series?.partRefs ?: emptyList()

                        val partNames = mutableListOf<String>()
                        val partPoints = mutableListOf<Points>()

                        refs.forEach { ref ->
                            val part = allParts.find { it.identy == ref.partIdenty }
                            if (part != null) {
                                partNames += part.title
                                val amp = if (part.amplitude > 0f) part.amplitude else 1f
                                partPoints += Points(part.samples.map { (it - 1024f) * amp })
                            }
                        }

                        EditableLead(
                            partNames = partNames,
                            partPoints = partPoints,
                            onPartPointsChange = { index, newPoints ->
                                // TODO: Update part points in viewModel
                            },
                            title = lead.name,
                            selectedPartIndex = if (isFocused) selectedPartIndex else null,
                            onPartClick = {
                                focusedLead = lead
                                selectedPartIndex = it
                            }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.CenterStart
            ){
                val seriesId = selectedRhythm?.seriesIdentityByLead?.get(focusedLead)
                val series = allSeries.find { it.identy == seriesId }
                val parts = series?.partRefs?.mapNotNull { ref ->
                    allParts.find { it.identy == ref.partIdenty }
                } ?: emptyList()

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    parts.forEachIndexed { index, part ->
                        val isSelected = selectedPartIndex == index
                        Tab(
                            text = part.title,
                            onClick = { selectedPartIndex = index },
                            backgroundColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun EditorScreenPreview() {
    val appBuilder = AppBuilder()
    appBuilder.addMode(OperatingModeModel(OperatingMode.Editor))

    val previewViewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    appState = appBuilder.build()
                ) as T
            }
        }
    )

    CardioSimulatorTheme {
        EditorScreen(viewModel = previewViewModel)
    }
}
