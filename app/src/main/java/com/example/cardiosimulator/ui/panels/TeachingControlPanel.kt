package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MainViewModel

val educationPrograms = listOf(
    "Program 1", "Program 2", "Program 3", "Program 4", "Program 5", "Program 6",
)

@Composable
fun TeachingControlPanel(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onTab2Click: () -> Unit = {},
    onTab3Click: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedProgram by remember { mutableStateOf(educationPrograms[0]) }

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
                    text = selectedProgram,
                    onClick = { expanded = true }
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

@SuppressLint("LocalContextResourcesRead")
@Preview(showBackground = true, widthDp = 1000)
@Composable
fun TeachingControlPanelPreview() {
    val context = LocalContext.current
    val appModes = context.resources.getStringArray(R.array.app_modes)
    val appBuilder = AppBuilder()
    appModes.forEach { title ->
        appBuilder.addMode(OperatingModeModel(title, ""))
    }
    CardioSimulatorTheme {
        TeachingControlPanel(
            viewModel = MainViewModel(
                appState = appBuilder.build(),
                repository = Points.fromResources(context)
            )
        )
    }
}