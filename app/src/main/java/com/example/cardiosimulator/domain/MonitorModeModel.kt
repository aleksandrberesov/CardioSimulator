package com.example.cardiosimulator.domain

enum class GridScheme {
    Pink,
    BlueGray
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
    val scale: Int = 100
)
