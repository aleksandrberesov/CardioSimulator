package com.example.cardiosimulator.domain

/**
 * Represents the standard significant points of an ECG complex
 * as shown in the reference scheme.
 */
enum class EcgPointType(val label: String, val descriptionRu: String) {
    P("P", "Сокращение и расслабление предсердий"),
    Q("Q", "Возбуждение межжелудочковой перегородки (начало)"),
    R("R", "Возбуждение желудочков (пик)"),
    S("S", "Возбуждение межжелудочковой перегородки (конец)"),
    T("T", "Расслабление желудочков")
}

/**
 * A marker for a specific point in a waveform.
 */
data class SignificantPoint(
    val index: Int,
    val type: EcgPointType
)
