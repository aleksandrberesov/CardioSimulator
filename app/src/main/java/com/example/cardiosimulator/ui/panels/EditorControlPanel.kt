package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.components.ControlPanelDivider
import com.example.cardiosimulator.ui.components.Label
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.viewmodels.EditorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun EditorControlPanel(
    editorViewModel: EditorViewModel,
    monitorViewModel: MonitorViewModel,
    modifier: Modifier = Modifier
) {
    val selectedIndex by editorViewModel.selectedIndex.collectAsState()
    val targetFile by editorViewModel.targetFile
    val focusedLead by editorViewModel.focusedLead.collectAsState()
    val monitorMode by monitorViewModel.monitorMode.collectAsState()

    var showSpeedDialog by remember { mutableStateOf(false) }

    if (showSpeedDialog) {
        var speedText by remember { mutableStateOf(monitorMode.speed.toString()) }
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(R.string.monitor_speed_title)) },
            text = {
                TextField(
                    value = speedText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) speedText = newValue
                    },
                    label = { Text(stringResource(R.string.monitor_speed_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    speedText.toIntOrNull()?.let { monitorViewModel.setSpeed(it) }
                    showSpeedDialog = false
                }) {
                    Text(stringResource(R.string.editor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.editor_rename_cancel))
                }
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Point Selection
        Tab(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            onClick = { editorViewModel.selectPrevious() },
            modifier = Modifier.weight(0.4f)
        )
        Tab(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = { editorViewModel.selectNext() },
            modifier = Modifier.weight(0.4f)
        )

        ControlPanelDivider()

        // Point Adjustment
        Tab(
            icon = Icons.Default.KeyboardArrowUp,
            onClick = { editorViewModel.moveSelectedUp() },
            modifier = Modifier.weight(0.4f)
        )
        Tab(
            icon = Icons.Default.KeyboardArrowDown,
            onClick = { editorViewModel.moveSelectedDown() },
            modifier = Modifier.weight(0.4f)
        )

        ControlPanelDivider()

        // Info Display
        Box(modifier = Modifier.weight(2f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            targetFile?.leads?.get(focusedLead)?.samples?.let { samples ->
                if (selectedIndex in samples.indices) {
                    val adc = samples[selectedIndex]
                    val timeMs = (selectedIndex * 1000f / monitorMode.calibration.sampleRateHz).toInt()
                    
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Label(
                            text = "Pt: $selectedIndex",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Label(
                            text = "ADC: $adc",
                            fontSize = 13.sp,
                            backgroundColor = Color.LightGray.copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f)
                        )
                        Label(
                            text = "${timeMs}ms",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        ControlPanelDivider()

        // Speed Control (Variable)
        Row(
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Tab(
                icon = Icons.Default.Remove,
                onClick = { if (monitorMode.speed > 1) monitorViewModel.setSpeed(monitorMode.speed - 1) },
                modifier = Modifier.weight(1f)
            )
            Tab(
                text = "${monitorMode.speed}",
                subText = "mm/s",
                onClick = { showSpeedDialog = true },
                modifier = Modifier.weight(1.5f)
            )
            Tab(
                icon = Icons.Default.Add,
                onClick = { monitorViewModel.setSpeed(monitorMode.speed + 1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
