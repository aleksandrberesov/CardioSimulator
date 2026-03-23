package com.example.cardiosimulator.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

fun Modifier.topSection(): Modifier {
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

fun Modifier.middleSectionRight(): Modifier {
    return this
        .background(Color.LightGray)
        .padding(8.dp)
        .fillMaxHeight()
}

fun Modifier.bottomSection(): Modifier {
    return this
        .background(Color.LightGray)
        .padding(8.dp)
        .fillMaxWidth()
}