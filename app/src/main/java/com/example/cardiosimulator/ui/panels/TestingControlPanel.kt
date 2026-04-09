package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.ui.components.Label
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.viewmodels.MainViewModel

@Composable
fun TestingControlPanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onTab2Click: () -> Unit = {},
    onTab3Click: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Tab(
                    text = "                  ",
                    onClick = onTab2Click
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Label(
                    text = "Question 13",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Red,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 24.sp
                )
                Tab(
                    icon = Icons.Default.Pause,
                    onClick = {}
                )
            }
        }
    }
}