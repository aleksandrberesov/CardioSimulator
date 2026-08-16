package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import androidx.compose.ui.platform.LocalContext
import com.example.cardiosimulator.domain.QuestionStimulus
import com.example.cardiosimulator.domain.Test
import com.example.cardiosimulator.domain.OperatingMode
import coil.compose.AsyncImage
import com.example.cardiosimulator.ui.display.Lead as LeadView
import java.io.File
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import com.example.cardiosimulator.ui.viewmodels.TestViewModel
import com.example.cardiosimulator.ui.viewmodels.TestingMode
import com.example.cardiosimulator.ui.components.ModeCard

@Composable
fun TestingScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    testViewModel: TestViewModel
) {
    val activeTest by testViewModel.activeTest.collectAsState()
    val finished by testViewModel.finished.collectAsState()
    val pendingTest by appViewModel.pendingTest.collectAsState()
    val mode by testViewModel.mode.collectAsState()

    LaunchedEffect(pendingTest) {
        pendingTest?.let {
            testViewModel.setMode(TestingMode.Individual)
            testViewModel.start(it)
            appViewModel.setPendingTest(null)
        }
    }

    if (finished) {
        TestResultSummary(testViewModel, appViewModel)
    } else {
        when (mode) {
            TestingMode.Choice -> {
                TestingStartArea(onSelectMode = { testViewModel.setMode(it) })
            }
            TestingMode.Individual -> {
                if (activeTest == null) {
                    TestPicker(testViewModel, appViewModel.testRepository?.tests() ?: emptyList(), appViewModel)
                } else {
                    TestActiveView(testViewModel, monitorViewModel, rhythmViewModel, appViewModel)
                }
            }
            TestingMode.Group -> {
                TestingGroupArea(testViewModel, appViewModel)
            }
        }
    }
}

@Composable
fun TestingStartArea(onSelectMode: (TestingMode) -> Unit) {
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
                onClick = { onSelectMode(TestingMode.Individual) }
            )
            Spacer(modifier = Modifier.width(32.dp))
            ModeCard(
                title = stringResource(R.string.exam_mode_group),
                icon = Icons.Default.Groups,
                onClick = { onSelectMode(TestingMode.Group) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestingGroupArea(viewModel: TestViewModel, appViewModel: AppViewModel) {
    val isSessionActive by viewModel.isGroupSessionActive.collectAsState()
    val testThemes = remember { appViewModel.testThemeStore?.readThemes() ?: emptyList() }

    if (!isSessionActive) {
        var count by remember { mutableIntStateOf(10) }
        var selectedTheme by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setMode(TestingMode.Choice) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.test_group_setup_title), style = MaterialTheme.typography.headlineSmall)
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            Card(modifier = Modifier.fillMaxWidth(0.6f)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.test_group_questions_count), style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.padding(vertical = 8.dp)) {
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

                    Text(stringResource(R.string.test_group_theme), style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).horizontalScroll(rememberScrollState())
                    ) {
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
    } else {
        GroupSessionView(
            ip = viewModel.groupIp.collectAsState().value,
            participants = viewModel.participants.collectAsState().value,
            onStop = { viewModel.stopGroupSession() },
            onBack = { viewModel.setMode(TestingMode.Choice) }
        )
    }
}

