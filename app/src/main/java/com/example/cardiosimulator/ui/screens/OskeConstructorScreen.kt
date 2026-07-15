package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.OskeSpecialty
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.OskeViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun OskeConstructorScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    oskeViewModel: OskeViewModel
) {
    val form by oskeViewModel.constructorForm.collectAsState()
    val selections by oskeViewModel.constructorSelections.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()

    val specialty by oskeViewModel.constructorSpecialty.collectAsState()
    val selectedEcgId by oskeViewModel.constructorSelectedEcgId.collectAsState()
    val rhythms: List<PathologyEntry> by rhythmViewModel.rhythms.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Selection Panel
        Column(
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Specialty")
                OskeSpecialty.entries.forEach { spec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = specialty == spec,
                                onClick = { oskeViewModel.setConstructorSelection(spec, selectedEcgId) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = specialty == spec,
                            onClick = { oskeViewModel.setConstructorSelection(spec, selectedEcgId) }
                        )
                        Text(spec.name)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("ECG")
            }

            com.example.cardiosimulator.ui.panels.RhythmSelector(
                appViewModel = appViewModel,
                modifier = Modifier.fillMaxWidth().height(320.dp),
                rhythms = rhythms,
                selectedId = selectedEcgId,
                showPinButton = false,
                onRhythmSelect = { oskeViewModel.setConstructorSelection(specialty, it.id) },
            )
        }

        VerticalDivider()

        if (form != null) {
            Box(modifier = Modifier.weight(1f).middleSectionCenter()) {
                Monitor(
                    modifier = Modifier.fillMaxSize(),
                    monitorViewModel = monitorViewModel,
                ) { rows, columns, xOffset, scheme ->
                    LeadsGrid(
                        rows = rows,
                        columns = columns,
                        itemCount = mode.count,
                    ) { _, lead ->
                        val points = lead?.let { waveforms[it] } ?: Points(emptyList<Float>())
                        LeadView(
                            points = points,
                            title = lead?.name ?: "",
                            isRunning = mode.isRunning,
                            xOffsetPx = xOffset,
                            gridScheme = scheme,
                            artifacts = mode.artifacts,
                            filterType = mode.filterType,
                            calibration = mode.calibration
                        )
                    }
                }
            }

            VerticalDivider()

            Column(
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Correct Answers", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                form!!.questions.forEach { question ->
                    OskeQuestionBlock(
                        question = question,
                        selectedIds = selections[question.id] ?: emptyList(),
                        onOptionToggle = { optionId ->
                            oskeViewModel.selectConstructorOption(question.id, optionId, question.kind)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Select specialty and ECG to edit answer key")
            }
        }
    }
}
