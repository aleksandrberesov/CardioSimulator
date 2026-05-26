package com.example.cardiosimulator.ui.screens
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.panels.TopControlPanel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel
import com.example.cardiosimulator.ui.viewmodels.EditorViewModel
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.data.AssetPathologySource
import com.example.cardiosimulator.data.DataSourcePrefs

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel,
    rhythmViewModel: RhythmViewModel,
    editorViewModel: EditorViewModel
) {
    val selectedOperatingMode by appViewModel.selectedOperatingMode.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopControlPanel(
                viewModel = appViewModel,
                monitorViewModel = monitorViewModel,
                onSettingsClick = { showSettings = true }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f)) {
                    when (selectedOperatingMode.id) {
                        OperatingMode.Teaching -> TeachingScreen(appViewModel, monitorViewModel, rhythmViewModel)
                        OperatingMode.Testing -> TestingScreen(appViewModel, monitorViewModel, rhythmViewModel)
                        OperatingMode.Examination -> ExaminationScreen(appViewModel, monitorViewModel, rhythmViewModel)
                        OperatingMode.OSKE -> OSKEScreen(appViewModel, monitorViewModel, rhythmViewModel)
                        OperatingMode.Editor -> EditorScreen(appViewModel, monitorViewModel, rhythmViewModel, editorViewModel)
                        OperatingMode.Comparison -> ComparisonScreen(appViewModel, monitorViewModel, rhythmViewModel)
                        OperatingMode.CourseConstructor -> CourseConstructorScreen(appViewModel)
                    }
                }
            }

            if (showSettings) {
                SettingsDialog(
                    monitorViewModel = monitorViewModel,
                    appViewModel = appViewModel,
                    onDismiss = { showSettings = false }
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    val appBuilder = AppBuilder()
        .addMode(OperatingModeModel(OperatingMode.Teaching))
        .addMode(OperatingModeModel(OperatingMode.Testing))
        .addMode(OperatingModeModel(OperatingMode.Examination))
        .addMode(OperatingModeModel(OperatingMode.OSKE))
        .addMode(OperatingModeModel(OperatingMode.Editor))
        .addMode(OperatingModeModel(OperatingMode.Comparison))
        
    val repo = PathologyRepository(AssetPathologySource(context.assets))
    val prefs = DataSourcePrefs(context)
        
    val appViewModel: AppViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(
                    appState = appBuilder.build(),
                    repository = repo,
                    prefs = prefs
                ) as T
            }
        }
    )

    val monitorViewModel: MonitorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(prefs) as T
            }
        }
    )

    val rhythmViewModel: RhythmViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RhythmViewModel(repo, prefs) as T
            }
        }
    )

    val editorViewModel: EditorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(repo, prefs) as T
            }
        }
    )

    CardioSimulatorTheme {
        MainScreen(
            appViewModel = appViewModel,
            monitorViewModel = monitorViewModel,
            rhythmViewModel = rhythmViewModel,
            editorViewModel = editorViewModel
        )
    }
}
