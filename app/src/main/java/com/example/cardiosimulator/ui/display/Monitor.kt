package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import kotlinx.coroutines.isActive
import kotlin.math.ceil



@Composable
fun Monitor(
    modifier: Modifier = Modifier,
    monitorViewModel: MonitorViewModel,
    staticGrid: Boolean = false,
    showGridBackground: Boolean = true,
    showGridLines: Boolean = true,
    gesturesEnabled: Boolean = true,
    backgroundContent: @Composable () -> Unit = {},
    content: @Composable ColumnScope.(rows: Int, columns: Int, xOffsetPx: Float, scheme: GridScheme) -> Unit
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
        SeriesScheme.ThreeByFour -> 4
        SeriesScheme.Grid -> 4
    }

    val rows = if (mode.count > 0) ceil(mode.count.toFloat() / maxColumns).toInt() else 0
    val columns = if (rows > 0) ceil(mode.count.toFloat() / rows).toInt() else 1

    val density = LocalDensity.current
    // 1 mm = 160/25.4 dp on Android's mdpi reference; convert to px via display density.
    // displayScale is a global shrink/zoom factor so the whole picture (grid + trace
    // + cal pulse) fits the monitor without breaking the mm-based relationships.
    val pxPerMm = density.density * (160f / 25.4f) * mode.displayScale
    val pixelScale = remember(pxPerMm, mode.speed, mode.calibration, scale) {
        PixelScale(
            pxPerMm = pxPerMm,
            paperSpeedMmPerSec = mode.speed,
            gainZoomY = 1.0f,
            cal = mode.calibration,
            zoom = scale
        )
    }

    val timeState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(mode.isCompareMode, mode.gridScheme, mode.seriesScheme, mode.comparisonTargets, mode.count) {
        if (mode.isCompareMode) {
            timeState.floatValue = 0f
        }
    }

    LaunchedEffect(mode.isRunning) {
        if (mode.isRunning) {
            var lastTime = 0L
            while (isActive) {
                withFrameNanos { frameTime ->
                    if (lastTime != 0L) {
                        val deltaMs = (frameTime - lastTime) / 1_000_000f
                        timeState.floatValue += deltaMs
                    }
                    lastTime = frameTime
                }
            }
        }
    }
    val timeMillis = timeState.floatValue
    val xOffsetPx = -(timeMillis / 1000f) * pixelScale.pxPerSec

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .clipToBounds()
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val state = rememberTransformableState { _, zoomChange, offsetChange, _ ->
            if (gesturesEnabled) {
                scale = (scale * zoomChange).coerceIn(1f, 5f)

                val maxX = (containerWidth * (scale - 1)) / 2
                val maxY = (containerHeight * (scale - 1)) / 2

                val newOffset = offset + offsetChange
                offset = Offset(
                    x = newOffset.x.coerceIn(-maxX, maxX),
                    y = newOffset.y.coerceIn(-maxY, maxY)
                )
            }
        }

        LaunchedEffect(scale) {
            kotlinx.coroutines.delay(500)
            monitorViewModel.setScale(scale)
        }

        CompositionLocalProvider(LocalPixelScale provides pixelScale) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (gesturesEnabled) Modifier.transformable(state = state) else Modifier)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                backgroundContent()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .ekgGrid(
                            scheme = if (showGridLines) mode.gridScheme else GridScheme.Blank,
                            xOffsetPx = if (staticGrid) 0f else xOffsetPx,
                            showBackground = showGridBackground
                        )
                ) {
                    content(rows, columns, xOffsetPx, mode.gridScheme)
                }
            }

            // Scale label (fixed in the bottom-right corner of the monitor)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                val speedStr = if (mode.speed == mode.speed.toInt().toFloat()) mode.speed.toInt().toString() else mode.speed.toString()
                val gainStr = if (mode.calibration.gainMmPerMv == mode.calibration.gainMmPerMv.toInt().toFloat()) mode.calibration.gainMmPerMv.toInt().toString() else mode.calibration.gainMmPerMv.toString()

                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.monitor_scale_label_format, speedStr, gainStr),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
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
    val vm: MonitorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(mode = OperatingMode.Teaching) as T
            }
        }
    )
    vm.setSeriesCount(12)
    vm.setSeriesScheme(SeriesScheme.OneColumn)
    vm.setGridScheme(GridScheme.Pink)
    val mode by vm.monitorMode.collectAsState()
    CardioSimulatorTheme {
        Monitor(
            monitorViewModel = vm
        ) { rows, columns, xOffset, scheme ->
            LeadsGrid(
                rows = rows,
                columns = columns,
                itemCount = mode.count,
            ) { _, lead ->
                Lead(
                    points = samplePoints,
                    title = lead?.name ?: "",
                    xOffsetPx = xOffset,
                    gridScheme = scheme
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
    val vm: MonitorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(mode = OperatingMode.Teaching) as T
            }
        }
    )
    vm.setSeriesCount(12)
    vm.setSeriesScheme(SeriesScheme.TwoColumn)
    vm.setGridScheme(GridScheme.BlueGray)
    val mode by vm.monitorMode.collectAsState()
    CardioSimulatorTheme {
        Monitor(
            monitorViewModel = vm
        ) { rows, columns, xOffset, scheme ->
            LeadsGrid(
                rows = rows,
                columns = columns,
                itemCount = mode.count,
            ) { _, lead ->
                Lead(
                    points = samplePoints,
                    title = lead?.name ?: "",
                    xOffsetPx = xOffset,
                    gridScheme = scheme
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
    val vm: MonitorViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MonitorViewModel(mode = OperatingMode.Teaching) as T
            }
        }
    )
    vm.setSeriesCount(6)
    vm.setSeriesScheme(SeriesScheme.Grid)
    vm.setGridScheme(GridScheme.Pink)
    val mode by vm.monitorMode.collectAsState()
    CardioSimulatorTheme {
        Monitor(
            monitorViewModel = vm
        ) { rows, columns, xOffset, scheme ->
            LeadsGrid(
                rows = rows,
                columns = columns,
                itemCount = mode.count,
            ) { _, lead ->
                Lead(
                    points = samplePoints,
                    title = lead?.name ?: "",
                    xOffsetPx = xOffset,
                    gridScheme = scheme
                )
            }
        }
    }
}
