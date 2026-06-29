package com.example.cardiosimulator.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.cardiosimulator.domain.GridScheme

data class GridPalette(val background: Color, val minor: Color, val major: Color, val trace: Color)

fun GridScheme.palette(): GridPalette = when (this) {
    GridScheme.Yellow   -> GridPalette(PaperBackground, GridMinor, GridMajor, EcgTraceTeal)
    GridScheme.BlueGray -> GridPalette(Color(0xFFF0F4F7), Color(0xFFDDE4E9), Color(0xFFBCC6CF), EcgTraceTeal)
    GridScheme.Pink     -> GridPalette(PinkPaperBackground, PinkGridMinor, PinkGridMajor, EcgTraceBlack)
    GridScheme.Blank    -> GridPalette(BedsideBackground, BedsideBackground, BedsideBackground, BedsideTraceGreen)
}
