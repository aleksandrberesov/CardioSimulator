package com.example.cardiosimulator.ui.screens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppStateModel
import com.example.cardiosimulator.ui.viewmodels.MainViewModel
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.AppControlPanel
import com.example.cardiosimulator.ui.panels.RhythmChoosingPanel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme

@Composable
fun MainScreen(viewModel: MainViewModel){
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).topSection(),
            contentAlignment = Alignment.Center
        ) {
            AppControlPanel(viewModel = viewModel)
        }
        if (isLandscape) {
            Row(modifier = Modifier.weight(10f).fillMaxWidth()) {
                Box(
                    modifier = Modifier.weight(1f).middleSectionLeft(),
                    contentAlignment = Alignment.TopStart
                ) {
                    RhythmChoosingPanel()
                }
                Box(
                    modifier = Modifier.weight(4f).middleSectionCenter(),
                    contentAlignment = Alignment.Center
                ) {
                    Monitor(points = viewModel.points, count = 12)
                }
            }
        } else {
            Column(modifier = Modifier.weight(10f).fillMaxWidth()) {
                Box(
                    modifier = Modifier.weight(1f).middleSectionLeft(),
                    contentAlignment = Alignment.TopStart
                ) {
                    RhythmChoosingPanel()
                }
                Box(
                    modifier = Modifier.weight(2f).middleSectionCenter(),
                    contentAlignment = Alignment.Center
                ) {
                    Monitor(points = viewModel.points, count = 12)
                }
            }
        }

    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 1000)
@Composable
fun MainScreenPreview() {
    val context = LocalContext.current
    CardioSimulatorTheme {
        MainScreen(
            viewModel = MainViewModel(
                appState = AppStateModel(initialOperatingMode = "Test"),
                repository = Points.fromResources(context)
            )
        )
    }
}