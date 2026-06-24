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

object TestGenerator {
    fun generate(
        bank: List<TestQuestion>,
        count: Int,
        theme: String? = null,
        seed: Long = System.currentTimeMillis()
    ): Test {
        val rng = Random(seed)
        val pool = bank.filter { theme == null || it.theme == theme }.shuffled(rng)
        val selected = pool.take(count)
        
        val questions = selected.mapIndexed { index, q ->
            q.copy(number = index + 1)
        }
        
        val id = "gen_" + Integer.toHexString(rng.nextInt())
        val title = (if (theme != null) "$theme — " else "") + "Тест ($count вопросов)"
        
        return Test(id, title, questions)
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
