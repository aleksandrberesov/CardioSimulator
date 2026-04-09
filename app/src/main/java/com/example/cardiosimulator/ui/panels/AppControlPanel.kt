package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.screens.ExaminationScreen
import com.example.cardiosimulator.ui.screens.OSKEScreen
import com.example.cardiosimulator.ui.screens.TeachingScreen
import com.example.cardiosimulator.ui.screens.TestingScreen
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MainViewModel

@Composable
fun AppControlPanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val operatingModes = viewModel.operatingModes
    val selectedOperatingMode by viewModel.selectedOperatingMode.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
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
                Box(
                    modifier = Modifier.weight(2f).fillMaxWidth()
                ) {
                    Tab(
                        text = selectedOperatingMode.title,
                        onClick = { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        operatingModes.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.title) },
                                onClick = {
                                    viewModel.updateOperatingMode(item)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(2f).fillMaxWidth()
                ){
                    when (selectedOperatingMode.title) {
                        "Teaching" -> TeachingControlPanel(viewModel = viewModel)
                        "Testing" -> TestingControlPanel(viewModel = viewModel)
                        "Examination" -> {}
                        "OSKE" -> {}
                    }
                }
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
                appState = com.example.cardiosimulator.domain.AppBuilder()
                    .addMode(OperatingModeModel(title = "Test", description = ""))
                    .build(),
                repository = com.example.cardiosimulator.data.Points.fromResources(context)
            )
        )
    }
}
