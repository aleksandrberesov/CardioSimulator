package com.example.cardiosimulator.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.topSection(): Modifier {
    return this
        .background(Color.LightGray)
        .padding(8.dp)
        .fillMaxWidth()
}

fun Modifier.bottomSection(): Modifier {
    return this
        .background(Color.LightGray)
        .padding(8.dp)
        .fillMaxWidth()
}

fun Modifier.middleSectionLeft(): Modifier {
    return this
        .background(Color.LightGray)
        .padding(8.dp)
        .fillMaxHeight()
}

fun Modifier.middleSectionCenter(): Modifier {
    return this
        .background(Color.LightGray)
        .padding(8.dp)
        .fillMaxHeight()
}

@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f)
): Modifier {
    val targetAlpha = if (state.isScrollInProgress) 1f else 0f
    val duration = if (state.isScrollInProgress) 150 else 500

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
    )

    return drawWithContent {
        drawContent()

        val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0f

        if (needDrawScrollbar && firstVisibleElementIndex != null) {
            val elementCount = state.layoutInfo.totalItemsCount
            val visibleElementsCount = state.layoutInfo.visibleItemsInfo.size
            val scrollbarHeight = (size.height / elementCount) * visibleElementsCount
            val scrollbarOffsetY = (size.height / elementCount) * firstVisibleElementIndex

            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight)
            )
        }
    }
}
