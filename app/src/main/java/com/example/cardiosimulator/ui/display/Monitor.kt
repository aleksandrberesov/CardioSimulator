package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.panels.MonitorControlPanel
import com.example.cardiosimulator.ui.screens.SettingsDialog
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import kotlin.math.ceil

private val LEAD_ORDER = listOf(
    Lead.I, Lead.II, Lead.III,
    Lead.aVR, Lead.aVL, Lead.aVF,
    Lead.V1, Lead.V2, Lead.V3, Lead.V4, Lead.V5, Lead.V6,
)

@Composable
fun Monitor(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    monitorViewModel: MonitorViewModel = viewModel(),
    waveformsByLead: Map<Lead, Points>? = null,
    points: Points = Points(emptyList()),
){
    val mode by monitorViewModel.monitorMode.collectAsState()
    val showSettings = remember { mutableStateOf(false) }

    var scale by remember { mutableStateOf(mode.scale) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(mode.scale) {
        if (kotlin.math.abs(scale - mode.scale) > 0.001f) {
            scale = mode.scale
            offset = Offset.Zero
        }
    }

    if (showSettings.value) {
        SettingsDialog(
            monitorViewModel = monitorViewModel,
            appViewModel = appViewModel,
            onDismiss = { showSettings.value = false }
        )
    }

    val columns = when (mode.seriesScheme) {
        SeriesScheme.OneColumn -> 1
        SeriesScheme.TwoColumn -> 2
        SeriesScheme.Grid -> 4
    }

    val rows = ceil(mode.count.toFloat() / columns).toInt()

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .weight(10f)
                .clipToBounds()
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()

            val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                if (newScale != scale) {
                    scale = newScale
                    monitorViewModel.setScale(newScale)
                }

                val maxX = (containerWidth * (scale - 1)) / 2
                val maxY = (containerHeight * (scale - 1)) / 2

                val newOffset = offset + offsetChange
                offset = Offset(
                    x = newOffset.x.coerceIn(-maxX, maxX),
                    y = newOffset.y.coerceIn(-maxY, maxY)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = state)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .ekgGrid(mode.gridScheme)
            ) {
                repeat(rows) { rowIndex ->
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(columns) { colIndex ->
                            val itemIndex = rowIndex * columns + colIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (itemIndex < mode.count) {
                                    val lead = LEAD_ORDER.getOrNull(itemIndex)
                                    val leadPoints = lead?.let { waveformsByLead?.get(it) }
                                        ?.takeIf { it.values.size >= 2 }
                                        ?: points
                                    Series(
                                        points = leadPoints,
                                        modifier = modifier,
                                        title = lead?.name ?: (itemIndex + 1).toString()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier.weight(1.0f),
            contentAlignment = Alignment.Center
        ) {
            MonitorControlPanel(
                viewModel = monitorViewModel,
                onSettingsClick = { showSettings.value = true }
            )
        }
    }
}

@Preview(name = "1 Column - 12 Leads", showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MonitorOneColumn12Preview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    val vm: MonitorViewModel = viewModel()
    vm.setSeriesCount(12)
    vm.setSeriesScheme(SeriesScheme.OneColumn)
    vm.setGridScheme(GridScheme.Pink)
    CardioSimulatorTheme {
        Monitor(
            points = samplePoints,
            appViewModel = viewModel(),
            monitorViewModel = vm
        )
    }
}

@Preview(name = "2 Columns - 12 Leads", showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MonitorTwoColumn12Preview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    val vm: MonitorViewModel = viewModel()
    vm.setSeriesCount(12)
    vm.setSeriesScheme(SeriesScheme.TwoColumn)
    vm.setGridScheme(GridScheme.BlueGray)
    CardioSimulatorTheme {
        Monitor(
            points = samplePoints,
            appViewModel = viewModel(),
            monitorViewModel = vm
        )
    }
}

@Preview(name = "Grid (4 Col) - 12 Leads", showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MonitorGrid12Preview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    val vm: MonitorViewModel = viewModel()
    vm.setSeriesCount(12)
    vm.setSeriesScheme(SeriesScheme.Grid)
    vm.setGridScheme(GridScheme.Pink)
    CardioSimulatorTheme {
        Monitor(
            points = samplePoints,
            appViewModel = viewModel(),
            monitorViewModel = vm
        )
    }
}
