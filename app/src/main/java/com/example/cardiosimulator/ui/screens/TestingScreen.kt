package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    LaunchedEffect(pendingTest) {
        pendingTest?.let {
            testViewModel.start(it)
            appViewModel.setPendingTest(null)
        }
    }

    if (finished) {
        TestResultSummary(testViewModel, appViewModel)
    } else if (activeTest == null) {
        TestPicker(testViewModel, appViewModel.testRepository?.tests() ?: emptyList(), appViewModel)
    } else {
        TestActiveView(testViewModel, monitorViewModel, rhythmViewModel, appViewModel)
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
        Text(stringResource(R.string.test_select_title), style = MaterialTheme.typography.headlineMedium)
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
