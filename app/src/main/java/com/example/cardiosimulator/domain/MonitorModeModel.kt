package com.example.cardiosimulator.domain

import androidx.annotation.StringRes
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.EcgCalibration

enum class GridScheme(@StringRes val labelRes: Int) {
    Pink(R.string.grid_scheme_pink),
    BlueGray(R.string.grid_scheme_blue_gray)
}

enum class SeriesScheme {
    OneColumn,
    TwoColumn,
    Grid
}

data class MonitorModeModel(
    val count: Int = 1,
    val gridScheme: GridScheme = GridScheme.Pink,
    val seriesScheme: SeriesScheme = SeriesScheme.OneColumn,
    val speed: Int = 25,
    val scale: Float = 1f,
    val displayScale: Float = 0.4f,
    val calibration: EcgCalibration = EcgCalibration(),
    val isRunning: Boolean = false,
    val isBlankSheet: Boolean = false,
)
