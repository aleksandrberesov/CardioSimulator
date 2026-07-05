package com.example.cardiosimulator.ui.display

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.domain.TipOverlay
import com.example.cardiosimulator.domain.TipOverlayKind
import com.example.cardiosimulator.domain.TipLineEndCap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TipRenderOverlay(
    tips: List<TipOverlay>,
    modifier: Modifier = Modifier
) {
    val scale = LocalPixelScale.current
    val density = LocalDensity.current
    val stepX = scale.pxPerSample
    val stepY = scale.pxPerAdcCount
    
    val fontSizePx = with(density) { 12.sp.toPx() }

    val tipBlue = Color(0xFF1976D2)
    val tipBlueFill = tipBlue.copy(alpha = 60f / 255f)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baselineY = size.height / 2f
            
            clipRect {
                tips.forEach { tip ->
                    drawTip(tip, stepX, stepY, baselineY, tipBlue, tipBlueFill, scale.zoom, fontSizePx)
                }
            }
        }
    }
}

private fun DrawScope.drawTip(
    tip: TipOverlay,
    stepX: Float,
    stepY: Float,
    baselineY: Float,
    color: Color,
    fillColor: Color,
    zoom: Float,
    fontSizePx: Float
) {
    if (tip.points.isEmpty() && tip.kind != TipOverlayKind.LeadArea) return

    val pts = tip.points.map { 
        Offset(it.sample * stepX, baselineY - it.adc * stepY)
    }
    
    val strokeWidth = 2.dp.toPx() / zoom

    when (tip.kind) {
        TipOverlayKind.Arrow -> {
            if (pts.size >= 2) {
                val start = pts[0]
                val end = pts[1]
                drawLine(color, start, end, strokeWidth = strokeWidth)
                drawArrowHead(start, end, color, strokeWidth)
                if (!tip.text.isNullOrBlank()) {
                    drawTipText(tip.text, end, color, fontSizePx)
                }
            }
        }
        TipOverlayKind.LeadArea -> {
            drawRect(fillColor, Offset(0f, 0f), size = size)
        }
        TipOverlayKind.GraphArea -> {
            if (pts.size >= 2) {
                val p1 = pts[0]
                val p2 = pts[1]
                val left = minOf(p1.x, p2.x)
                val top = minOf(p1.y, p2.y)
                val right = maxOf(p1.x, p2.x)
                val bottom = maxOf(p1.y, p2.y)
                val rectSize = Size(right - left, bottom - top)
                drawRect(fillColor, Offset(left, top), rectSize)
                drawRect(color, Offset(left, top), rectSize, style = Stroke(strokeWidth))
            }
        }
        TipOverlayKind.FreeformArea -> {
            if (pts.size >= 3) {
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        lineTo(pts[i].x, pts[i].y)
                    }
                    close()
                }
                drawPath(path, fillColor)
                drawPath(path, color, style = Stroke(strokeWidth))
            }
        }
        TipOverlayKind.EcgPart -> {
            if (pts.size >= 2) {
                val x1 = pts[0].x
                val x2 = pts[1].x
                val left = minOf(x1, x2)
                val right = maxOf(x1, x2)
                drawRect(fillColor, Offset(left, 0f), Size(right - left, size.height))
                drawLine(color, Offset(left, 0f), Offset(left, size.height), strokeWidth = strokeWidth)
                drawLine(color, Offset(right, 0f), Offset(right, size.height), strokeWidth = strokeWidth)
            }
        }
        TipOverlayKind.VerticalLines -> {
            pts.forEach { pt ->
                drawLine(color, Offset(pt.x, 0f), Offset(pt.x, size.height), strokeWidth = strokeWidth)
                drawEndCaps(Offset(pt.x, 0f), Offset(pt.x, size.height), tip.endCap, color, strokeWidth, vertical = true)
            }
        }
        TipOverlayKind.HorizontalLines -> {
            pts.forEach { pt ->
                drawLine(color, Offset(0f, pt.y), Offset(size.width, pt.y), strokeWidth = strokeWidth)
                drawEndCaps(Offset(0f, pt.y), Offset(size.width, pt.y), tip.endCap, color, strokeWidth, vertical = false)
            }
        }
        TipOverlayKind.Label -> {
            if (pts.isNotEmpty()) {
                val pt = pts[0]
                if (!tip.text.isNullOrBlank()) {
                    drawTipText(tip.text, pt, color, fontSizePx, drawBackground = true)
                }
            }
        }
        TipOverlayKind.Points -> {
            pts.forEach { pt ->
                drawCircle(color, 4.dp.toPx(), pt)
            }
        }
    }
}

