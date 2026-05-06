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
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
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
    onRulerClick: () -> Unit = {},
    onPauseClick: () -> Unit = {},
) {
    val monitorMode by viewModel.monitorMode.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
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
                listOf(1, 6, 12).forEach { count ->
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
                    SeriesScheme.OneColumn -> stringResource(R.string.monitor_columns_one_short)
                    SeriesScheme.TwoColumn -> stringResource(R.string.monitor_columns_two_short)
                    SeriesScheme.Grid -> stringResource(R.string.monitor_columns_grid_short)
                },
                onClick = { schemeMenuExpanded = true },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = schemeMenuExpanded,
                onDismissRequest = { schemeMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.monitor_columns_one)) },
                    onClick = {
                        viewModel.setSeriesScheme(SeriesScheme.OneColumn)
                        schemeMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.monitor_columns_two)) },
                    onClick = {
                        viewModel.setSeriesScheme(SeriesScheme.TwoColumn)
                        schemeMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.monitor_columns_grid)) },
                    onClick = {
                        viewModel.setSeriesScheme(SeriesScheme.Grid)
                        schemeMenuExpanded = false
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            var speedMenuExpanded by remember { mutableStateOf(false) }
            Tab(
                text = "${monitorMode.speed}",
                subText = stringResource(R.string.monitor_speed_unit),
                onClick = { speedMenuExpanded = true },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = speedMenuExpanded,
                onDismissRequest = { speedMenuExpanded = false }
            ) {
                listOf(25, 50).forEach { speed ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.monitor_speed_format, speed)) },
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
                listOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f).forEach { scaleOption ->
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

        ControlPanelDivider()

        Tab(
            text = stringResource(R.string.monitor_electrodes),
            onClick = { },
            modifier = Modifier.weight(1.5f)
        )
        Tab(
            text = stringResource(R.string.monitor_emd_ebpa),
            onClick = { },
            modifier = Modifier.weight(1.5f)
        )
        Tab(
            text = stringResource(R.string.monitor_muscle),
            onClick = { },
            modifier = Modifier.weight(1.5f)
        )

        ControlPanelDivider()

        Label(
            text = stringResource(R.string.monitor_eos),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.Red,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 20.sp,
            borderWidth = 1.dp,
            borderColor = androidx.compose.ui.graphics.Color.Black,
            cornerRadius = 4.dp,
            modifier = Modifier.weight(1f)
        )
        Label(
            text = stringResource(R.string.monitor_hr_format, 160),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            backgroundColor = androidx.compose.ui.graphics.Color.Black,
            fontSize = 20.sp,
            cornerRadius = 4.dp,
            modifier = Modifier.weight(1f)
        )
        Tab(
            text = stringResource(R.string.monitor_tips),
            onClick = onTipsClick,
            modifier = Modifier.weight(1f)
        )

        ControlPanelDivider()

        Tab(
            icon = Icons.Default.Straighten,
            iconModifier = Modifier.rotate(-45f),
            iconContentDescription = stringResource(R.string.cd_ruler),
            onClick = onRulerClick,
            modifier = Modifier.weight(0.8f)
        )
        Tab(
            icon = Icons.Default.Pause,
            iconContentDescription = stringResource(R.string.cd_pause),
            onClick = onPauseClick,
            modifier = Modifier.weight(0.8f)
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 150)
@Composable
fun MonitorControlPanelPreview() {
    CardioSimulatorTheme {
        MonitorControlPanel(viewModel = viewModel())
    }
}
