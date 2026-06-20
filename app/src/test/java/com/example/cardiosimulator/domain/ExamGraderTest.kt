package com.example.cardiosimulator.domain

import org.junit.Assert.*
import org.junit.Test

class ExamGraderTest {
    @Test
    fun `grade - all correct passes`() {
        val test = Test(
            "t1", "Title",
            questions = listOf(
                TestQuestion("q1", 1, "Q1", emptyList(), "o1", ""),
                TestQuestion("q2", 2, "Q2", emptyList(), "o2", "")
            )
        )
        val selections = mapOf("q1" to "o1", "q2" to "o2")
        val student = ExamStudentInfo("Name", "Group")

        val result = ExamGrader.grade(test, selections, student)

        assertEquals(2, result.correctCount)
        assertEquals(2, result.totalCount)
        assertTrue(result.passed)
        assertTrue(result.questions.all { it.isCorrect })
    }

    @Test
    fun `grade - unanswered fails`() {
        val test = Test(
            "t1", "Title",
            questions = listOf(
                TestQuestion("q1", 1, "Q1", emptyList(), "o1", ""),
                TestQuestion("q2", 2, "Q2", emptyList(), "o2", "")
            )
        )
        val selections = emptyMap<String, String>()
        val student = ExamStudentInfo("Name", "Group")

        val result = ExamGrader.grade(test, selections, student)

        assertEquals(0, result.correctCount)
        assertFalse(result.passed)
        assertEquals(null, result.questions[0].selected)
    }

    @Test
    fun `grade - wrong recorded`() {
        val test = Test(
            "t1", "Title",
            questions = listOf(
                TestQuestion("q1", 1, "Q1", emptyList(), "o1", "")
            )
        )
        val selections = mapOf("q1" to "wrong")
        val student = ExamStudentInfo("Name", "Group")

        val result = ExamGrader.grade(test, selections, student)

        assertEquals(0, result.correctCount)
        assertFalse(result.passed)
        assertEquals("wrong", result.questions[0].selected)
        assertEquals("o1", result.questions[0].correct)
    }
}