private fun DrawScope.drawArrowHead(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    val angle = atan2(end.y - start.y, end.x - start.x)
    val headLen = 12.dp.toPx()
    val headAngle = Math.PI / 6
    
    val p1 = Offset(
        end.x - headLen * cos(angle - headAngle).toFloat(),
        end.y - headLen * sin(angle - headAngle).toFloat()
    )
    val p2 = Offset(
        end.x - headLen * cos(angle + headAngle).toFloat(),
        end.y - headLen * sin(angle + headAngle).toFloat()
    )
    drawLine(color, end, p1, strokeWidth = strokeWidth)
    drawLine(color, end, p2, strokeWidth = strokeWidth)
}

private fun DrawScope.drawEndCaps(
    start: Offset, 
    end: Offset, 
    cap: TipLineEndCap, 
    color: Color, 
    strokeWidth: Float,
    vertical: Boolean
) {
    if (cap == TipLineEndCap.Plain) return
    
    if (cap == TipLineEndCap.Dots) {
        drawCircle(color, 4.dp.toPx(), start)
        drawCircle(color, 4.dp.toPx(), end)
    } else if (cap == TipLineEndCap.Arrows) {
        val headLen = 10.dp.toPx()
        val headAngle = Math.PI / 4
        if (vertical) {
            // Top arrow
            drawLine(color, start, Offset(start.x - headLen * sin(headAngle).toFloat(), start.y + headLen * cos(headAngle).toFloat()), strokeWidth)
            drawLine(color, start, Offset(start.x + headLen * sin(headAngle).toFloat(), start.y + headLen * cos(headAngle).toFloat()), strokeWidth)
            // Bottom arrow
            drawLine(color, end, Offset(end.x - headLen * sin(headAngle).toFloat(), end.y - headLen * cos(headAngle).toFloat()), strokeWidth)
            drawLine(color, end, Offset(end.x + headLen * sin(headAngle).toFloat(), end.y - headLen * cos(headAngle).toFloat()), strokeWidth)
        } else {
            // Left arrow
            drawLine(color, start, Offset(start.x + headLen * cos(headAngle).toFloat(), start.y - headLen * sin(headAngle).toFloat()), strokeWidth)
            drawLine(color, start, Offset(start.x + headLen * cos(headAngle).toFloat(), start.y + headLen * sin(headAngle).toFloat()), strokeWidth)
            // Right arrow
            drawLine(color, end, Offset(end.x - headLen * cos(headAngle).toFloat(), end.y - headLen * sin(headAngle).toFloat()), strokeWidth)
            drawLine(color, end, Offset(end.x - headLen * cos(headAngle).toFloat(), end.y + headLen * sin(headAngle).toFloat()), strokeWidth)
        }
    }
}

private fun DrawScope.drawTipText(
    text: String,
    pos: Offset,
    color: Color,
    fontSizePx: Float,
    drawBackground: Boolean = false
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = Paint().apply {
            this.color = color.toArgb()
            textSize = fontSizePx
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 0f, 0f, Color.White.toArgb())
        }
        
        if (drawBackground) {
            val textWidth = paint.measureText(text)
            val fm = paint.fontMetrics
            val bgRect = android.graphics.RectF(
                pos.x - 4f, 
                pos.y + fm.ascent - 2f,
                pos.x + textWidth + 4f,
                pos.y + fm.descent + 2f
            )
            val bgPaint = Paint().apply {
                this.color = Color.White.copy(alpha = 0.8f).toArgb()
            }
            drawRect(bgRect, bgPaint)
        }
        
        drawText(text, pos.x, pos.y, paint)
    }
}
