package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

fun Modifier.chartArea(): Modifier {
    return this
        .fillMaxWidth(1f)
        .fillMaxHeight(1f)
}

fun Modifier.Tab(): Modifier {
    return this
}
