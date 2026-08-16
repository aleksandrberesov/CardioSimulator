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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.cardiosimulator.domain.Student
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.theme.*
import com.example.cardiosimulator.ui.components.ModeCard
import com.example.cardiosimulator.ui.viewmodels.*
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
    var showAbortDialog by remember { mutableStateOf(false) }

    val selectedCourseId by appViewModel.selectedCourseId.collectAsState()
    val activeTest by examinationViewModel.activeTest.collectAsState()

    if (showAbortDialog) {
        AlertDialog(
            onDismissRequest = { showAbortDialog = false },
            title = { Text(stringResource(R.string.test_abort)) },
            text = { Text("Abort exam and return to lecture?") },
            confirmButton = {
                TextButton(onClick = {
                    showAbortDialog = false
                    appViewModel.setPreserveCourseSelection(true)
                    appViewModel.operatingModes.find { it.id == OperatingMode.Teaching }?.let {
                        appViewModel.updateOperatingMode(it)
                    }
                }) {
                    Text(stringResource(R.string.cd_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbortDialog = false }) {
                    Text(stringResource(R.string.cd_cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabRow(
                selectedTabIndex = subMode.ordinal,
                modifier = Modifier.weight(1f)
            ) {
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

            if (selectedCourseId != null && selectedCourseId != AppViewModel.ALL_RHYTHMS_ID) {
                Button(
                    onClick = {
                        if (activeTest != null) {
                            showAbortDialog = true
                        } else {
                            appViewModel.setPreserveCourseSelection(true)
                            appViewModel.operatingModes.find { it.id == OperatingMode.Teaching }?.let {
                                appViewModel.updateOperatingMode(it)
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.back_to_lecture))
                }
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
    val mode by viewModel.mode.collectAsState()
    val lastResult by viewModel.lastResult.collectAsState()
    val activeTest by viewModel.activeTest.collectAsState()
    val isGroupSessionActive by viewModel.isGroupSessionActive.collectAsState()

    if (lastResult != null) {
        ExamResultSummary(lastResult!!, onNewAttempt = { viewModel.reset() }, testRepository)
    } else {
        when (mode) {
            ExamMode.Choice -> {
                ExamStartArea(onSelectMode = { 
                    if (it == ExamMode.Group) viewModel.setMode(ExamMode.Group) 
                    else viewModel.setMode(ExamMode.IndividualSetup)
                })
            }
            ExamMode.IndividualSetup -> {
                ExamIndividualSetupArea(viewModel, appViewModel)
            }
            ExamMode.Group -> {
                if (isGroupSessionActive) {
                    GroupSessionView(viewModel)
                } else {
                    ExamGroupSetupArea(viewModel, appViewModel)
                }
            }
            ExamMode.IndividualActive -> {
                if (activeTest != null) {
                    ExamActiveTestView(viewModel, monitorViewModel, rhythmViewModel, appViewModel)
                }
            }
        }
    }
}

@Composable
fun ExamStartArea(onSelectMode: (ExamMode) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.exam_choose_prompt),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            ModeCard(
                title = stringResource(R.string.exam_mode_individual),
                icon = Icons.Default.Person,
                onClick = { onSelectMode(ExamMode.IndividualSetup) }
            )
            Spacer(modifier = Modifier.width(32.dp))
            ModeCard(
                title = stringResource(R.string.exam_mode_group),
                icon = Icons.Default.Groups,
                onClick = { onSelectMode(ExamMode.Group) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamIndividualSetupArea(viewModel: ExaminationViewModel, appViewModel: AppViewModel) {
    val roster = remember { appViewModel.studentStore?.list().orEmpty() }
    val testThemes = remember { appViewModel.testThemeStore?.readThemes() ?: emptyList() }
    
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(10) }
    var selectedTheme by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.setMode(ExamMode.Choice) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.exam_start_title), style = MaterialTheme.typography.headlineSmall)
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth(0.6f)) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (roster.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val manualLabel = stringResource(R.string.exam_pick_student_manual)
                    var selectedLabel by remember { mutableStateOf(manualLabel) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.exam_pick_student)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(manualLabel) },
                                onClick = {
                                    selectedLabel = manualLabel
                                    expanded = false
                                }
                            )
                            roster.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.fullName} · ${s.group}") },
                                    onClick = {
                                        name = s.fullName
                                        group = s.group
                                        selectedLabel = "${s.fullName} · ${s.group}"
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

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
                
                Spacer(modifier = Modifier.height(24.dp))

                Text(stringResource(R.string.test_group_questions_count), style = MaterialTheme.typography.titleSmall)
                Row {
                    listOf(10, 20, 30).forEach { c ->
                        FilterChip(
                            selected = count == c,
                            onClick = { count = c },
                            label = { Text(c.toString()) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(stringResource(R.string.test_group_theme), style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = selectedTheme == null,
                        onClick = { selectedTheme = null },
                        label = { Text(stringResource(R.string.exam_mode_all)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    testThemes.forEach { theme ->
                        FilterChip(
                            selected = selectedTheme == theme,
                            onClick = { selectedTheme = theme },
                            label = { Text(theme) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { viewModel.generateAndStartIndividual(count, selectedTheme, ExamStudentInfo(name, group)) },
                    enabled = name.isNotBlank() && group.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.exam_start))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamGroupSetupArea(viewModel: ExaminationViewModel, appViewModel: AppViewModel) {
    val testThemes = remember { appViewModel.testThemeStore?.readThemes() ?: emptyList() }
    var count by remember { mutableIntStateOf(10) }
    var selectedTheme by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.setMode(ExamMode.Choice) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.test_group_setup_title), style = MaterialTheme.typography.headlineSmall)
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth(0.6f)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.test_group_questions_count), style = MaterialTheme.typography.titleSmall)
                Row {
                    listOf(10, 20, 30).forEach { c ->
                        FilterChip(
                            selected = count == c,
                            onClick = { count = c },
                            label = { Text(c.toString()) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(stringResource(R.string.test_group_theme), style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = selectedTheme == null,
                        onClick = { selectedTheme = null },
                        label = { Text(stringResource(R.string.exam_mode_all)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    testThemes.forEach { theme ->
                        FilterChip(
                            selected = selectedTheme == theme,
                            onClick = { selectedTheme = theme },
                            label = { Text(theme) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { viewModel.startGroupSession(count, selectedTheme) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.test_group_start))
                }
            }
        }
    }
}

@Composable
fun ExamActiveTestView(
    viewModel: ExaminationViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    appViewModel: AppViewModel
) {
    val activeTest by viewModel.activeTest.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
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
                            artifacts = mode.artifacts,
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setMode(ExamMode.Choice) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.test_group_title), style = MaterialTheme.typography.headlineMedium)
            }
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
                Text(stringResource(R.string.test_group_stop))
            }
        }
        
        VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.test_group_participants, participants.size), style = MaterialTheme.typography.titleLarge)
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
                                    color = if (p.result!!.passed) Positive else Negative,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(stringResource(R.string.test_group_in_progress), color = TextSecondary)
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
                containerColor = if (result.passed) Positive.copy(alpha = 0.12f) else Negative.copy(alpha = 0.12f)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (result.passed) stringResource(R.string.exam_passed) else stringResource(R.string.exam_failed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (result.passed) Positive else Negative,
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
                colors = CardDefaults.cardColors(containerColor = if (qResult.isCorrect) Positive.copy(alpha = 0.08f) else Negative.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (qResult.isCorrect) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (qResult.isCorrect) Positive else Negative
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Вопрос ${index + 1}", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "ID вопроса: ${qResult.questionId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
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
                                color = if (result.passed) Positive else Negative
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
