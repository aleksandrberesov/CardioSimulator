package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.displayScaleFactor
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.GridScheme
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt



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
    val showRuler by rememberUpdatedState(mode.showRuler)

    var scale by remember { mutableFloatStateOf(mode.scale) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var caliperA by remember { mutableStateOf<Offset?>(null) }
    var caliperB by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(mode.showRuler, mode.isCompareMode) {
        if (!mode.showRuler || mode.isCompareMode) {
            caliperA = null
            caliperB = null
        }
    }

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
    val pxPerMm = density.density * (160f / 25.4f) * mode.displayScale * displayScaleFactor(mode.count)
    val pixelScale = remember(pxPerMm, mode.speed, mode.calibration, scale, mode.count) {
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

    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .clipToBounds()
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val state = rememberTransformableState { _, zoomChange, offsetChange, _ ->
            if (showRuler) return@rememberTransformableState

            // Zoom is always available (if gestures are not explicitly disabled for the whole monitor)
            val oldScale = scale
            scale = (scale * zoomChange).coerceIn(1f, 5f)

            // Pan is enabled if gesturesEnabled is true (e.g. Select mode or Pan mode)
            if (gesturesEnabled) {
                val maxX = (containerWidth * (scale - 1)) / 2
                val maxY = (containerHeight * (scale - 1)) / 2

                val newOffset = offset + offsetChange
                offset = Offset(
                    x = newOffset.x.coerceIn(-maxX, maxX),
                    y = newOffset.y.coerceIn(-maxY, maxY)
                )
            } else if (scale != oldScale) {
                // If pan is disabled but we zoomed, we still need to keep the offset clamped
                // to the new scale to avoid jumping when pan is re-enabled.
                val maxX = (containerWidth * (scale - 1)) / 2
                val maxY = (containerHeight * (scale - 1)) / 2
                offset = Offset(
                    x = offset.x.coerceIn(-maxX, maxX),
                    y = offset.y.coerceIn(-maxY, maxY)
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
                    .transformable(state = state)
                    .pointerInput(mode.showRuler) {
                        if (mode.showRuler) {
                            detectTapGestures {
                                caliperA = null
                                caliperB = null
                            }
                        }
                    }
                    .pointerInput(mode.showRuler) {
                        if (mode.showRuler) {
                            detectDragGestures(
                                onDragStart = {
                                    caliperA = it
                                    caliperB = it
                                },
                                onDrag = { change, _ ->
                                    caliperB = change.position
                                }
                            )
                        }
                    }
                    .drawWithContent {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        withTransform({
                            scale(scale, scale, pivot = Offset(cx, cy))
                            translate(offset.x, offset.y)
                        }) {
                            this@drawWithContent.drawContent()
                        }
                    }
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

            if (mode.showRuler && !mode.isCompareMode && caliperA != null && caliperB != null) {
                val a = caliperA!!
                val b = caliperB!!
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val color = Color(0xFF1E88E5)
                    val bandColor = color.copy(alpha = 0.15f)
                    val strokeWidth = 1.2.dp.toPx()

                    // Vertical band
                    drawRect(
                        color = bandColor,
                        topLeft = Offset(min(a.x, b.x), 0f),
                        size = androidx.compose.ui.geometry.Size(abs(a.x - b.x), size.height)
                    )

                    // Legs
                    drawLine(color, Offset(a.x, 0f), Offset(a.x, size.height), strokeWidth)
                    drawLine(color, Offset(b.x, 0f), Offset(b.x, size.height), strokeWidth)

                    // Connector
                    drawLine(color, a, b, strokeWidth)

                    // Dots
                    drawCircle(color, 4.dp.toPx(), a)
                    drawCircle(color, 4.dp.toPx(), b)

                    // Readout
                    val dtSec = abs(b.x - a.x) / (pixelScale.pxPerSec * scale)
                    val ms = (dtSec * 1000).roundToInt()
                    val bpm = if (dtSec > 0) (60.0 / dtSec).roundToInt().toString() else "—"
                    val mv = abs(b.y - a.y) / (pixelScale.pxPerMv * scale)

                    val text = "Δt $ms ms   $bpm bpm\nΔ ${"%.2f".format(mv)} mV"
                    val textLayoutResult = textMeasurer.measure(
                        text = text,
                        style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )

                    val boxPadding = 8.dp.toPx()
                    val boxWidth = textLayoutResult.size.width + boxPadding * 2
                    val boxHeight = textLayoutResult.size.height + boxPadding
                    val boxX = ((a.x + b.x) / 2 - boxWidth / 2).coerceIn(0f, size.width - boxWidth)
                    val boxY = (min(a.y, b.y) - boxHeight - 20.dp.toPx()).coerceIn(0f, size.height - boxHeight)

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = Offset(boxX, boxY),
                        size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )

                    drawText(
                        textLayoutResult,
                        topLeft = Offset(boxX + boxPadding, boxY + boxPadding / 2)
                    )
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
    vm.setGridScheme(GridScheme.Yellow)
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
                    gridScheme = scheme,
                    artifacts = mode.artifacts,
                    filterType = mode.filterType,
                    calibration = mode.calibration
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
                    gridScheme = scheme,
                    artifacts = mode.artifacts,
                    filterType = mode.filterType,
                    calibration = mode.calibration
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
    vm.setGridScheme(GridScheme.Yellow)
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
                    gridScheme = scheme,
                    artifacts = mode.artifacts,
                    filterType = mode.filterType,
                    calibration = mode.calibration
                )
            }
        }
    }
}