@Composable
fun GroupSessionView(
    ip: String?,
    participants: List<com.example.cardiosimulator.network.GroupTestServer.Participant>,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    val url = "http://${ip ?: "0.0.0.0"}:8080/"
    
    val qrBitmap = remember(url) {
        runCatching {
            val barcodeEncoder = com.journeyapps.barcodescanner.BarcodeEncoder()
            barcodeEncoder.encodeBitmap(url, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
        }.getOrNull()
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.test_group_title), style = MaterialTheme.typography.headlineMedium)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (qrBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(300.dp).background(Color.White).padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(url, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.test_group_stop))
            }
        }
        
        VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.test_group_participants, participants.size), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            
            androidx.compose.foundation.lazy.LazyColumn {
                items(participants) { p ->
                    ListItem(
                        headlineContent = { Text(p.student.fullName) },
                        supportingContent = { Text(p.student.group) },
                        trailingContent = {
                            if (p.result != null) {
                                Text(
                                    "${p.result!!.correctCount}/${p.result!!.totalCount}",
                                    color = if (p.result!!.passed) com.example.cardiosimulator.ui.theme.Positive else com.example.cardiosimulator.ui.theme.Negative,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(stringResource(R.string.test_group_in_progress), color = com.example.cardiosimulator.ui.theme.TextSecondary)
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
fun TestActiveView(
    viewModel: TestViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    appViewModel: AppViewModel
) {
    val test by viewModel.activeTest.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val revealed by viewModel.revealed.collectAsState()
    val selectedOptionId by viewModel.selectedOptionId.collectAsState()
    val assemblyAttempt by viewModel.assemblyAttempt.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    val mode by monitorViewModel.monitorMode.collectAsState()

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
            if (currentQuestion?.isAssembly == true && assemblyAttempt != null) {
                EcgAssemblyWorkspace(
                    attempt = assemblyAttempt!!,
                    revealed = revealed,
                    onPlace = { slotIndex, key -> viewModel.placePiece(slotIndex, key) }
                )
            } else if (currentQuestion?.stimulus == QuestionStimulus.Image) {
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
                TestQuestionPanel(
                    question = currentQuestion,
                    totalQuestions = test?.questions?.size ?: 0,
                    remainingSeconds = remainingSeconds,
                    revealed = revealed,
                    selectedOptionId = selectedOptionId,
                    onOptionSelect = { viewModel.select(it) },
                    onNext = { viewModel.next() },
                    onAbort = { viewModel.close() },
                    isTimed = (test?.questionTimeSeconds ?: 0) > 0,
                    assemblyAttempt = assemblyAttempt,
                    onSubmitAssembly = { viewModel.submitAssembly() }
                )
            }
        }
    }
}

@Composable
fun TestPicker(viewModel: TestViewModel, tests: List<Test>, appViewModel: AppViewModel) {
    val selectedCourseId by appViewModel.selectedCourseId.collectAsState()
    val showBackToLecture = selectedCourseId != null && selectedCourseId != AppViewModel.ALL_RHYTHMS_ID

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.setMode(TestingMode.Choice) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.test_select_title), style = MaterialTheme.typography.headlineMedium)
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (tests.isEmpty()) {
            Text(stringResource(R.string.test_empty))
        } else {
            tests.forEach { test ->
                Button(
                    onClick = { viewModel.start(test) },
                    modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 4.dp)
                ) {
                    Text(test.title)
                }
            }
        }

        if (showBackToLecture) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    appViewModel.setPreserveCourseSelection(true)
                    appViewModel.operatingModes.find { it.id == OperatingMode.Teaching }?.let {
                        appViewModel.updateOperatingMode(it)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text(stringResource(R.string.back_to_lecture))
            }
        }
    }
}

@Composable
fun TestResultSummary(viewModel: TestViewModel, appViewModel: AppViewModel) {
    val correctCount by viewModel.correctCount.collectAsState()
    val test by viewModel.activeTest.collectAsState()
    val totalCount = test?.questions?.size ?: 0
    val selectedCourseId by appViewModel.selectedCourseId.collectAsState()
    val showBackToLecture = selectedCourseId != null && selectedCourseId != AppViewModel.ALL_RHYTHMS_ID

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.test_result_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.test_result_score_format, correctCount, totalCount),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row {
            Button(onClick = { viewModel.restart() }) {
                Text(stringResource(R.string.test_restart))
            }
            if (showBackToLecture) {
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = {
                    appViewModel.setPreserveCourseSelection(true)
                    appViewModel.operatingModes.find { it.id == OperatingMode.Teaching }?.let {
                        appViewModel.updateOperatingMode(it)
                    }
                }) {
                    Text(stringResource(R.string.back_to_lecture))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { viewModel.close() }) {
                Text(stringResource(R.string.cd_close))
            }
        }
    }
}
