package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.components.ControlPanelDivider
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel
import com.example.cardiosimulator.ui.viewmodels.EditingAlgorithm
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun ConstructorControlPanel(
    constructorViewModel: ConstructorViewModel,
    monitorViewModel: MonitorViewModel,
    modifier: Modifier = Modifier,
) {
    val selectedIndex by constructorViewModel.selectedIndex.collectAsState()
    val targetFile by constructorViewModel.targetFile
    val focusedLead by constructorViewModel.focusedLead.collectAsState()
    val monitorMode by monitorViewModel.monitorMode.collectAsState()
    val editingAlgorithm by constructorViewModel.editingAlgorithm.collectAsState()
    val editingRadius by constructorViewModel.editingRadius.collectAsState()

    val showSpeedDialog = remember { mutableStateOf(value = false) }
    val showTimeDialog = remember { mutableStateOf(value = false) }
    val showAdcDialog = remember { mutableStateOf(value = false) }
    val showSmoothingDialog = remember { mutableStateOf(value = false) }
    val showLibraryDialog = remember { mutableStateOf(value = false) }

    val samples = targetFile?.leads?.get(focusedLead)?.samples
    val currentAdc = if (samples != null && (selectedIndex in samples.indices)) samples[selectedIndex] else 0
    val currentTimeMs = if (samples != null && (selectedIndex in samples.indices))
        (selectedIndex * 1000f / monitorMode.calibration.sampleRateHz).toInt()
    else 0

    if (showSpeedDialog.value) {
        var speedText by remember { mutableStateOf(value = if (monitorMode.speed % 1 == 0f) monitorMode.speed.toInt().toString() else monitorMode.speed.toString()) }
        AlertDialog(
            onDismissRequest = { showSpeedDialog.value = false },
            title = { Text(stringResource(R.string.monitor_speed_title)) },
            text = {
                TextField(
                    value = speedText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '.' }) speedText = newValue
                    },
                    label = { Text(stringResource(R.string.monitor_speed_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        speedText.toFloatOrNull()?.let { monitorViewModel.setSpeed(it) }
                        showSpeedDialog.value = false
                    }
                ) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSpeedDialog.value = false }
                ) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    if (showTimeDialog.value) {
        var timeText by remember { mutableStateOf(value = currentTimeMs.toString()) }
        AlertDialog(
            onDismissRequest = { showTimeDialog.value = false },
            title = { Text(stringResource(R.string.constructor_set_time_title)) },
            text = {
                TextField(
                    value = timeText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) timeText = newValue
                    },
                    label = { Text(stringResource(R.string.constructor_time_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        timeText.toIntOrNull()?.let { ms ->
                            val index = (ms * monitorMode.calibration.sampleRateHz / 1000f).toInt()
                            constructorViewModel.selectIndex(index)
                        }
                        showTimeDialog.value = false
                    }
                ) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimeDialog.value = false }
                ) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    if (showAdcDialog.value) {
        var adcText by remember { mutableStateOf(value = currentAdc.toString()) }
        AlertDialog(
            onDismissRequest = { showAdcDialog.value = false },
            title = { Text(stringResource(R.string.constructor_set_adc_title)) },
            text = {
                TextField(
                    value = adcText,
                    onValueChange = { newValue ->
                        // Raw ADC is 0..2048; only accept non-negative digits.
                        if (newValue.all { it.isDigit() }) {
                            adcText = newValue
                        }
                    },
                    label = { Text(stringResource(R.string.constructor_adc_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        adcText.toIntOrNull()?.let { value ->
                            constructorViewModel.setSample(focusedLead, selectedIndex, value)
                        }
                        showAdcDialog.value = false
                    }
                ) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAdcDialog.value = false }
                ) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    if (showSmoothingDialog.value) {
        var selectedAlgorithm by remember { mutableStateOf(value = editingAlgorithm) }
        var widthText by remember { mutableStateOf(value = editingRadius.toString()) }
        AlertDialog(
            onDismissRequest = { showSmoothingDialog.value = false },
            title = { Text(stringResource(R.string.constructor_smoothing_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.constructor_smoothing_algorithm_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    EditingAlgorithm.entries.forEach { algo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAlgorithm = algo }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAlgorithm == algo,
                                onClick = { selectedAlgorithm = algo }
                            )
                            Text(text = algo.name)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextField(
                        value = widthText,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) widthText = newValue
                        },
                        label = { Text(stringResource(R.string.constructor_smoothing_width_label)) },
                        suffix = { Text(stringResource(R.string.constructor_smoothing_width_unit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        constructorViewModel.setEditingAlgorithm(selectedAlgorithm)
                        widthText.toIntOrNull()?.let { constructorViewModel.setEditingRadius(it) }
                        showSmoothingDialog.value = false
                    }
                ) {
                    Text(stringResource(R.string.constructor_rename_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSmoothingDialog.value = false }
                ) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    if (showLibraryDialog.value) {
        AlertDialog(
            onDismissRequest = { showLibraryDialog.value = false },
            title = { Text("ECG Element Library") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { constructorViewModel.insertP(); showLibraryDialog.value = false }) {
                        Text("Insert P-wave")
                    }
                    TextButton(onClick = { constructorViewModel.insertQRS(); showLibraryDialog.value = false }) {
                        Text("Insert QRS-complex")
                    }
                    TextButton(onClick = { constructorViewModel.insertT(); showLibraryDialog.value = false }) {
                        Text("Insert T-wave")
                    }
                    TextButton(onClick = { constructorViewModel.insertFullCycle(); showLibraryDialog.value = false }) {
                        Text("Insert Normal Cycle (P-QRS-T)")
                    }
                    TextButton(onClick = { constructorViewModel.insertBaseline(); showLibraryDialog.value = false }) {
                        Text("Insert Baseline (100ms)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLibraryDialog.value = false }) {
                    Text(stringResource(R.string.constructor_rename_cancel))
                }
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Info Display
        val adcDisplayValue = if (samples != null && selectedIndex in samples.indices) currentAdc.toString() else "-"
        val timeDisplayMs = if (samples != null && selectedIndex in samples.indices)
            stringResource(R.string.constructor_time_format, currentTimeMs)
        else "-"

        // Point Selection
        Row(
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Tab(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                onClick = { constructorViewModel.selectPrevious() },
                isRepeatable = true,
                modifier = Modifier.weight(1f)
            )
            Tab(
                text = timeDisplayMs,
                onClick = { showTimeDialog.value = true },
                modifier = Modifier.weight(1.5f)
            )
            Tab(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                onClick = { constructorViewModel.selectNext() },
                isRepeatable = true,
                modifier = Modifier.weight(1f)
            )
        }

        ControlPanelDivider()

        // Point Adjustment
        Row(
            modifier = Modifier.weight(2.0f).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Tab(
                icon = Icons.Default.KeyboardArrowDown,
                onClick = { constructorViewModel.moveSelectedDown() },
                isRepeatable = true,
                modifier = Modifier.weight(1f)
            )
            Tab(
                text = stringResource(R.string.constructor_adc_format, adcDisplayValue),
                onClick = { showAdcDialog.value = true },
                modifier = Modifier.weight(1.5f)
            )
            Tab(
                text = editingAlgorithm.name,
                onClick = { showSmoothingDialog.value = true },
                modifier = Modifier.weight(1.5f)
            )
            Tab(
                icon = Icons.Default.KeyboardArrowUp,
                onClick = { constructorViewModel.moveSelectedUp() },
                isRepeatable = true,
                modifier = Modifier.weight(1f)
            )
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
                isRepeatable = true,
                modifier = Modifier.weight(1f)
            )
            val formattedSpeed = if (monitorMode.speed % 1 == 0f) monitorMode.speed.toInt().toString() else monitorMode.speed.toString()
            Tab(
                text = formattedSpeed,
                subText = stringResource(R.string.monitor_speed_unit),
                onClick = { showSpeedDialog.value = true },
                modifier = Modifier.weight(1.5f)
            )
            Tab(
                icon = Icons.Default.Add,
                onClick = { monitorViewModel.setSpeed(monitorMode.speed + 1) },
                isRepeatable = true,
                modifier = Modifier.weight(1f)
            )
        }

        ControlPanelDivider()

        Tab(
            icon = Icons.Default.LibraryAdd,
            iconContentDescription = "Library",
            onClick = { showLibraryDialog.value = true },
            modifier = Modifier.weight(0.5f)
        )

        ControlPanelDivider()

        Tab(
            icon = if (monitorMode.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            iconContentDescription = if (monitorMode.isRunning) stringResource(R.string.cd_stop) else stringResource(R.string.cd_start),
            onClick = { monitorViewModel.setIsRunning(!monitorMode.isRunning) },
            modifier = Modifier.weight(0.5f)
        )
    }
}
