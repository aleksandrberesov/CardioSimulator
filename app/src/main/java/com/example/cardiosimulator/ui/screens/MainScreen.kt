package com.example.cardiosimulator.ui.screens
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.DataState
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import androidx.compose.runtime.LaunchedEffect

@Composable
fun MainScreen(viewModel: AppViewModel){
    val selectedMode by viewModel.selectedOperatingMode.collectAsState()
    val dataState by viewModel.dataState.collectAsState()
    val isDataConfirmed by viewModel.isDataConfirmed.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    val monitorViewModel: MonitorViewModel = viewModel(
        key = selectedMode.id.name,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(prefs = viewModel.prefs) as T
            }
        }
    )

    val rhythmViewModel: RhythmViewModel = viewModel(
        key = selectedMode.id.name + "_rhythm",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RhythmViewModel(ecgRepository = viewModel.ecgRepository!!) as T
            }
        }
    )

    LaunchedEffect(dataState, rhythmViewModel) {
        if (dataState is DataState.Ready) {
            rhythmViewModel.loadData()
        }
    }

    // If the user has not yet picked a data archive, or it's loading/erroring,
    // or they haven't confirmed the summary of the loaded data, show the picker.
    if (!isDataConfirmed ||
        dataState is DataState.NotConfigured ||
        dataState is DataState.Error ||
        dataState is DataState.Loading
    ) {
        DataSourceScreen(viewModel = viewModel, rhythmViewModel = rhythmViewModel, state = dataState)
        return
    }

    if (showSettings) {
        SettingsDialog(
            monitorViewModel = monitorViewModel,
            appViewModel = viewModel,
            onDismiss = { showSettings = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Box(
            modifier = Modifier.weight(2f).topSection(),
            contentAlignment = Alignment.Center
        ) {
            com.example.cardiosimulator.ui.panels.AppControlPanel(
                viewModel = viewModel,
                onSettingsClick = { showSettings = true }
            )
        }
        Box(
            modifier = Modifier.weight(15f).fillMaxWidth()
        ) {
            when (selectedMode.id) {
                OperatingMode.Teaching -> TeachingScreen(
                    viewModel = viewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel
                )
                OperatingMode.Testing -> TestingScreen(
                    viewModel = viewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel
                )
                OperatingMode.Examination -> ExaminationScreen(
                    viewModel = viewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel
                )
                OperatingMode.OSKE -> OSKEScreen(
                    viewModel = viewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel
                )
                OperatingMode.Editor -> EditorScreen(
                    viewModel = viewModel,
                    monitorViewModel = monitorViewModel,
                    rhythmViewModel = rhythmViewModel
                )
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
                return AppViewModel(
                    appState = appBuilder.build()
                ) as T
            }
        }
    )

    CardioSimulatorTheme {
        MainScreen(viewModel = previewViewModel)
    }
}
