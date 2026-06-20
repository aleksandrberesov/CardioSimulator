package com.example.cardiosimulator.domain

data class TestOption(val id: String, val text: String)

data class TestQuestion(
    val id: String,
    val number: Int,
    val text: String,
    val options: List<TestOption>,
    val correctOptionId: String,
    val comment: String,
    val pathologyId: String? = null,    // ECG shown on the monitor; null = leave monitor as-is
    val leads: List<Lead> = emptyList(),
    val scheme: SeriesScheme = SeriesScheme.Grid,
) {
    fun correctOptionNumber(): Int =
        options.indexOfFirst { it.id == correctOptionId }.let { if (it < 0) 0 else it + 1 }
}

data class Test(
    val testId: String,
    val title: String,
    val questions: List<TestQuestion>,
    val questionTimeSeconds: Int = 0,   // per-question countdown; 0 = untimed
)
