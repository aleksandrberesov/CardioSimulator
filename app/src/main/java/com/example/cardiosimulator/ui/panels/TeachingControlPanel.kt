package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

val educationPrograms = listOf(
    "Program 1", "Program 2", "Program 3", "Program 4", "Program 5", "Program 6",
)

@Composable
fun TeachingControlPanel(
    modifier: Modifier = Modifier,
    monitorViewModel: MonitorViewModel = viewModel(),
    onStartStopClick: (Boolean) -> Unit = {},
) {
    val monitorMode by monitorViewModel.monitorMode.collectAsState()

    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var expanded by remember { mutableStateOf(false) }
        var selectedProgram by remember { mutableStateOf(educationPrograms[0]) }

        Tab(
            text = selectedProgram,
            onClick = { expanded = true },
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            educationPrograms.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        selectedProgram = item
                        expanded = false
                    }
                )
            }
        }

        Tab(
            icon = if (monitorMode.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            iconContentDescription = if (monitorMode.isRunning) stringResource(R.string.cd_stop) else stringResource(R.string.cd_start),
            onClick = {
                val newState = !monitorMode.isRunning
                monitorViewModel.setIsRunning(newState)
                onStartStopClick(newState)
            },
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@SuppressLint("LocalContextResourcesRead")
@Preview(showBackground = true, widthDp = 1000)
@Composable
fun TeachingControlPanelPreview() {
    CardioSimulatorTheme {
        TeachingControlPanel()
    }
}