package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import com.example.cardiosimulator.ui.viewmodels.TestConstructorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestConstructorScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    testConstructorViewModel: TestConstructorViewModel
) {
    val tests = appViewModel.testRepository?.tests() ?: emptyList()
    val testId by testConstructorViewModel.testId.collectAsState()
    val title by testConstructorViewModel.title.collectAsState()
    val time by testConstructorViewModel.questionTimeSeconds.collectAsState()
    val questions by testConstructorViewModel.questions.collectAsState()
    
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Monitor Panel
        Box(modifier = Modifier.weight(1f).middleSectionLeft()) {
            Monitor(
                modifier = Modifier.fillMaxSize(),
                monitorViewModel = monitorViewModel,
            ) { rows, columns, xOffset, scheme ->
                LeadsGrid(
                    rows = rows,
                    columns = columns,
                    itemCount = mode.count,
                    leadOrder = mode.leadOrder ?: com.example.cardiosimulator.ui.display.LEAD_ORDER
                ) { _, lead ->
                    val points = lead?.let { waveforms[it] } ?: Points(emptyList<Float>())
                    LeadView(
                        points = points,
                        title = lead?.name ?: "",
                        isRunning = mode.isRunning,
                        xOffsetPx = xOffset,
                        gridScheme = scheme,
                    )
                }
            }
        }

        VerticalDivider()

        // Editor Panel
        Column(
            modifier = Modifier
                .width(500.dp)
                .fillMaxHeight()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = tests.find { it.testId == testId }?.title ?: "Select Test",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.test_ctor_tests_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        tests.forEach { test ->
                            DropdownMenuItem(
                                text = { Text(test.title) },
                                onClick = {
                                    testConstructorViewModel.load(test.testId)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { testConstructorViewModel.newTest() }) {
                    Text(stringResource(R.string.test_ctor_new))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { testConstructorViewModel.setTitle(it) },
                label = { Text(stringResource(R.string.test_ctor_title_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = if (time == 0) "" else time.toString(),
                onValueChange = { testConstructorViewModel.setQuestionTimeSeconds(it.toIntOrNull() ?: 0) },
                label = { Text(stringResource(R.string.test_ctor_time_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            questions.forEach { question ->
                QuestionEditorCard(
                    question = question,
                    rhythms = rhythms,
                    onUpdate = { transform -> testConstructorViewModel.updateQuestion(question.id, transform) },
                    onRemove = { testConstructorViewModel.removeQuestion(question.id) },
                    onAddOption = { testConstructorViewModel.addOption(question.id) },
                    onRemoveOption = { optId -> testConstructorViewModel.removeOption(question.id, optId) },
                    onPreview = { pathologyId ->
                        if (pathologyId != null) {
                            rhythmViewModel.selectRhythm(pathologyId, persist = false)
                            appViewModel.sendStartCommand(pathologyId)
                        } else {
                            appViewModel.sendStopCommand()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = { testConstructorViewModel.addQuestion() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.test_ctor_add_question))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { testConstructorViewModel.save() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.test_ctor_save))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { testConstructorViewModel.delete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.test_ctor_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionEditorCard(
    question: com.example.cardiosimulator.domain.TestQuestion,
    rhythms: List<PathologyEntry>,
    onUpdate: ((com.example.cardiosimulator.domain.TestQuestion) -> com.example.cardiosimulator.domain.TestQuestion) -> Unit,
    onRemove: () -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (String) -> Unit,
    onPreview: (String?) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.test_ctor_question_label_format, question.number),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }

            OutlinedTextField(
                value = question.text,
                onValueChange = { text -> onUpdate { it.copy(text = text) } },
                label = { Text(stringResource(R.string.test_ctor_question_text)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ECG Picker
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = rhythms.find { it.id == question.pathologyId }?.titleEn ?: stringResource(R.string.test_ctor_ecg_none),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.test_ctor_ecg)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.test_ctor_ecg_none)) },
                        onClick = {
                            onUpdate { it.copy(pathologyId = null) }
                            onPreview(null)
                            expanded = false
                        }
                    )
                    rhythms.forEach { rhythm ->
                        DropdownMenuItem(
                            text = { Text(rhythm.titleEn) },
                            onClick = {
                                onUpdate { it.copy(pathologyId = rhythm.id) }
                                onPreview(rhythm.id)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Options
            question.options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = question.correctOptionId == option.id,
                        onClick = { onUpdate { it.copy(correctOptionId = option.id) } }
                    )
                    OutlinedTextField(
                        value = option.text,
                        onValueChange = { text ->
                            onUpdate { q ->
                                q.copy(options = q.options.map { if (it.id == option.id) it.copy(text = text) else it })
                            }
                        },
                        label = { Text(stringResource(R.string.test_ctor_option_format, index + 1)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemoveOption(option.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }

            TextButton(onClick = onAddOption) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.test_ctor_add_option))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = question.comment,
                onValueChange = { comment -> onUpdate { it.copy(comment = comment) } },
                label = { Text(stringResource(R.string.test_ctor_comment)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
