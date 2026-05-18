package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.domain.AnchorPoint
import com.example.cardiosimulator.domain.BakedWaveform
import kotlin.math.roundToInt

internal const val HANDLE_HIT_RADIUS_PX = 36f

/**
 * High-level editor component that coordinates waveform dots and draggable
 * anchor handles using a unified projection.
 */
@Composable
fun AnchorChart(
    baked: BakedWaveform,
    anchors: List<AnchorPoint>,
    sampleRateHz: Float,
    samplesPerMv: Float,
    selectedIndex: Int?,
    onAnchorSelected: (Int?) -> Unit,
    onAnchorMoved: (Int, Float, Float) -> Unit,
    onAllAnchorsMoved: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val scale = LocalPixelScale.current
        val stepX = if (sampleRateHz > 0f) scale.pxPerSampleFor(sampleRateHz) else scale.pxPerSample
        val stepY = if (samplesPerMv > 0f) scale.pxPerAdcCountFor(samplesPerMv) else scale.pxPerAdcCount
        val baselineY = with(LocalDensity.current) { maxHeight.toPx() } / 2f

        val dots = remember(baked, stepX, stepY, baselineY) {
            projectDots(baked.samples, baked.originX, stepX, stepY, baselineY)
        }
        val handlePositions = remember(dots, baked.anchorSampleIndex) {
            baked.anchorSampleIndex.map { dots.getOrElse(it) { Offset.Zero } }
        }

        if (dots.size >= 2) {
            Canvas(Modifier.matchParentSize()) {
                drawDots(dots, Color.Black)
            }
        }

        if (handlePositions.isNotEmpty()) {
            AnchorHandleOverlay(
                handlePositions = handlePositions,
                anchors = anchors,
                stepX = stepX,
                stepY = stepY,
                selectedIndex = selectedIndex,
                onAnchorSelected = onAnchorSelected,
                onAnchorMoved = onAnchorMoved,
                onAllAnchorsMoved = onAllAnchorsMoved,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * Transparent overlay that draws draggable anchor handles.
 * Sits on top in a [AnchorChart], handling gestures and handle circles.
 * Receives pre-projected [handlePositions] to ensure they sit exactly on
 * the waveform dots.
 */
@Composable
fun AnchorHandleOverlay(
    handlePositions: List<Offset>,
    anchors: List<AnchorPoint>,
    stepX: Float,
    stepY: Float,
    selectedIndex: Int?,
    onAnchorSelected: (Int?) -> Unit,
    onAnchorMoved: (Int, Float, Float) -> Unit,
    onAllAnchorsMoved: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    handleColor: Color = Color(0xFF1976D2),
    selectedHandleColor: Color = Color(0xFFD32F2F),
) {
    var dragOriginAll by remember { mutableStateOf(false) }

    val currentHandlePositions by rememberUpdatedState(handlePositions)
    val currentAnchors by rememberUpdatedState(anchors)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnAnchorSelected by rememberUpdatedState(onAnchorSelected)
    val currentOnAnchorMoved by rememberUpdatedState(onAnchorMoved)
    val currentOnAllAnchorsMoved by rememberUpdatedState(onAllAnchorsMoved)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val hit = currentHandlePositions.indexOfFirst { s ->
                        (s - tap).getDistance() <= HANDLE_HIT_RADIUS_PX
                    }
                    currentOnAnchorSelected(if (hit >= 0) hit else null)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        val hit = currentHandlePositions.indexOfFirst { s ->
                            (s - start).getDistance() <= HANDLE_HIT_RADIUS_PX
                        }
                        if (hit == 0) dragOriginAll = true
                        if (hit >= 0) currentOnAnchorSelected(hit)
                    },
                    onDragEnd = { dragOriginAll = false },
                    onDragCancel = { dragOriginAll = false },
                ) { change, delta ->
                    change.consume()
                    val dx = delta.x / stepX
                    val dy = -delta.y / stepY
                    if (dragOriginAll) {
                        currentOnAllAnchorsMoved(dx, dy)
                    } else {
                        val idx = currentSelectedIndex ?: return@detectDragGestures
                        if (idx in currentAnchors.indices) {
                            val a = currentAnchors[idx]
                            val nx = (a.x + dx).roundToInt().toFloat().coerceAtLeast(0f)
                            val ny = a.y + dy
                            currentOnAnchorMoved(idx, nx, ny)
                        }
                    }
                }
            }
    ) {
        currentHandlePositions.forEachIndexed { i, s ->
            val r = if (i == currentSelectedIndex) 8.dp.toPx() else 5.dp.toPx()
            val col = if (i == currentSelectedIndex) selectedHandleColor else handleColor
            drawCircle(color = col, radius = r, center = s)
            drawCircle(color = Color.White, radius = r * 0.4f, center = s)
        }
    }
}
