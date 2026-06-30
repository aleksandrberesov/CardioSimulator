package com.example.cardiosimulator.signals.biosppy

import com.example.cardiosimulator.domain.EcgFilterType

object EcgFilters {
    fun apply(signal: DoubleArray, filterType: EcgFilterType, samplingRate: Double): DoubleArray {
        return try {
            when (filterType) {
                EcgFilterType.LOWPASS ->
                    Filter.filterSignal(signal, "butter", "lowpass", 4, doubleArrayOf(25.0), samplingRate)
                EcgFilterType.HIGHPASS ->
                    Filter.filterSignal(signal, "butter", "highpass", 4, doubleArrayOf(3.0), samplingRate)
                EcgFilterType.BANDPASS ->
                    Filter.filterSignal(signal, "butter", "bandpass", 4, doubleArrayOf(3.0, 25.0), samplingRate)
                else -> signal
            }
        } catch (e: Exception) {
            signal
        }
    }
}
