package com.example.cardiosimulator.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.data.TestRepository
import com.example.cardiosimulator.domain.*
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ExaminationViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class ExamSubMode { Exam, Results }

@Composable
fun ExaminationScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    examinationViewModel: ExaminationViewModel,
    testRepository: TestRepository
) {
    var subMode by remember { mutableStateOf(ExamSubMode.Exam) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subMode.ordinal) {
            ExamSubMode.entries.forEach { mode ->
                Tab(
                    selected = subMode == mode,
                    onClick = { subMode = mode },
                    text = {
                        Text(
                            when (mode) {
                                ExamSubMode.Exam -> stringResource(R.string.exam_tab_exam)
                                ExamSubMode.Results -> stringResource(R.string.exam_tab_results)
                            }
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (subMode) {
                ExamSubMode.Exam -> ExamWorkView(examinationViewModel, monitorViewModel, rhythmViewModel, appViewModel, testRepository)
                ExamSubMode.Results -> ExamResultsView(examinationViewModel, testRepository)
            }
        }
    }
}

@Composable
fun ExamWorkView(
    viewModel: ExaminationViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    appViewModel: AppViewModel,
    testRepository: TestRepository
) {
    val activeTest by viewModel.activeTest.collectAsState()
    val lastResult by viewModel.lastResult.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val isGroupSessionActive by viewModel.isGroupSessionActive.collectAsState()

    var showStartDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeTest, isGroupSessionActive) {
        if (activeTest == null && lastResult == null && !isGroupSessionActive) {
            showStartDialog = true
        }
    }

    if (showStartDialog) {
        ExamStartSelectionDialog(
            testThemes = appViewModel.testThemeStore?.readThemes() ?: emptyList(),
            onDismiss = { showStartDialog = false },
            onStartIndividual = { name, group, count, theme ->
                viewModel.generateAndStartIndividual(count, theme, ExamStudentInfo(name, group))
                showStartDialog = false
            },
            onStartGroup = { count, theme ->
                viewModel.startGroupSession(count, theme)
                showStartDialog = false
            }
        )
    }

    if (isGroupSessionActive) {
        GroupSessionView(viewModel)
    } else if (lastResult != null) {
        ExamResultSummary(lastResult!!, onNewAttempt = { viewModel.reset() }, testRepository)
    } else if (activeTest != null) {
        val currentQuestion = viewModel.currentQuestion
        val context = LocalContext.current

        LaunchedEffect(currentQuestion?.id) {
            val q = currentQuestion ?: return@LaunchedEffect
            if (q.pathologyId != null) {
                rhythmViewModel.selectRhythm(q.pathologyId, persist = false)
                monitorViewModel.setSeriesScheme(q.scheme, persist = false)
                monitorViewModel.setLeadOrder(q.leads.ifEmpty { null })
                appViewModel.sendStartCommand(q.pathologyId)
            } else {
                appViewModel.sendStopCommand()
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(3f).middleSectionLeft()) {
                if (currentQuestion?.stimulus == QuestionStimulus.Image) {
                    AsyncImage(
                        model = currentQuestion.imagePath?.let { path ->
                            if (path.startsWith("/")) File(path)
                            else File(context.filesDir, "${AppViewModel.TEST_IMAGES_DIR}/$path")
                        },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                } else {
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
                                filterType = mode.filterType,
                                calibration = mode.calibration
                            )
                        }
                    }
                }
            }

            VerticalDivider()

            Box(modifier = Modifier.weight(2f).middleSectionCenter()) {
                if (currentQuestion != null) {
                    ExamQuestionPanel(
                        question = currentQuestion,
                        totalQuestions = activeTest!!.questions.size,
                        remainingSeconds = remainingSeconds,
                        selectedOptionId = selections[currentQuestion.id],
                        onOptionSelect = { viewModel.select(it) },
                        onNext = { viewModel.next() },
                        isTimed = activeTest!!.questionTimeSeconds > 0
                    )
                }
            }
        }
    }
}

