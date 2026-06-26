package com.example.cardiosimulator.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.panels.*
import com.example.cardiosimulator.ui.theme.*
import com.example.cardiosimulator.ui.viewmodels.*

@Composable
fun MainScreen(appViewModel: AppViewModel) {
    val selectedMode by appViewModel.selectedOperatingMode.collectAsState()
    val dataState by appViewModel.dataState.collectAsState()
    val courseDataState by appViewModel.courseDataState.collectAsState()
    val isDataConfirmed by appViewModel.isDataConfirmed.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    val monitorViewModel: MonitorViewModel = viewModel(
        key = selectedMode.id.name,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(
                    mode = selectedMode.id,
                    prefs = appViewModel.prefs
                ) as T
            }
        }
    )

    val rhythmViewModel: RhythmViewModel = viewModel(
        key = selectedMode.id.name + "_rhythm",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RhythmViewModel(
                    repository = appViewModel.repository!!,
                    mode = selectedMode.id,
                    prefs = appViewModel.prefs,
                    appViewModel = appViewModel
                ) as T
            }
        }
    )
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()

    val constructorViewModel: ConstructorViewModel = viewModel(
        key = selectedMode.id.name + "_editor",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConstructorViewModel(
                    repository = appViewModel.repository!!,
                    mode = selectedMode.id,
                    prefs = appViewModel.prefs
                ) as T
            }
        }
    )

    val courseConstructorViewModel: CourseConstructorViewModel = viewModel(
        key = selectedMode.id.name + "_course_editor",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CourseConstructorViewModel(
                    repository = appViewModel.courseRepository!!,
                    mode = selectedMode.id,
                    prefs = appViewModel.prefs
                ) as T
            }
        }
    )

    val courseViewerViewModel: CourseViewerViewModel = viewModel(
        key = selectedMode.id.name + "_course_viewer",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CourseViewerViewModel(
                    repository = appViewModel.courseRepository!!,
                    mode = selectedMode.id,
                    prefs = appViewModel.prefs
                ) as T
            }
        }
    )

    val oskeViewModel: OskeViewModel = viewModel(
        key = selectedMode.id.name + "_oske",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OskeViewModel(
                    repository = appViewModel.oskeRepository!!,
                    resultStore = appViewModel.oskeResultStore!!,
                    pathologyRepository = appViewModel.repository!!
                ) as T
            }
        }
    )

    val testViewModel: TestViewModel = viewModel(
        key = selectedMode.id.name + "_test",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TestViewModel() as T
            }
        }
    )

    val examinationViewModel: ExaminationViewModel = viewModel(
        key = selectedMode.id.name + "_exam",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ExaminationViewModel(
                    resultStore = appViewModel.examResultStore!!,
                    bankRepository = appViewModel.questionBankRepository,
                    appContext = appViewModel.appContext
                ) as T
            }
        }
    )

    val testConstructorViewModel: TestConstructorViewModel = viewModel(
        key = selectedMode.id.name + "_test_ctor",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TestConstructorViewModel(
                    repository = appViewModel.testRepository!!,
                    bankRepository = appViewModel.questionBankRepository!!,
                    themeStore = appViewModel.testThemeStore!!
                ) as T
            }
        }
    )

    LaunchedEffect(dataState, rhythmViewModel) {
        if (dataState is DataState.Ready) {
            rhythmViewModel.loadManifest()
        }
    }

    if (!isDataConfirmed) {
        DataSourceScreen(
            appViewModel = appViewModel,
            rhythmViewModel = rhythmViewModel,
            state = dataState,
            courseState = courseDataState
        )
        return
    }

    if (showSettings) {
        SettingsDialog(
            monitorViewModel = monitorViewModel,
            appViewModel = appViewModel,
            onDismiss = { showSettings = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground).systemBarsPadding()) {
        Box(
            modifier = Modifier.weight(2f).topSection(),
            contentAlignment = Alignment.Center
        ) {
            TopControlPanel(
                viewModel = appViewModel,
                monitorViewModel = monitorViewModel,
                rhythmViewModel = rhythmViewModel,
                constructorViewModel = constructorViewModel,
                courseConstructorViewModel = courseConstructorViewModel,
                courseViewerViewModel = courseViewerViewModel,
                onStartStopClick = { isRunning ->
                    if (isRunning) {
                        appViewModel.sendStartCommand(selectedRhythm?.id, selectedRhythm?.titleEn)
                    } else {
                        appViewModel.sendStopCommand()
                    }
                }
            )
        }
        Box(
            modifier = Modifier
                .weight(15f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(PanelBackground, RoundedCornerShape(16.dp))
                .border(1.dp, ControlBorder, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ) {
            when (selectedMode.id) {
                OperatingMode.Teaching -> TeachingScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    courseViewerViewModel = courseViewerViewModel,
                )
                OperatingMode.Testing -> TestingScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    testViewModel = testViewModel,
                )
                OperatingMode.Examination -> ExaminationScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    examinationViewModel = examinationViewModel,
                    testRepository = appViewModel.testRepository!!
                )
                OperatingMode.TestConstructor -> TestConstructorScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    testConstructorViewModel = testConstructorViewModel
                )
                OperatingMode.OSKE -> OSKEScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    oskeViewModel = oskeViewModel,
                )
                OperatingMode.OSKEConstructor -> OskeConstructorScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    oskeViewModel = oskeViewModel,
                )
                OperatingMode.Constructor -> ConstructorScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    constructorViewModel = constructorViewModel,
                )
                OperatingMode.CourseConstructor -> CourseConstructorScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                    courseConstructorViewModel = courseConstructorViewModel,
                )
            }
        }
        Box(
            modifier = Modifier.weight(2f).bottomSection(),
            contentAlignment = Alignment.Center
        ) {
            BottomControlPanel(
                onSettingsClick = { showSettings = true }
            ) {
                when (selectedMode.id) {
                    OperatingMode.Teaching -> {
                        val appSelectedCourseId by appViewModel.selectedCourseId.collectAsState()
                        val showMonitorOverlay by appViewModel.showMonitorOverlay.collectAsState()
                        val isAllRhythms = appSelectedCourseId == AppViewModel.ALL_RHYTHMS_ID || appSelectedCourseId == null
                        if (isAllRhythms || showMonitorOverlay) {
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
                    }
                    OperatingMode.Constructor -> {
                        ConstructorControlPanel(
                            constructorViewModel = constructorViewModel,
                            monitorViewModel = monitorViewModel
                        )
                    }
                    OperatingMode.CourseConstructor -> {
                        CourseConstructorControlPanel(
                            appViewModel = appViewModel,
                            courseConstructorViewModel = courseConstructorViewModel
                        )
                    }
                    OperatingMode.OSKEConstructor -> {
                        OskeConstructorControlPanel(
                            oskeViewModel = oskeViewModel,
                            rhythmViewModel = rhythmViewModel
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@SuppressLint("LocalContextResourcesRead")
@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun MainScreenPreview() {
    val appBuilder = AppBuilder()
    OperatingMode.entries.forEach { mode ->
        appBuilder.addMode(OperatingModeModel(mode))
    }

    val previewViewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(appState = appBuilder.build()) as T
            }
        }
    )

    CardioSimulatorTheme {
        MainScreen(appViewModel = previewViewModel)
    }
}
