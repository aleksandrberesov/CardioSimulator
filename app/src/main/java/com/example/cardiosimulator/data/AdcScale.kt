package com.example.cardiosimulator.data

/**
 * Model that defines the mapping between ADC (Analog-to-Digital Converter) values 
 * and screen pixels for consistent ECG rendering.
 */
data class AdcScale(
    val horizontalPixelsPerSample: Float = 1.0f,
    val verticalPixelsPerUnit: Float = 0.5f
)
