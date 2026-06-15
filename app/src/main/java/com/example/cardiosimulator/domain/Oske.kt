package com.example.cardiosimulator.domain

import java.util.Date

enum class OskeSpecialty { Therapy, Cardiology, FunctionalDiagnostics }

enum class OskeAnswerKind { Single, Multi }

data class OskeOption(val id: String, val text: String)

data class OskeQuestion(
    val id: String,
    val number: Int,
    val title: String,
    val kind: OskeAnswerKind,
    val options: List<OskeOption>
)

data class OskeForm(
    val formId: String,
    val specialty: OskeSpecialty,
    val version: String,
    val questions: List<OskeQuestion>,
    val passFraction: Double = 1.0
)

data class OskeAnswerKey(
    val ecgId: String,
    val formId: String,
    val correctOptionIds: Map<String, List<String>>
)

data class OskeStudentInfo(val fullName: String, val group: String)

data class OskeBlockResult(
    val questionId: String,
    val selected: List<String>,
    val correct: List<String>,
    val isCorrect: Boolean
)

data class OskeResult(
    val student: OskeStudentInfo,
    val specialty: OskeSpecialty,
    val ecgId: String,
    val formId: String,
    val timestamp: Long,
    val blocks: List<OskeBlockResult>,
    val correctCount: Int,
    val totalCount: Int,
    val passed: Boolean
)

data class OskeManifest(
    val version: String,
    val entries: List<OskeManifestEntry>
)

data class OskeManifestEntry(
    val formId: String,
    val version: String,
    val answerKeyCount: Int
)

object OskeGrader {
    fun grade(
        form: OskeForm,
        key: OskeAnswerKey,
        studentSelections: Map<String, List<String>>,
        student: OskeStudentInfo,
        ecgId: String
    ): OskeResult {
        val blocks = form.questions.map { question ->
            val selected = studentSelections[question.id] ?: emptyList()
            val correct = key.correctOptionIds[question.id] ?: emptyList()
            val isCorrect = selected.toSet() == correct.toSet()
            OskeBlockResult(question.id, selected, correct, isCorrect)
        }

        val correctCount = blocks.count { it.isCorrect }
        val totalCount = form.questions.size
        val passed = if (totalCount > 0) {
            correctCount.toDouble() / totalCount >= form.passFraction
        } else true

        return OskeResult(
            student = student,
            specialty = form.specialty,
            ecgId = ecgId,
            formId = form.formId,
            timestamp = System.currentTimeMillis(),
            blocks = blocks,
            correctCount = correctCount,
            totalCount = totalCount,
            passed = passed
        )
    }
}
