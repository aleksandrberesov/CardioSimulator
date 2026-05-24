package com.example.cardiosimulator.domain

/**
 * Represents the standard significant points and boundaries of an ECG complex
 * as shown in the reference scheme.
 */
enum class EcgPointType(val label: String, val descriptionRu: String) {
    P_START("P<sub>s</sub>", "Начало зубца P"),
    P_PEAK("P", "Пик зубца P"),
    P_END("P<sub>e</sub>", "Конец зубца P"),
    
    QRS_START("QRS<sub>s</sub>", "Начало комплекса QRS"),
    Q_PEAK("Q", "Пик зубца Q"),
    R_PEAK("R", "Пик зубца R"),
    S_PEAK("S", "Пик зубца S"),
    QRS_END("QRS<sub>e</sub>", "Конец комплекса QRS"),
    
    T_START("T<sub>s</sub>", "Начало зубца T"),
    T_PEAK("T", "Пик зубца T"),
    T_END("T<sub>e</sub>", "Конец зубца T")
}

/**
 * A marker for a specific point in a waveform.
 */
data class SignificantPoint(
    val index: Int,
    val type: EcgPointType
)
