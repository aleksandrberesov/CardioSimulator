package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.RhythmChoosingPanel
import com.example.cardiosimulator.ui.viewmodels.MainViewModel

@Composable
fun OSKEScreen(viewModel: MainViewModel){
    Row(
        modifier = Modifier.fillMaxSize().systemBarsPadding()
    ) {
        Box(
            modifier = Modifier.weight(1f).middleSectionLeft(),
            contentAlignment = Alignment.TopStart
        ) {

        }
        Box(
            modifier = Modifier.weight(4f).middleSectionCenter(),
            contentAlignment = Alignment.Center
        ) {

        }
    }
}