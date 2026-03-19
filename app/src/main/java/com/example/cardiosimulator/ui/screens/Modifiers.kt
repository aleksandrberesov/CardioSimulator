package com.example.cardiosimulator.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

fun Modifier.topSection(): Modifier {
    return this
        .background(Color.LightGray)
        .padding(8.dp)
}

fun Modifier.middleSectionLeft(): Modifier {
    return this
        .background(Color.Cyan)
        .padding(8.dp)
}

fun Modifier.middleSectionCenter(): Modifier {
    return this
        .background(Color.Green)
        .padding(8.dp)
}

fun Modifier.middleSectionRight(): Modifier {
    return this
        .background(Color.Yellow)
        .padding(8.dp)
}

fun Modifier.bottomSection(): Modifier {
    return this
        .background(Color.LightGray)
        .padding(8.dp)
}