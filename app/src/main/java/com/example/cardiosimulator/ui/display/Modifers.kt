package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.domain.GridScheme

import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

fun Modifier.leadArea(): Modifier {
    return this
        .fillMaxWidth(1f)
        .fillMaxHeight(1f)
}

fun Modifier.ekgGrid(
    scheme: GridScheme = GridScheme.Pink,
    isBlankSheet: Boolean = false,
    scrollOffsetPx: Float = 0f
): Modifier = composed {
    val backgroundColor = when (scheme) {
        GridScheme.Pink -> Color(0xFFFFF5F5)
        GridScheme.BlueGray -> Color(0xFFF0F4F7)
    }
    val smallGridColor = when (scheme) {
        GridScheme.Pink -> Color(0xFFFDE4E4)
        GridScheme.BlueGray -> Color(0xFFDDE4E9)
    }
    val largeGridColor = when (scheme) {
        GridScheme.Pink -> Color(0xFFF9BDBD)
        GridScheme.BlueGray -> Color(0xFFBCC6CF)
    }

    val scale = LocalPixelScale.current
    val smallStep = scale.smallGridStepPx
    val largeStep = scale.largeGridStepPx

    this
        .background(backgroundColor)
        .drawWithCache {
            val thinStroke = 0.5.dp.toPx()
            val thickStroke = 1.5.dp.toPx()

            val smallPath = Path()
            val largePath = Path()

            // Vertical lines
            val offsetX = scrollOffsetPx % largeStep
            var x = offsetX
            while (x <= size.width + largeStep) {
                largePath.moveTo(x, 0f)
                largePath.lineTo(x, size.height)

                // Small lines between large ones
                for (k in 1..4) {
                    val sx = x + k * smallStep
                    smallPath.moveTo(sx, 0f)
                    smallPath.lineTo(sx, size.height)
                }

                x += largeStep
            }

            // Horizontal lines (static)
            var y = 0f
            var j = 0
            while (y <= size.height) {
                if (j % 5 == 0) {
                    largePath.moveTo(0f, y)
                    largePath.lineTo(size.width, y)
                } else {
                    smallPath.moveTo(0f, y)
                    smallPath.lineTo(size.width, y)
                }
                y += smallStep
                j++
            }

            onDrawBehind {
                if (!isBlankSheet) {
                    drawPath(smallPath, smallGridColor, style = Stroke(thinStroke))
                    drawPath(largePath, largeGridColor, style = Stroke(thickStroke))
                } else {
                    // "Black bar" - draw a vertical line at the scroll boundary
                    // to give a sense of movement on a blank sheet
                    val barX = (scrollOffsetPx % size.width + size.width) % size.width
                    drawLine(
                        color = Color.Black.copy(alpha = 0.1f),
                        start = Offset(barX, 0f),
                        end = Offset(barX, size.height),
                        strokeWidth = 4.dp.toPx()
                    )
                }
            }
        }
}

