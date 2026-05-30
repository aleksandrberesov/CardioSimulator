package com.example.cardiosimulator.domain

import androidx.annotation.StringRes
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.EcgCalibration

enum class GridScheme(@StringRes val labelRes: Int) {
    Pink(R.string.grid_scheme_pink),
    BlueGray(R.string.grid_scheme_blue_gray),
    Blank(R.string.grid_scheme_blank)
}

enum class SeriesScheme {
    OneColumn,
    TwoColumn,
    Grid
}

data class ComparisonTarget(
    val pathologyId: String,
    val lead: Lead
)

data class ComparisonPreset(
    val name: String,
    val targets: Map<Int, ComparisonTarget>
)

data class MonitorModeModel(
    val count: Int = 1,
    val gridScheme: GridScheme = GridScheme.Pink,
    val seriesScheme: SeriesScheme = SeriesScheme.OneColumn,
    val speed: Float = 25f,
    val scale: Float = 1f,
    val displayScale: Float = 0.4f,
    val calibration: EcgCalibration = EcgCalibration(),
    val isRunning: Boolean = false,
    val isCompareMode: Boolean = false,
    val comparisonTargets: Map<Int, ComparisonTarget> = emptyMap(),
    val comparisonPresets: List<ComparisonPreset> = emptyList()
)
