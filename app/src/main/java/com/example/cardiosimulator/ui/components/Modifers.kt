package com.example.cardiosimulator.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

fun Modifier.chartArea(): Modifier {
    return this
        .fillMaxWidth(1f)
        .fillMaxHeight(1f)
        .background(Color.White)
        //.padding(16.dp)
}
fun Modifier.Tab(): Modifier {
    return this
}