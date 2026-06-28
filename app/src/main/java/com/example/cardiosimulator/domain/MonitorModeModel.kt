package com.example.cardiosimulator.domain

import androidx.annotation.StringRes
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.EcgCalibration
import kotlinx.serialization.Serializable

enum class GridScheme(@param:StringRes val labelRes: Int) {
    Pink(R.string.grid_scheme_pink),
    BlueGray(R.string.grid_scheme_blue_gray),
    Blank(R.string.grid_scheme_blank)
}

@Serializable
enum class SeriesScheme {
    OneColumn,
    TwoColumn,
    ThreeByFour,
    Grid
}

enum class EcgArtifact {
    None,
    Muscle,
    Mains,
    Baseline,
    Contact,
    Motion
}

enum class EcgFilterType {
    NONE,
    LOWPASS,
    HIGHPASS,
    BANDPASS
}

enum class TipOverlayKind {
    Arrow,
    LeadArea,
    GraphArea,
    EcgPart,
    VerticalLines,
    HorizontalLines,
    Label
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
    val showImpulseLabels: Boolean = false,
    val artifact: EcgArtifact = EcgArtifact.None,
    val filterType: EcgFilterType = EcgFilterType.NONE,
    val showElectrodes: Boolean = false,
    val show3D: Boolean = false,
    val showEos: Boolean = false,
    val showTips: Boolean = false,
    val selectedTipKind: TipOverlayKind = TipOverlayKind.Arrow,
    val comparisonTargets: Map<Int, ComparisonTarget> = emptyMap(),
    val comparisonPresets: List<ComparisonPreset> = emptyList(),
    val leadOrder: List<Lead>? = null
)
