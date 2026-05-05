package com.example.cardiosimulator.data

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Display-side scaling derived from pixel density and [EcgCalibration].
 * `pxPerMm` is the single anchor: every other value is derived from it,
 * which keeps the paper grid and the waveform in the same coordinate system.
 */
class PixelScale(
    val pxPerMm: Float,
    paperSpeedMmPerSec: Float,
    gainZoomY: Float,
    cal: EcgCalibration,
) {
    val pxPerMv: Float = cal.gainMmPerMv * pxPerMm * gainZoomY
    val pxPerSec: Float = paperSpeedMmPerSec * pxPerMm
    val pxPerSample: Float = pxPerSec / cal.sampleRateHz
    val pxPerAdcCount: Float = pxPerMv / cal.adcCountsPerMv
    val smallGridStepPx: Float = pxPerMm
    val largeGridStepPx: Float = pxPerMm * 5f
}

val LocalPixelScale = staticCompositionLocalOf<PixelScale> {
    error("PixelScale not provided")
}
