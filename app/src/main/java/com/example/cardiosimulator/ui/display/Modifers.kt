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

fun Modifier.seriesArea(): Modifier {
    return this
        .fillMaxWidth(1f)
        .fillMaxHeight(1f)
}

fun Modifier.ekgGrid(scheme: GridScheme = GridScheme.Pink): Modifier = composed {
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
        .drawBehind {
            val thinStroke = 0.5.dp.toPx()
            val thickStroke = 1.5.dp.toPx()

            // Vertical small lines
            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = smallGridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = thinStroke,
                )
                x += smallStep
            }
            // Vertical major lines (every 5 mm)
            x = 0f
            while (x <= size.width) {
                drawLine(
                    color = largeGridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = thickStroke,
                )
                x += largeStep
            }
            // Horizontal small lines
            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color = smallGridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = thinStroke,
                )
                y += smallStep
            }
            // Horizontal major lines (every 5 mm)
            y = 0f
            while (y <= size.height) {
                drawLine(
                    color = largeGridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = thickStroke,
                )
                y += largeStep
            }
        }
}
