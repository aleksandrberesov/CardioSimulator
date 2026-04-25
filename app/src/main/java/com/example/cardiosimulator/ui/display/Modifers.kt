package com.example.cardiosimulator.ui.display

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.domain.GridScheme
import kotlin.math.roundToInt

fun Modifier.seriesArea(): Modifier {
    return this
        .fillMaxWidth(1f)
        .fillMaxHeight(1f)
//        .border(1.dp, Color.Black)
}

fun Modifier.ekgGrid(scheme: GridScheme = GridScheme.Pink): Modifier {
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

    return this
        .background(backgroundColor)
        .drawBehind {
            val smallGridStep = 5.dp.toPx()

            // Vertical lines
            var x = 0f
            while (x <= size.width) {
                val isLarge = (x / smallGridStep).roundToInt() % 5 == 0
                drawLine(
                    color = if (isLarge) largeGridColor else smallGridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = if (isLarge) 1.5.dp.toPx() else 0.5.dp.toPx()
                )
                x += smallGridStep
            }

            // Horizontal lines
            var y = 0f
            while (y <= size.height) {
                val isLarge = (y / smallGridStep).roundToInt() % 5 == 0
                drawLine(
                    color = if (isLarge) largeGridColor else smallGridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (isLarge) 1.5.dp.toPx() else 0.5.dp.toPx()
                )
                y += smallGridStep
            }
        }
}
