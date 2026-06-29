package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.domain.GridScheme
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.cardiosimulator.ui.theme.*

fun Modifier.leadArea(): Modifier {
    return this
        .fillMaxWidth(1f)
        .fillMaxHeight(1f)
}

fun Modifier.ekgGrid(
    scheme: GridScheme = GridScheme.Yellow,
    xOffsetPx: Float = 0f,
    showBackground: Boolean = true
): Modifier = composed {
    val pal = scheme.palette()

    if (scheme == GridScheme.Blank) {
        return@composed if (showBackground) {
            this.background(pal.background)
        } else {
            this
        }
    }

    val smallGridColor = pal.minor
    val largeGridColor = pal.major

    val scale = LocalPixelScale.current
    val smallStep = scale.smallGridStepPx
    val largeStep = scale.largeGridStepPx

    val baseModifier = if (showBackground) this.background(pal.background) else this

    baseModifier
        .drawWithCache {
            val thinStroke = 0.5.dp.toPx() / scale.zoom
            val thickStroke = 1.5.dp.toPx() / scale.zoom

            val smallPath = Path()
            val largePath = Path()

            // Vertical lines: draw one extra large step to allow for scrolling
            var x = 0f
            var i = 0
            while (x <= size.width + largeStep) {
                if (i % 5 == 0) {
                    largePath.moveTo(x, 0f)
                    largePath.lineTo(x, size.height)
                } else {
                    smallPath.moveTo(x, 0f)
                    smallPath.lineTo(x, size.height)
                }
                x += smallStep
                i++
            }

            // Horizontal lines
            var y = 0f
            var j = 0
            while (y <= size.height) {
                if (j % 5 == 0) {
                    largePath.moveTo(0f, y)
                    largePath.lineTo(size.width + largeStep, y)
                } else {
                    smallPath.moveTo(0f, y)
                    smallPath.lineTo(size.width + largeStep, y)
                }
                y += smallStep
                j++
            }

            onDrawBehind {
                val scroll = xOffsetPx % largeStep
                drawContext.canvas.save()
                drawContext.canvas.translate(scroll, 0f)
                drawPath(smallPath, smallGridColor, style = Stroke(thinStroke))
                drawPath(largePath, largeGridColor, style = Stroke(thickStroke))
                drawContext.canvas.restore()
            }
        }
}
