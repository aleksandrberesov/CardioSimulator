package com.example.cardiosimulator.domain

import kotlinx.serialization.Serializable

@Serializable
enum class QuestionKind { SingleChoice, AssembleEcg }

@Serializable
enum class QuestionStimulus { Text, Image, Ecg }

@Serializable
enum class QuestionDifficulty { Easy, Medium, Hard }

@Serializable
data class TestOption(val id: String, val text: String)

@Serializable
data class TestQuestion(
    val id: String,
    val number: Int,
    val text: String,
    val options: List<TestOption>,
    val correctOptionId: String,
    val comment: String,
    val pathologyId: String? = null,    // ECG shown on the monitor; null = leave monitor as-is
    val leads: List<Lead> = emptyList(),
    val scheme: SeriesScheme = SeriesScheme.TwoColumn,
    val imagePath: String? = null,
    val theme: String? = null,
    val tags: String? = null,
    val assemble: EcgAssembly? = null,
    val difficulty: QuestionDifficulty? = null,
    val acronyms: List<String> = emptyList(),
) {
    val kind: QuestionKind
        get() = if (assemble != null) QuestionKind.AssembleEcg else QuestionKind.SingleChoice

    val isAssembly: Boolean get() = kind == QuestionKind.AssembleEcg

    val stimulus: QuestionStimulus
        get() = when {
            !imagePath.isNullOrBlank() -> QuestionStimulus.Image
            !pathologyId.isNullOrBlank() -> QuestionStimulus.Ecg
            else -> QuestionStimulus.Text
        }

    val tagList: List<String>
        get() = tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun correctOptionNumber(): Int =
        options.indexOfFirst { it.id == correctOptionId }.let { if (it < 0) 0 else it + 1 }
}

@Serializable
data class Test(
    val testId: String,
    val title: String,
    val questions: List<TestQuestion>,
    val questionTimeSeconds: Int = 0,   // per-question countdown; 0 = untimed
)
