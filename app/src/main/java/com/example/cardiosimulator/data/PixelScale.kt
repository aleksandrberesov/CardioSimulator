package com.example.cardiosimulator.data

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Display-side scaling derived from pixel density and [EcgCalibration].
 * `pxPerMm` is the single anchor: every other value is derived from it,
 * which keeps the paper grid and the waveform in the same coordinate system.
 *
 * The `cal` field still owns the *default* sample rate and ADC scale used
 * by viewers (and by the old asset-based fixtures). Records that ship with
 * `max`/`value`/`duration` in their `source:` field override these
 * defaults via per-part calls to [pxPerSampleFor] / [pxPerAdcCountFor] —
 * see [com.example.cardiosimulator.domain.WaveformPart.effectiveSampleRateHz]
 * and [com.example.cardiosimulator.domain.WaveformPart.samplesPerMv].
 */
class PixelScale(
    val pxPerMm: Float,
    val paperSpeedMmPerSec: Float,
    val gainZoomY: Float,
    val cal: EcgCalibration,
    /**
     * If true, the grid step is the *source coordinate system*'s mm — i.e.
     * one grid square == `AMax/AValue/10` source units. Used by Editor mode
     * so dragging an anchor maps to an integer step in the file's own units.
     * Set false for viewer modes (Teaching / Testing / Examination / OSKE)
     * where the grid is a physical-mm grid.
     */
    val sourceAnchored: Boolean = false,
) {
    val pxPerMv: Float = cal.gainMmPerMv * pxPerMm * gainZoomY
    val pxPerSec: Float = paperSpeedMmPerSec * pxPerMm
    /** Default px-per-sample using the global sampleRateHz. */
    val pxPerSample: Float = pxPerSec / cal.sampleRateHz
    /** Default px-per-ADC-count using the global adcCountsPerMv. */
    val pxPerAdcCount: Float = pxPerMv / cal.adcCountsPerMv
    val smallGridStepPx: Float = pxPerMm
    val largeGridStepPx: Float = pxPerMm * 5f

    /**
     * Per-part px-per-sample using the part's effective sample rate.
     * Falls back to the global if [sampleRateHz] is non-positive.
     */
    fun pxPerSampleFor(sampleRateHz: Float): Float =
        if (sampleRateHz > 0f) pxPerSec / sampleRateHz else pxPerSample

    /**
     * Per-part px-per-source-unit using the part's `samplesPerMv` factor.
     * `samplesPerMv` is `AMax/AValue`; with `gainMmPerMv` mm/mV
     * the result is the per-source-unit pixel scale.
     */
    fun pxPerAdcCountFor(samplesPerMv: Float): Float =
        if (samplesPerMv > 0f) pxPerMv / samplesPerMv else pxPerAdcCount

    companion object {
        /**
         * Convenience builder for Editor mode: replaces [pxPerMm] with a
         * source-anchored value `AMax/AValue/10` so the grid aligns with
         * one source-unit per small grid square.
         */
        fun sourceAnchored(
            aMax: Int,
            aValue: Int,
            paperSpeedMmPerSec: Float,
            gainZoomY: Float,
            cal: EcgCalibration,
            physicalPxPerMm: Float,
        ): PixelScale {
            val safeVal = aValue.coerceAtLeast(1)
            // One grid square == 1 source unit == AMax/AValue/10 px.
            // We still want a sensible *visible* size — we multiply the
            // source-anchored value by the physical px/mm ratio so the
            // editor grid stays in a reasonable on-screen size for any
            // reasonable AMax/AValue.
            val sourcePxPerMm = (aMax.toFloat() / safeVal / 10f) * (physicalPxPerMm / (160f / 25.4f))
            return PixelScale(
                pxPerMm = sourcePxPerMm.coerceAtLeast(1f),
                paperSpeedMmPerSec = paperSpeedMmPerSec,
                gainZoomY = gainZoomY,
                cal = cal,
                sourceAnchored = true,
            )
        }
    }
}

val LocalPixelScale = staticCompositionLocalOf<PixelScale> {
    error("PixelScale not provided")
}
