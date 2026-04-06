package com.example.cardiosimulator.ui.screens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cardiosimulator.ui.viewmodels.MainViewModel
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.AppControlPanel
import com.example.cardiosimulator.ui.panels.AppModePanel
import com.example.cardiosimulator.ui.panels.RhythmChoosingPanel

@Composable
fun MainScreen(viewModel: MainViewModel){
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).topSection(),
            contentAlignment = Alignment.Center
        ) {
            AppModePanel()
        }
        Box(
            modifier = Modifier.weight(1f).topSection(),
            contentAlignment = Alignment.Center
        ) {
            AppControlPanel()
        }
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

    }
}