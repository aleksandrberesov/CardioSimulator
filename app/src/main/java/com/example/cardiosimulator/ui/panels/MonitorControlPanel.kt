package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
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
    onSettingsClick: () -> Unit = {}
) {
    val monitorMode by viewModel.monitorMode.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Group 1: Menus (Count, Scheme, Speed, Scale)
        Box {
            var countMenuExpanded by remember { mutableStateOf(false) }
            Tab(
                text = "${monitorMode.count}x",
                onClick = { countMenuExpanded = true }
            )
            DropdownMenu(
                expanded = countMenuExpanded,
                onDismissRequest = { countMenuExpanded = false }
            ) {
                listOf(1, 6, 12).forEach { count ->
                    DropdownMenuItem(
                        text = { Text("${count}x") },
                        onClick = {
                            viewModel.setSeriesCount(count)
                            countMenuExpanded = false
                        }
                    )
                }
            }
        }

        Box {
            var schemeMenuExpanded by remember { mutableStateOf(false) }
            Tab(
                text = when (monitorMode.seriesScheme) {
                    SeriesScheme.OneColumn -> "1 Col"
                    SeriesScheme.TwoColumn -> "2 Cols"
                    SeriesScheme.Grid -> "Grid"
                },
                onClick = { schemeMenuExpanded = true }
            )
            DropdownMenu(
                expanded = schemeMenuExpanded,
                onDismissRequest = { schemeMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("1 Column") },
                    onClick = {
                        viewModel.setSeriesScheme(SeriesScheme.OneColumn)
                        schemeMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("2 Columns") },
                    onClick = {
                        viewModel.setSeriesScheme(SeriesScheme.TwoColumn)
                        schemeMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Grid") },
                    onClick = {
                        viewModel.setSeriesScheme(SeriesScheme.Grid)
                        schemeMenuExpanded = false
                    }
                )
            }
        }

        Box {
            var speedMenuExpanded by remember { mutableStateOf(false) }
            Tab(
                text = "${monitorMode.speed}",
                subText = "mm/s",
                onClick = { speedMenuExpanded = true }
            )
            DropdownMenu(
                expanded = speedMenuExpanded,
                onDismissRequest = { speedMenuExpanded = false }
            ) {
                listOf(25, 50).forEach { speed ->
                    DropdownMenuItem(
                        text = { Text("$speed mm/s") },
                        onClick = {
                            viewModel.setSpeed(speed)
                            speedMenuExpanded = false
                        }
                    )
                }
            }
        }

        Box {
            var scaleMenuExpanded by remember { mutableStateOf(false) }
            Tab(
                text = "${monitorMode.scale}%",
                onClick = { scaleMenuExpanded = true }
            )
            DropdownMenu(
                expanded = scaleMenuExpanded,
                onDismissRequest = { scaleMenuExpanded = false }
            ) {
                listOf(100, 75, 50, 25).forEach { scaleOption ->
                    DropdownMenuItem(
                        text = { Text("$scaleOption%") },
                        onClick = {
                            viewModel.setScale(scaleOption)
                            scaleMenuExpanded = false
                        }
                    )
                }
            }
        }

        ControlPanelDivider()

        // Group 2: Mode Toggles
        Tab(text = "Electrodes", onClick = { })
        Tab(text = "EMD/EBPA", onClick = { })
        Tab(text = "Muscle", onClick = { })

        ControlPanelDivider()

        // Group 3: Status Indicators
        Label(
            text = "EOS",
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.Red,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 20.sp,
            borderWidth = 1.dp,
            borderColor = androidx.compose.ui.graphics.Color.Black,
            cornerRadius = 4.dp
        )
        Label(
            text = "HR 160",
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            backgroundColor = androidx.compose.ui.graphics.Color.Black,
            fontSize = 20.sp,
            cornerRadius = 4.dp
        )
        Tab(text = "Tips", onClick = onTipsClick)

        ControlPanelDivider()

        // Group 4: Main Actions
        Tab(
            icon = Icons.Default.Straighten,
            iconModifier = Modifier.rotate(-45f),
            onClick = onRulerClick
        )
        Tab(
            icon = Icons.Default.Pause,
            onClick = onPauseClick
        )
        Tab(
            icon = Icons.Default.Settings,
            onClick = onSettingsClick,
            borderWidth = 0.dp
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
