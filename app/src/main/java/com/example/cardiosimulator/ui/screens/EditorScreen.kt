package com.example.cardiosimulator.ui.screens

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
import androidx.compose.material3.OutlinedTextField
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
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.components.Tab
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
    var selectedLead by remember { mutableStateOf(Lead.II) }
    LaunchedEffect(Unit) {
        monitorViewModel.setSeriesCount(1)
        monitorViewModel.setSeriesScheme(SeriesScheme.OneColumn)
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
            // Selector for choosing Lead
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Lead.entries.forEach { lead ->
                    Tab(
                        text = lead.name,
                        onClick = { selectedLead = lead },
                        backgroundColor = if (selectedLead == lead)
                            MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
            Monitor(
                modifier = Modifier.fillMaxWidth().weight(5f),
                monitorViewModel = monitorViewModel,
                waveformsByLead = waveforms,
                leadOrder = listOf(selectedLead)
            )
            OutlinedTextField(
                value = selectedRhythm?.fileName ?: "",
                onValueChange = {},
                label = { Text(stringResource(R.string.editor_file_content_label)) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                readOnly = true,
                singleLine = true,
            )
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
