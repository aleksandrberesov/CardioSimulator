package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import kotlin.math.ceil

@Composable
fun Monitor(
    modifier: Modifier = Modifier,
    monitorViewModel: MonitorViewModel = viewModel(),
    /**
     * When non-null, switches to a source-anchored grid sized so that one
     * small grid square == one source `mm` per RP5's
     * `AMax/AValue/10 px-per-mm` rule (Frame.Segments.pas:1142). Used by
     * Editor mode where the user drags anchors and the grid must align
     * with integer source units. Pass null for viewer modes — they get
     * the physical-mm grid.
     */
    sourceAnchoredCalibration: Pair<Int, Int>? = null,
    content: @Composable ColumnScope.(rows: Int, columns: Int) -> Unit
){
    val mode by monitorViewModel.monitorMode.collectAsState()

    var scale by remember { mutableFloatStateOf(mode.scale) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(mode.scale) {
        if (kotlin.math.abs(scale - mode.scale) > 0.001f) {
            scale = mode.scale
            offset = Offset.Zero
        }
    }

    val maxColumns = when (mode.seriesScheme) {
        SeriesScheme.OneColumn -> 1
        SeriesScheme.TwoColumn -> 2
        SeriesScheme.Grid -> 4
    }

    val rows = if (mode.count > 0) ceil(mode.count.toFloat() / maxColumns).toInt() else 0
    val columns = if (rows > 0) ceil(mode.count.toFloat() / rows).toInt() else 1

    val density = LocalDensity.current
    // 1 mm = 160/25.4 dp on Android's mdpi reference; convert to px via display density.
    // displayScale is a global shrink/zoom factor so the whole picture (grid + trace
    // + cal pulse) fits the monitor without breaking the mm-based relationships.
    val pxPerMm = density.density * (160f / 25.4f) * mode.displayScale
    val pixelScale = remember(pxPerMm, mode.speed, mode.scale, mode.calibration, sourceAnchoredCalibration) {
        if (sourceAnchoredCalibration != null) {
            val (aMax, aValue) = sourceAnchoredCalibration
            PixelScale.sourceAnchored(
                aMax = aMax,
                aValue = aValue,
                paperSpeedMmPerSec = mode.speed.toFloat(),
                gainZoomY = mode.scale,
                cal = mode.calibration,
                physicalPxPerMm = pxPerMm,
            )
        } else {
            PixelScale(
                pxPerMm = pxPerMm,
                paperSpeedMmPerSec = mode.speed.toFloat(),
                gainZoomY = mode.scale,
                cal = mode.calibration,
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
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

        CompositionLocalProvider(LocalPixelScale provides pixelScale) {
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
                content(rows, columns)
            }
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
    val mode by vm.monitorMode.collectAsState()
    CardioSimulatorTheme {
        Monitor(
            monitorViewModel = vm
        ) { rows, columns ->
            LeadsGrid(
                rows = rows,
                columns = columns,
                itemCount = mode.count,
            ) { _, lead ->
                Lead(
                    points = samplePoints,
                    title = lead?.name ?: ""
                )
            }
        }
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
    val mode by vm.monitorMode.collectAsState()
    CardioSimulatorTheme {
        Monitor(
            monitorViewModel = vm
        ) { rows, columns ->
            LeadsGrid(
                rows = rows,
                columns = columns,
                itemCount = mode.count,
            ) { _, lead ->
                Lead(
                    points = samplePoints,
                    title = lead?.name ?: ""
                )
            }
        }
    }
}

@Preview(name = "Grid (4 Col) - 6 Leads", showBackground = true, device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MonitorGrid12Preview() {
    val samplePoints = Points(listOf(
        0f, 0.1f, 0.2f, 0.5f, 1f, 0.5f, 0.2f, 0.1f, 0f,
        -0.1f, -0.2f, -0.5f, -1f, -0.5f, -0.2f, -0.1f, 0f
    ))
    val vm: MonitorViewModel = viewModel()
    vm.setSeriesCount(6)
    vm.setSeriesScheme(SeriesScheme.Grid)
    vm.setGridScheme(GridScheme.Pink)
    val mode by vm.monitorMode.collectAsState()
    CardioSimulatorTheme {
        Monitor(
            monitorViewModel = vm
        ) { rows, columns ->
            LeadsGrid(
                rows = rows,
                columns = columns,
                itemCount = mode.count,
            ) { _, lead ->
                Lead(
                    points = samplePoints,
                    title = lead?.name ?: ""
                )
            }
        }
    }
}
