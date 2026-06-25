package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.components.ControlPanelDivider
import com.example.cardiosimulator.ui.components.Label
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun MonitorControlPanel(
    viewModel: MonitorViewModel,
    modifier: Modifier = Modifier,
    onTipsClick: () -> Unit = {},
    onCompareClick: () -> Unit = {},
    onRulerClick: () -> Unit = {},
    onStartStopClick: (Boolean) -> Unit = {},
) {
    val monitorMode by viewModel.monitorMode.collectAsState()
    // ... (rest of the code using viewModel.setShowElectrodes, etc.)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(0.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left section: Count, Scheme, Speed, Scale
        Row(
            modifier = Modifier.weight(4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                var countMenuExpanded by remember { mutableStateOf(false) }
                Tab(
                    text = stringResource(R.string.monitor_count_format, monitorMode.count),
                    onClick = { countMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = countMenuExpanded,
                    onDismissRequest = { countMenuExpanded = false }
                ) {
                    viewModel.availableSeriesCounts.forEach { count ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.monitor_count_format, count)) },
                            onClick = {
                                viewModel.setSeriesCount(count)
                                countMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                var schemeMenuExpanded by remember { mutableStateOf(false) }
                Tab(
                    text = when (monitorMode.seriesScheme) {
                        com.example.cardiosimulator.domain.SeriesScheme.OneColumn -> stringResource(R.string.monitor_columns_one_short)
                        com.example.cardiosimulator.domain.SeriesScheme.TwoColumn -> stringResource(R.string.monitor_columns_two_short)
                        com.example.cardiosimulator.domain.SeriesScheme.ThreeByFour -> stringResource(R.string.monitor_columns_three_by_four_short)
                        com.example.cardiosimulator.domain.SeriesScheme.Grid -> stringResource(R.string.monitor_columns_grid_short)
                    },
                    onClick = { schemeMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = schemeMenuExpanded,
                    onDismissRequest = { schemeMenuExpanded = false }
                ) {
                    viewModel.availableSeriesSchemes.forEach { scheme ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (scheme) {
                                        com.example.cardiosimulator.domain.SeriesScheme.OneColumn -> stringResource(R.string.monitor_columns_one)
                                        com.example.cardiosimulator.domain.SeriesScheme.TwoColumn -> stringResource(R.string.monitor_columns_two)
                                        com.example.cardiosimulator.domain.SeriesScheme.ThreeByFour -> stringResource(R.string.monitor_columns_three_by_four)
                                        com.example.cardiosimulator.domain.SeriesScheme.Grid -> stringResource(R.string.monitor_columns_grid)
                                    }
                                )
                            },
                            onClick = {
                                viewModel.setSeriesScheme(scheme)
                                schemeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                var speedMenuExpanded by remember { mutableStateOf(false) }
                val formattedSpeed = if (monitorMode.speed % 1 == 0f) monitorMode.speed.toInt().toString() else monitorMode.speed.toString()
                Tab(
                    text = formattedSpeed,
                    subText = stringResource(R.string.monitor_speed_unit),
                    onClick = { speedMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { speedMenuExpanded = false }
                ) {
                    viewModel.availableSpeeds.forEach { speed ->
                        val displaySpeed = if (speed % 1 == 0f) speed.toInt().toString() else speed.toString()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.monitor_speed_format, displaySpeed)) },
                            onClick = {
                                viewModel.setSpeed(speed)
                                speedMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                var scaleMenuExpanded by remember { mutableStateOf(false) }
                Tab(
                    text = "${(monitorMode.scale * 100).toInt()}%",
                    onClick = { scaleMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = scaleMenuExpanded,
                    onDismissRequest = { scaleMenuExpanded = false }
                ) {
                    viewModel.availableScales.forEach { scaleOption ->
                        DropdownMenuItem(
                            text = { Text("${(scaleOption * 100).toInt()}%") },
                            onClick = {
                                viewModel.setScale(scaleOption)
                                scaleMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        ControlPanelDivider()

        // Middle-left section: Electrodes, Artifacts, 3D
        Row(
            modifier = Modifier.weight(3.5f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tab(
                text = stringResource(R.string.monitor_electrodes),
                onClick = { viewModel.setShowElectrodes(!monitorMode.showElectrodes) },
                backgroundColor = if (monitorMode.showElectrodes) Color.Blue else Color.Transparent,
                contentColor = if (monitorMode.showElectrodes) Color.White else Color.Black,
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.weight(1.5f)) {
                // ... (artifacts dropdown)
                var artifactsMenuExpanded by remember { mutableStateOf(false) }
                Tab(
                    text = stringResource(R.string.monitor_artifacts),
                    subText = "▾",
                    onClick = { artifactsMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = artifactsMenuExpanded,
                    onDismissRequest = { artifactsMenuExpanded = false }
                ) {
                    com.example.cardiosimulator.domain.EcgArtifact.entries.forEach { artifact ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (artifact) {
                                        com.example.cardiosimulator.domain.EcgArtifact.None -> stringResource(R.string.monitor_artifact_none)
                                        com.example.cardiosimulator.domain.EcgArtifact.Muscle -> stringResource(R.string.monitor_artifact_muscle)
                                        com.example.cardiosimulator.domain.EcgArtifact.Mains -> stringResource(R.string.monitor_artifact_mains)
                                        com.example.cardiosimulator.domain.EcgArtifact.Baseline -> stringResource(R.string.monitor_artifact_baseline)
                                        com.example.cardiosimulator.domain.EcgArtifact.Contact -> stringResource(R.string.monitor_artifact_contact)
                                        com.example.cardiosimulator.domain.EcgArtifact.Motion -> stringResource(R.string.monitor_artifact_motion)
                                    }
                                )
                            },
                            onClick = {
                                viewModel.setArtifact(artifact)
                                artifactsMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Tab(
                text = "3D",
                painter = androidx.compose.ui.res.painterResource(R.drawable.heart_3d),
                onClick = { viewModel.setShow3D(!monitorMode.show3D) },
                backgroundColor = if (monitorMode.show3D) Color.Blue else Color.Transparent,
                contentColor = if (monitorMode.show3D) Color.White else Color.Black,
                modifier = Modifier.weight(1f)
            )
        }

        ControlPanelDivider()

        // Middle-right section: pQRSt, EOS, Tips
        Row(
            modifier = Modifier.weight(3f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tab(
                text = "pQRSt",
                onClick = { viewModel.setShowImpulseLabels(!monitorMode.showImpulseLabels) },
                backgroundColor = if (monitorMode.showImpulseLabels) Color.Blue else Color.Transparent,
                contentColor = if (monitorMode.showImpulseLabels) Color.White else Color.Black,
                modifier = Modifier.weight(1f)
            )
            Tab(
                text = stringResource(R.string.monitor_eos),
                contentColor = Color.Red,
                onClick = { viewModel.setShowEos(!monitorMode.showEos) },
                backgroundColor = if (monitorMode.showEos) Color.Blue.copy(alpha = 0.1f) else Color.Transparent,
                modifier = Modifier.weight(1f)
            )
            Tab(
                text = stringResource(R.string.monitor_tips),
                onClick = { viewModel.setShowTips(!monitorMode.showTips) },
                backgroundColor = if (monitorMode.showTips) Color.Blue.copy(alpha = 0.1f) else Color.Transparent,
                modifier = Modifier.weight(1f)
            )
        }

        if (viewModel.mode == OperatingMode.Teaching) {
            Tab(
                text = stringResource(R.string.monitor_compare),
                onClick = onCompareClick,
                modifier = Modifier.weight(1f),
                backgroundColor = if (monitorMode.isCompareMode) Color.Blue else Color.Transparent,
                contentColor = if (monitorMode.isCompareMode) Color.White else Color.Black
            )
        }

        ControlPanelDivider()

        // Right section: Ruler, Start/Stop
        Row(
            modifier = Modifier.weight(1.6f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Tab(
                icon = Icons.Default.Straighten,
                iconModifier = Modifier.rotate(-45f),
                iconContentDescription = stringResource(R.string.cd_ruler),
                onClick = onRulerClick,
                modifier = Modifier.weight(1f)
            )
            Tab(
                icon = if (monitorMode.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                iconContentDescription = if (monitorMode.isRunning) stringResource(R.string.cd_stop) else stringResource(R.string.cd_start),
                onClick = {
                    val newState = !monitorMode.isRunning
                    viewModel.setIsRunning(newState)
                    onStartStopClick(newState)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 150)
@Composable
fun MonitorControlPanelPreview() {
    val previewViewModel: MonitorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(mode = OperatingMode.Teaching) as T
            }
        }
    )
    CardioSimulatorTheme {
        MonitorControlPanel(viewModel = previewViewModel)
    }
}
