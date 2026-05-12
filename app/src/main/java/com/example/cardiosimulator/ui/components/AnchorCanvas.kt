package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.domain.AnchorPoint
import com.example.cardiosimulator.domain.EasingCurve
import com.example.cardiosimulator.domain.bakeAnchors

/**
 * Source-coordinate -> screen-coordinate mapping for the anchor canvas.
 *
 * `pxPerSourceX` and `pxPerSourceY` come from RP5's grid math
 * (`Frame.Segments.pas:1142`): one source unit on X is `pxPerSec /
 * effectiveSampleRateHz`, one source unit on Y is `pxPerMv / samplesPerMv`
 * — i.e. the editor grid is anchored to source units, not to physical mm.
 */
class AnchorSpace(
    val pxPerSourceX: Float,
    val pxPerSourceY: Float,
    val baselineY: Float,
    val originX: Float = 0f,
) {
    fun toScreen(a: AnchorPoint): Offset =
        Offset(originX + a.x * pxPerSourceX, baselineY - a.y * pxPerSourceY)

    fun fromScreen(o: Offset): Pair<Float, Float> {
        val x = (o.x - originX) / pxPerSourceX
        val y = (baselineY - o.y) / pxPerSourceY
        return x to y
    }
}

/**
 * Renders [anchors] as a polyline (baked through curve interpolation) with
 * draggable handles. Mirrors RP5's Segments tab editing: tap an anchor to
 * select; drag to move; long-press the origin (index 0) to drag all
 * anchors together (RP5's `MovePointsBy`).
 */
@Composable
fun AnchorEditableCanvas(
    anchors: List<AnchorPoint>,
    sampleRateHz: Float,
    samplesPerMv: Float,
    selectedIndex: Int?,
    onAnchorSelected: (Int?) -> Unit,
    onAnchorMoved: (Int, Float, Float) -> Unit,
    onAllAnchorsMoved: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    waveColor: Color = Color.Black,
    handleColor: Color = Color(0xFF1976D2),
    selectedHandleColor: Color = Color(0xFFD32F2F),
) {
    val scale = LocalPixelScale.current
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var dragOriginAll by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(anchors) {
                detectTapGestures { tap ->
                    if (canvasSize == Offset.Zero) return@detectTapGestures
                    val space = computeSpace(scale, sampleRateHz, samplesPerMv, canvasSize.y)
                    val hit = anchors.indexOfFirst { a ->
                        val s = space.toScreen(a)
                        (s - tap).getDistance() <= HANDLE_HIT_RADIUS_PX
                    }
                    onAnchorSelected(if (hit >= 0) hit else null)
                }
            }
            .pointerInput(anchors, selectedIndex) {
                detectDragGestures(
                    onDragStart = { start ->
                        if (canvasSize == Offset.Zero) return@detectDragGestures
                        val space = computeSpace(scale, sampleRateHz, samplesPerMv, canvasSize.y)
                        val hit = anchors.indexOfFirst { a ->
                            val s = space.toScreen(a)
                            (s - start).getDistance() <= HANDLE_HIT_RADIUS_PX
                        }
                        if (hit == 0) dragOriginAll = true
                        if (hit >= 0) onAnchorSelected(hit)
                    },
                    onDragEnd = { dragOriginAll = false },
                    onDragCancel = { dragOriginAll = false },
                ) { change, delta ->
                    change.consume()
                    val space = computeSpace(scale, sampleRateHz, samplesPerMv, canvasSize.y)
                    val dx = delta.x / space.pxPerSourceX
                    val dy = -delta.y / space.pxPerSourceY
                    if (dragOriginAll) {
                        onAllAnchorsMoved(dx, dy)
                    } else {
                        val idx = selectedIndex ?: return@detectDragGestures
                        if (idx in anchors.indices) {
                            val a = anchors[idx]
                            onAnchorMoved(idx, a.x + dx, a.y + dy)
                        }
                    }
                }
            }
    ) {
        canvasSize = Offset(size.width, size.height)
        val space = computeSpace(scale, sampleRateHz, samplesPerMv, size.height)
        drawWaveform(anchors, space, waveColor)
        drawHandles(anchors, space, handleColor, selectedHandleColor, selectedIndex)
    }
}

private fun computeSpace(
    scale: com.example.cardiosimulator.data.PixelScale,
    sampleRateHz: Float,
    samplesPerMv: Float,
    height: Float,
): AnchorSpace {
    // One source-X unit = one sample at the part's effective rate. Convert
    // to px through the scale's px-per-sample.
    val pxX = if (sampleRateHz > 0f) scale.pxPerSampleFor(sampleRateHz) else scale.pxPerSample
    val pxY = if (samplesPerMv > 0f) scale.pxPerSourceUnitFor(samplesPerMv) else scale.pxPerAdcCount
    return AnchorSpace(
        pxPerSourceX = pxX.coerceAtLeast(0.0001f),
        pxPerSourceY = pxY.coerceAtLeast(0.0001f),
        baselineY = height / 2f,
    )
}

private fun DrawScope.drawWaveform(
    anchors: List<AnchorPoint>,
    space: AnchorSpace,
    color: Color,
) {
    if (anchors.size < 2) return
    val baked = bakeAnchors(anchors)
    if (baked.size < 2) return
    val path = Path().apply {
        baked.forEachIndexed { i, (x, y) ->
            val sx = space.originX + x * space.pxPerSourceX
            val sy = space.baselineY - y * space.pxPerSourceY
            if (i == 0) moveTo(sx, sy) else lineTo(sx, sy)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawHandles(
    anchors: List<AnchorPoint>,
    space: AnchorSpace,
    handleColor: Color,
    selectedColor: Color,
    selectedIndex: Int?,
) {
    anchors.forEachIndexed { i, a ->
        val s = space.toScreen(a)
        val r = if (i == selectedIndex) 8.dp.toPx() else 5.dp.toPx()
        val col = if (i == selectedIndex) selectedColor else handleColor
        drawCircle(color = col, radius = r, center = s)
        drawCircle(color = Color.White, radius = r * 0.4f, center = s)
    }
}

private const val HANDLE_HIT_RADIUS_PX = 36f
