package com.example.cardiosimulator.domain

import kotlinx.serialization.Serializable

@Serializable
data class ExamStudentInfo(val fullName: String, val group: String)

@Serializable
data class ExamQuestionResult(
    val questionId: String,
    val selected: String?,
    val correct: String,
    val isCorrect: Boolean
)

@Serializable
data class ExamResult(
    val student: ExamStudentInfo,
    val testId: String,
    val testTitle: String,
    val timestamp: Long,
    val questions: List<ExamQuestionResult>,
    val correctCount: Int,
    val totalCount: Int,
    val passed: Boolean,
)

object ExamGrader {
    const val PASS_FRACTION = 0.6

    fun grade(
        test: Test,
        selections: Map<String, String>,
        student: ExamStudentInfo
    ): ExamResult {
        val questions = test.questions.map { question ->
            val selected = selections[question.id]
            val correct = question.correctOptionId
            val isCorrect = selected == correct
            ExamQuestionResult(question.id, selected, correct, isCorrect)
        }

        val correctCount = questions.count { it.isCorrect }
        val totalCount = test.questions.size
        val passed = if (totalCount > 0) {
            correctCount.toDouble() / totalCount >= PASS_FRACTION
        } else true

        return ExamResult(
            student = student,
            testId = test.testId,
            testTitle = test.title,
            timestamp = System.currentTimeMillis(),
            questions = questions,
            correctCount = correctCount,
            totalCount = totalCount,
            passed = passed
        )
    }
}
