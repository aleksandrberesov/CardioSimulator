package com.example.cardiosimulator.ui.screens
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.panels.BottomControlPanel
import com.example.cardiosimulator.ui.panels.ConstructorControlPanel
import com.example.cardiosimulator.ui.panels.CourseConstructorControlPanel
import com.example.cardiosimulator.ui.panels.MonitorControlPanel
import com.example.cardiosimulator.ui.panels.TopControlPanel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.DataState
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.CourseViewerViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

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

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Box(
            modifier = Modifier.weight(2f).topSection(),
            contentAlignment = Alignment.Center
        ) {
            TopControlPanel(
                viewModel = appViewModel,
                monitorViewModel = monitorViewModel,
                constructorViewModel = constructorViewModel,
                courseConstructorViewModel = courseConstructorViewModel,
                onStartStopClick = { isRunning ->
                    if (isRunning) {
                        appViewModel.sendStartCommand(selectedRhythm?.id, selectedRhythm?.titleEn)
                    } else {
                        appViewModel.sendStopCommand()
                    }
                }
            )
        }
        Box(modifier = Modifier.weight(15f).fillMaxWidth()) {
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
                )
                OperatingMode.Examination -> ExaminationScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
                )
                OperatingMode.OSKE -> OSKEScreen(
                    appViewModel = appViewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel,
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
                        MonitorControlPanel(
                            viewModel = monitorViewModel,
                            onCompareClick = { monitorViewModel.toggleCompareMode(selectedRhythm?.id) },
                            onStartStopClick = { isRunning ->
                                if (isRunning) {
                                    appViewModel.sendStartCommand(selectedRhythm?.id, selectedRhythm?.titleEn)
                                } else {
                                    appViewModel.sendStopCommand()
                                }
                            },
                        )
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
