package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.cardiosimulator.data.TestRepository
import com.example.cardiosimulator.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class TestConstructorViewModel(private val repository: TestRepository) : ViewModel() {

    private val _testId = MutableStateFlow("")
    val testId: StateFlow<String> = _testId.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _questionTimeSeconds = MutableStateFlow(0)
    val questionTimeSeconds: StateFlow<Int> = _questionTimeSeconds.asStateFlow()

    private val _questions = MutableStateFlow<List<TestQuestion>>(emptyList())
    val questions: StateFlow<List<TestQuestion>> = _questions.asStateFlow()

    fun newTest() {
        _testId.value = generateId()
        _title.value = "New Test"
        _questionTimeSeconds.value = 0
        _questions.value = emptyList()
    }

    fun load(testId: String) {
        val test = repository.test(testId) ?: return
        _testId.value = test.testId
        _title.value = test.title
        _questionTimeSeconds.value = test.questionTimeSeconds
        _questions.value = test.questions
    }

    fun setTitle(title: String) {
        _title.value = title
    }

    fun setQuestionTimeSeconds(seconds: Int) {
        _questionTimeSeconds.value = seconds
    }

    fun addQuestion() {
        val nextNumber = (_questions.value.maxOfOrNull { it.number } ?: 0) + 1
        val newQuestion = TestQuestion(
            id = generateId(),
            number = nextNumber,
            text = "New Question",
            options = listOf(TestOption(generateId(), "Option 1")),
            correctOptionId = "",
            comment = ""
        )
        _questions.value = _questions.value + newQuestion
    }

    fun removeQuestion(id: String) {
        _questions.value = _questions.value.filterNot { it.id == id }
            .mapIndexed { index, q -> q.copy(number = index + 1) }
    }

    fun updateQuestion(id: String, transform: (TestQuestion) -> TestQuestion) {
        _questions.value = _questions.value.map { if (it.id == id) transform(it) else it }
    }

    fun addOption(questionId: String) {
        updateQuestion(questionId) { q ->
            q.copy(options = q.options + TestOption(generateId(), "New Option"))
        }
    }

    fun removeOption(questionId: String, optionId: String) {
        updateQuestion(questionId) { q ->
            val newOptions = q.options.filterNot { it.id == optionId }
            val newCorrectId = if (q.correctOptionId == optionId) "" else q.correctOptionId
            q.copy(options = newOptions, correctOptionId = newCorrectId)
        }
    }

    fun save(): Boolean {
        if (_testId.value.isBlank()) return false
        val test = Test(
            testId = _testId.value,
            title = _title.value,
            questions = _questions.value,
            questionTimeSeconds = _questionTimeSeconds.value
        )
        return repository.writeTest(test)
    }

    fun delete(): Boolean {
        return repository.deleteTest(_testId.value)
    }

    private fun generateId(): String = UUID.randomUUID().toString().take(8)
}
