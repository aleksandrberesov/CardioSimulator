package com.example.cardiosimulator.domain.generators

import com.example.cardiosimulator.domain.QuestionStimulus
import com.example.cardiosimulator.domain.Test
import com.example.cardiosimulator.domain.TestQuestion
import kotlinx.serialization.Serializable
import java.util.Random

@Serializable
data class QuizQuestionDto(
    val id: String,
    val text: String,
    val stimulus: QuestionStimulus,
    val options: List<QuizOptionDto>,
    val imagePath: String? = null,
    val pathologyId: String? = null
)

@Serializable
data class QuizOptionDto(val id: String, val text: String)

enum class TestGenType { Questions, Image, Detect, Assemble, Clinical }

object TestGenerator {
    fun generate(
        bank: List<TestQuestion>,
        count: Int,
        theme: String? = null,
        seed: Long = System.currentTimeMillis(),
        types: Set<TestGenType> = TestGenType.values().toSet(),
        themes: Set<String> = emptySet(),
        rhythms: Set<String> = emptySet(),
        minutes: Int = 0,
        isOrMode: Boolean = true,
    ): Test {
        val rng = Random(seed)
        
        val targetThemes = if (theme != null) themes + theme else themes

        // 1. Filter by types
        val typeFiltered = bank.filter { q ->
            types.any { type ->
                when (type) {
                    TestGenType.Questions -> !q.isAssembly && (q.stimulus == QuestionStimulus.Text || q.stimulus == QuestionStimulus.Ecg)
                    TestGenType.Image -> q.stimulus == QuestionStimulus.Image
                    TestGenType.Detect -> q.stimulus == QuestionStimulus.Ecg && !q.isAssembly
                    TestGenType.Assemble -> q.isAssembly
                    TestGenType.Clinical -> q.stimulus == QuestionStimulus.Text && !q.isAssembly
                }
            }
        }

        // 2. Filter by topics (themes and rhythms)
        var topicFiltered = typeFiltered.filter { q ->
            val inThemes = targetThemes.isEmpty() || targetThemes.any { it.equals(q.theme, ignoreCase = true) }
            val inRhythms = rhythms.isEmpty() || rhythms.any { it == q.pathologyId }

            if (targetThemes.isNotEmpty() && rhythms.isNotEmpty()) {
                if (isOrMode) inThemes || inRhythms else inThemes && inRhythms
            } else if (targetThemes.isNotEmpty()) {
                inThemes
            } else if (rhythms.isNotEmpty()) {
                inRhythms
            } else {
                true // None selected -> all match
            }
        }

        // Fallback to whole type-filtered bank if theme/rhythm filter yields nothing
        if (topicFiltered.isEmpty() && (targetThemes.isNotEmpty() || rhythms.isNotEmpty())) {
            topicFiltered = typeFiltered
        }

        val selected = topicFiltered.shuffled(rng).take(count)
        
        val questions = selected.mapIndexed { index, q ->
            q.copy(
                number = index + 1,
                id = "gen_q_${rng.nextInt().let { if (it < 0) -it else it }}"
            )
        }
        
        val questionTimeSeconds = if (minutes > 0 && questions.isNotEmpty()) {
            (minutes * 60) / questions.size
        } else 0
        
        val id = "gen_" + Integer.toHexString(rng.nextInt())
        
        val joinedTopics = (targetThemes + rhythms).joinToString(", ")
        val title = if (joinedTopics.isNotEmpty()) {
            if (joinedTopics.length > 70) joinedTopics.take(67) + "..." else joinedTopics
        } else {
            "Test ($count questions)"
        }
        
        return Test(id, title, questions, questionTimeSeconds)
    }

    fun toPublicDto(test: Test): List<QuizQuestionDto> {
        return test.questions.map { q ->
            QuizQuestionDto(
                id = q.id,
                text = q.text,
                stimulus = q.stimulus,
                options = q.options.map { QuizOptionDto(it.id, it.text) },
                imagePath = q.imagePath,
                pathologyId = q.pathologyId
            )
        }
    }
}
