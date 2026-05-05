package com.example.cardiosimulator.data

/**
 * Physical ECG calibration constants. Treated as ground truth when mapping
 * raw ADC samples to standard ECG paper coordinates (mm/mV, mm/s).
 */
data class EcgCalibration(
    val gainMmPerMv: Float = 10f,
    val sampleRateHz: Float = 250f,
    val adcCountsPerMv: Float = 200f,
)
