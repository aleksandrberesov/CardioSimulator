package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MainViewModel

@Composable
fun AppControlPanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onTab2Click: () -> Unit = {},
    onTab3Click: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val operatingModes = viewModel.operatingModes
    val selectedOperatingMode by viewModel.selectedOperatingMode.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    Tab(
                        text = selectedOperatingMode,
                        onClick = { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        operatingModes.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    viewModel.updateOperatingMode(item)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Tab(
                    text = "Education",
                    onClick = onTab2Click
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Tab(
                    text = "Self-education",
                    onClick = onTab3Click
                )
                Tab(
                    text = "Tips",
                    onClick = onTab3Click
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 100)
@Composable
fun AppControlPanelPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    CardioSimulatorTheme {
        AppControlPanel(
            viewModel = MainViewModel(
                appState = com.example.cardiosimulator.domain.AppStateModel(initialOperatingMode = "Preview"),
                repository = com.example.cardiosimulator.data.Points.fromResources(context)
            )
        )
    }
}
