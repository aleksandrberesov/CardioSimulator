package com.example.cardiosimulator.data

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Display-side scaling derived from pixel density and [EcgCalibration].
 * `pxPerMm` is the single anchor: every other value is derived from it,
 * which keeps the paper grid and the waveform in the same coordinate system.
 *
 * Internally reads [EcgCalibration] for fixed conversion factors (500Hz, 1024 counts/mV).
 */
data class PixelScale(
    val pxPerMm: Float,
    val paperSpeedMmPerSec: Float,
    val gainZoomY: Float,
    val cal: EcgCalibration,
    val zoom: Float = 1f,
) {
    val pxPerMv: Float = cal.gainMmPerMv * pxPerMm * gainZoomY
    val pxPerSec: Float = paperSpeedMmPerSec * pxPerMm
    val pxPerSample: Float = pxPerSec / cal.sampleRateHz
    val pxPerAdcCount: Float = pxPerMv / cal.adcCountsPerMv
    val smallGridStepPx: Float = pxPerMm
    val largeGridStepPx: Float = pxPerMm * 5f

    companion object {
        // No longer using source-anchored factories (anchor era dropped).
    }
}

/**
 * Per-lead-count multiplier applied to MonitorModeModel.displayScale on the live monitor.
 * With fewer leads each cell is much taller (cellH = height / rows), leaving the fixed-scale
 * trace as a small graphic in a sea of grid squares; scaling the whole cell (grid + trace) up
 * for sparse layouts makes them read as densely as the full 12-lead view. Hand-tuned by number
 * of leads (not a formula); 6+ leads use the base ×2. Only ever scales up.
 */
fun displayScaleFactor(leadCount: Int): Float = when {
    leadCount <= 1 -> 6.0f
    leadCount == 2 -> 4.4f
    leadCount == 3 -> 3.2f
    leadCount == 4 -> 3.2f
    leadCount == 5 -> 2.4f
    else -> 2.0f
}

val LocalPixelScale = staticCompositionLocalOf<PixelScale> {
    error("PixelScale not provided")
}