@Composable
fun ExamStartSelectionDialog(
    testThemes: List<String>,
    onDismiss: () -> Unit,
    onStartIndividual: (String, String, Int, String?) -> Unit,
    onStartGroup: (Int, String?) -> Unit
) {
    var mode by remember { mutableStateOf("Individual") }
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(10) }
    var selectedTheme by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exam_start_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = mode == "Individual",
                        onClick = { mode = "Individual" },
                        label = { Text("Индивидуально") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = mode == "Group",
                        onClick = { mode = "Group" },
                        label = { Text("Групповое") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                if (mode == "Individual") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.exam_field_full_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = group,
                        onValueChange = { group = it },
                        label = { Text(stringResource(R.string.exam_field_group)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text("Количество вопросов:", style = MaterialTheme.typography.titleSmall)
                Row {
                    listOf(10, 20, 30).forEach { c ->
                        FilterChip(
                            selected = count == c,
                            onClick = { count = c },
                            label = { Text(c.toString()) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Тема:", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = selectedTheme == null,
                        onClick = { selectedTheme = null },
                        label = { Text("Все") },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    testThemes.forEach { theme ->
                        FilterChip(
                            selected = selectedTheme == theme,
                            onClick = { selectedTheme = theme },
                            label = { Text(theme) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (mode == "Individual") onStartIndividual(name, group, count, selectedTheme)
                    else onStartGroup(count, selectedTheme)
                },
                enabled = (mode == "Group") || (name.isNotBlank() && group.isNotBlank())
            ) {
                Text(stringResource(R.string.exam_start))
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
fun GroupSessionView(viewModel: ExaminationViewModel) {
    val groupIp by viewModel.groupIp.collectAsState()
    val participants by viewModel.participants.collectAsState()
    
    val url = "http://${groupIp ?: "0.0.0.0"}:8080/"
    
    val qrBitmap = remember(url) {
        runCatching {
            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.encodeBitmap(url, BarcodeFormat.QR_CODE, 400, 400)
        }.getOrNull()
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Групповое тестирование", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(300.dp).background(Color.White).padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(url, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { viewModel.stopGroupSession() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Остановить сессию")
            }
        }
        
        VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text("Участники (${participants.size})", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn {
                items(participants) { p ->
                    ListItem(
                        headlineContent = { Text(p.student.fullName) },
                        supportingContent = { Text(p.student.group) },
                        trailingContent = {
                            if (p.result != null) {
                                Text(
                                    "${p.result!!.correctCount}/${p.result!!.totalCount}",
                                    color = if (p.result!!.passed) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text("В процессе", color = Color.Gray)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun ExamResultSummary(result: ExamResult, onNewAttempt: () -> Unit, testRepository: TestRepository) {
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
                    text = if (result.passed) stringResource(R.string.exam_passed) else stringResource(R.string.exam_failed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (result.passed) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.exam_score_format, result.correctCount, result.totalCount),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "${result.student.fullName} (${result.student.group})")
                Text(text = "Test: ${result.testTitle}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        result.questions.forEachIndexed { index, qResult ->
            Card(
                modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = if (qResult.isCorrect) Color(0xFFF1F8E9) else Color(0xFFFBE9E7))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (qResult.isCorrect) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (qResult.isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Вопрос ${index + 1}", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "ID вопроса: ${qResult.questionId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onNewAttempt) {
            Text(stringResource(R.string.exam_new_attempt))
        }
    }
}

@Composable
fun ExamResultsView(viewModel: ExaminationViewModel, testRepository: TestRepository) {
    val results by viewModel.results.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshResults()
    }

    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().middleSectionCenter(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.exam_results_empty))
        }
    } else {
        var selectedResult by remember { mutableStateOf<ExamResult?>(null) }

        if (selectedResult != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                ExamResultSummary(selectedResult!!, onNewAttempt = { selectedResult = null }, testRepository)
                IconButton(onClick = { selectedResult = null }, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
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
                            Text("${result.student.group} | ${result.testTitle} | $date")
                        },
                        trailingContent = {
                            Text(
                                text = "${result.correctCount}/${result.totalCount}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (result.passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        },
                        modifier = Modifier.clickable { selectedResult = result }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
