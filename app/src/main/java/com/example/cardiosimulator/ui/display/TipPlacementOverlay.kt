package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.TipLineEndCap
import com.example.cardiosimulator.domain.TipOverlay
import com.example.cardiosimulator.domain.TipOverlayKind
import com.example.cardiosimulator.domain.TipPoint

@Composable
fun TipPlacementOverlay(
    kind: TipOverlayKind,
    endCap: TipLineEndCap,
    homeLead: Lead?,
    onTipPlaced: (TipOverlay) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalPixelScale.current
    val stepX = scale.pxPerSample
    val stepY = scale.pxPerAdcCount

    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDragging by remember { mutableStateOf(false) }

    fun toTipPoint(offset: Offset, sizeHeight: Float): TipPoint {
        val baselineY = sizeHeight / 2f
        val sample = offset.x / stepX
        val adc = (baselineY - offset.y) / stepY
        return TipPoint(sample, adc)
    }

    val isDragKind = kind == TipOverlayKind.Arrow || 
                     kind == TipOverlayKind.GraphArea || 
                     kind == TipOverlayKind.EcgPart || 
                     kind == TipOverlayKind.FreeformArea

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(kind) {
                if (isDragKind) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            currentPoints = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            if (kind == TipOverlayKind.FreeformArea) {
                                currentPoints = currentPoints + change.position
                            } else {
                                currentPoints = listOf(currentPoints.first(), change.position)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            if (currentPoints.isNotEmpty()) {
                                if (kind == TipOverlayKind.FreeformArea && currentPoints.size < 3) {
                                     currentPoints = emptyList()
                                     return@detectDragGestures
                                }
                                val tipPoints = currentPoints.map { toTipPoint(it, size.height.toFloat()) }
                                onTipPlaced(TipOverlay(kind, tipPoints, lead = homeLead, endCap = endCap))
                            }
                            currentPoints = emptyList()
                        },
                        onDragCancel = {
                            isDragging = false
                            currentPoints = emptyList()
                        }
                    )
                } else {
                    detectTapGestures { offset ->
                        val tipPoint = toTipPoint(offset, size.height.toFloat())
                        onTipPlaced(TipOverlay(kind, listOf(tipPoint), lead = homeLead, endCap = endCap))
                    }
                }
            }
    ) {
        if (isDragging && currentPoints.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val color = Color(0xFF1976D2).copy(alpha = 0.5f)
                if (currentPoints.size >= 2) {
                    for (i in 0 until currentPoints.size - 1) {
                        drawLine(color, currentPoints[i], currentPoints[i + 1], strokeWidth = 2f)
                    }
                }
            }
        }
    }
}
