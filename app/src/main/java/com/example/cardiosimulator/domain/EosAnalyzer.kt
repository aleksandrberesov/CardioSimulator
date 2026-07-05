package com.example.cardiosimulator.domain

import com.example.cardiosimulator.data.EcgCalibration
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.signals.biosppy.Landmarks
import com.example.cardiosimulator.signals.biosppy.QrsSegmenters

data class EosAnalysis(
    val result: EosResult,
    val highlightSpans: Map<Lead, List<EcgSpan>>
)

object EosAnalyzer {

    fun analyze(waveforms: Map<Lead, Points>?, calibration: EcgCalibration): EosAnalysis? {
        if (waveforms == null) return null

        val iMeasure = measure(waveforms[Lead.I], calibration) ?: return null
        val avfMeasure = measure(waveforms[Lead.aVF], calibration) ?: return null

        val result = EosAxis.from(iMeasure.first, avfMeasure.first)
        val highlightSpans = mapOf(
            Lead.I to iMeasure.second,
            Lead.aVF to avfMeasure.second
        )

        return EosAnalysis(result, highlightSpans)
    }

    private fun measure(points: Points?, cal: EcgCalibration): Pair<EosLeadMeasure, List<EcgSpan>>? {
        if (points == null || points.values.isEmpty()) return null

        val sig = points.values.map { it.toDouble() }.toDoubleArray()
        val fs = cal.sampleRateHz.toDouble()

        return try {
            val rpeaksRaw = QrsSegmenters.hamiltonSegmenter(sig, fs)
            if (rpeaksRaw.isEmpty()) return null

            val rpeaks = QrsSegmenters.correctRPeaks(sig, rpeaksRaw, fs, 0.05)
            val landmarks = Landmarks.getLandmarks(sig, rpeaks, fs)
            if (landmarks.isEmpty()) return null

            val toMm = cal.gainMmPerMv.toDouble() / cal.adcCountsPerMv.toDouble()

            val rValues = mutableListOf<Double>()
            val qValues = mutableListOf<Double>()
            val sValues = mutableListOf<Double>()
            val spans = mutableListOf<EcgSpan>()

            for (lm in landmarks) {
                // R
                if (lm.rPeak in sig.indices) {
                    rValues.add(maxOf(0.0, sig[lm.rPeak] * toMm))
                }
                // Q
                if (lm.qPeak in sig.indices) {
                    qValues.add(maxOf(0.0, -sig[lm.qPeak] * toMm))
                }
                // S
                if (lm.sPeak in sig.indices) {
                    sValues.add(maxOf(0.0, -sig[lm.sPeak] * toMm))
                }

                if (lm.qrsStart in sig.indices && lm.qrsEnd in sig.indices && lm.qrsEnd > lm.qrsStart) {
                    spans.add(EcgSpan(lm.qrsStart, lm.qrsEnd))
                }
            }

            if (rValues.isEmpty()) return null

            val leadMeasure = EosLeadMeasure(
                qMm = qValues.average(),
                rMm = rValues.average(),
                sMm = sValues.average()
            )

            leadMeasure to spans
        } catch (e: Exception) {
            null
        }
    }
}
