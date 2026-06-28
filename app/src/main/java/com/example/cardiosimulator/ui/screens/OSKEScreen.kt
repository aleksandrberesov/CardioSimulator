package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.*
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.OskeSubMode
import com.example.cardiosimulator.ui.viewmodels.OskeViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OSKEScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    oskeViewModel: OskeViewModel
) {
    val subMode by oskeViewModel.subMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subMode.ordinal) {
            OskeSubMode.entries.forEach { mode ->
                Tab(
                    selected = subMode == mode,
                    onClick = { oskeViewModel.setSubMode(mode) },
                    text = {
                        Text(
                            when (mode) {
                                OskeSubMode.Exam -> stringResource(R.string.oske_tab_exam)
                                OskeSubMode.Results -> stringResource(R.string.oske_tab_results)
                            }
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (subMode) {
                OskeSubMode.Exam -> OskeExamView(oskeViewModel, monitorViewModel, rhythmViewModel, appViewModel)
                OskeSubMode.Results -> OskeResultsView(oskeViewModel)
            }
        }
    }
}

@Composable
fun OskeExamView(
    viewModel: OskeViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    appViewModel: AppViewModel
) {
    val activeForm by viewModel.activeForm.collectAsState()
    val lastResult by viewModel.lastResult.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()

    var showStartDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeForm) {
        if (activeForm == null && lastResult == null) {
            showStartDialog = true
        }
    }

    val rhythms: List<PathologyEntry> by rhythmViewModel.rhythms.collectAsState()

    if (showStartDialog) {
        OskeStartDialog(
            rhythms = rhythms,
            getEcgIdsWithKeys = { viewModel.getEcgIdsWithKeys(it) },
            onDismiss = { showStartDialog = false },
            onStart = { name, group, specialty, ecgId ->
                viewModel.startExam(OskeStudentInfo(name, group), specialty, ecgId)
                rhythmViewModel.selectRhythm(ecgId)
                appViewModel.sendStartCommand(ecgId)
                showStartDialog = false
            }
        )
    }

    if (lastResult != null) {
        OskeResultSummary(lastResult!!, onNewAttempt = { viewModel.resetExam() })
    } else if (activeForm != null) {
        Row(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    text = activeForm!!.formId.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                activeForm!!.questions.forEach { question ->
                    OskeQuestionBlock(
                        question = question,
                        selectedIds = selections[question.id] ?: emptyList(),
                        onOptionToggle = { optionId ->
                            viewModel.selectOption(question.id, optionId, question.kind)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.submitExam()
                        appViewModel.sendStopCommand()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.oske_submit_button))
                }
            }
        }
    }
}

@Composable
fun OskeStartDialog(
    rhythms: List<PathologyEntry>,
    getEcgIdsWithKeys: (OskeSpecialty) -> List<String>,
    onDismiss: () -> Unit,
    onStart: (String, String, OskeSpecialty, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf(OskeSpecialty.Therapy) }
    var selectedEcgId by remember { mutableStateOf<String?>(null) }

    val ecgsWithKeys = remember(specialty) { getEcgIdsWithKeys(specialty) }
    val filteredRhythms = rhythms.filter { it.id in ecgsWithKeys }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.oske_start_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.oske_student_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text(stringResource(R.string.oske_student_group)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(R.string.oske_specialty), style = MaterialTheme.typography.titleSmall)
                OskeSpecialty.entries.forEach { spec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = specialty == spec, onClick = { specialty = spec })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = specialty == spec, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(spec.name)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.oske_ecg_pick), style = MaterialTheme.typography.titleSmall)

                if (filteredRhythms.isEmpty()) {
                    Text(
                        stringResource(R.string.oske_no_key_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Simple ECG picker
                filteredRhythms.forEach { rhythm: PathologyEntry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selectedEcgId == rhythm.id, onClick = { selectedEcgId = rhythm.id })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedEcgId == rhythm.id, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(rhythm.titleEn)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(name, group, specialty, selectedEcgId!!) },
                enabled = name.isNotBlank() && group.isNotBlank() && selectedEcgId != null
            ) {
                Text(stringResource(R.string.oske_start_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cd_cancel))
            }
        }
    )
}

@Composable
fun OskeResultSummary(result: OskeResult, onNewAttempt: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.oske_result_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = if (result.passed) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (result.passed) stringResource(R.string.oske_passed) else stringResource(R.string.oske_failed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (result.passed) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.oske_score_format, result.correctCount, result.totalCount),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "${result.student.fullName} (${result.student.group})")
                Text(text = "ECG: ${result.ecgId}")
                Text(text = "Specialty: ${result.specialty}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onNewAttempt) {
            Text(stringResource(R.string.oske_new_attempt))
        }
    }
}

@Composable
fun OskeResultsView(viewModel: OskeViewModel) {
    val results by viewModel.results.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshResults()
    }

    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().middleSectionCenter(), contentAlignment = Alignment.Center) {
            Text("No results yet")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().middleSectionCenter()) {
            items(results) { result ->
                ListItem(
                    headlineContent = { Text(result.student.fullName) },
                    supportingContent = {
                        val date = remember(result.timestamp) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(result.timestamp))
                        }
                        Text("${result.student.group} | ${result.specialty} | ${result.ecgId} | $date")
                    },
                    trailingContent = {
                        Text(
                            text = "${result.correctCount}/${result.totalCount}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (result.passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
