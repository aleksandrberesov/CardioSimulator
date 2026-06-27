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

val LocalPixelScale = staticCompositionLocalOf<PixelScale> {
    error("PixelScale not provided")
}
