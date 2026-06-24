package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.QuestionBankRepository
import com.example.cardiosimulator.data.TestRepository
import com.example.cardiosimulator.data.TestThemeStore
import com.example.cardiosimulator.data.testJson
import com.example.cardiosimulator.domain.*
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class ConstructorTab { TEST, BANK }

class TestConstructorViewModel(
    private val repository: TestRepository,
    private val bankRepository: QuestionBankRepository,
    private val themeStore: TestThemeStore
) : ViewModel() {

    private val _activeTab = MutableStateFlow(ConstructorTab.TEST)
    val activeTab: StateFlow<ConstructorTab> = _activeTab.asStateFlow()

    // Test editing state
    private val _testId = MutableStateFlow("")
    val testId: StateFlow<String> = _testId.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _questionTimeSeconds = MutableStateFlow(0)
    val questionTimeSeconds: StateFlow<Int> = _questionTimeSeconds.asStateFlow()

    private val _questions = MutableStateFlow<List<TestQuestion>>(emptyList())
    val questions: StateFlow<List<TestQuestion>> = _questions.asStateFlow()

    // Bank state
    private val _bankQuestions = MutableStateFlow<List<TestQuestion>>(emptyList())
    val bankQuestions: StateFlow<List<TestQuestion>> = _bankQuestions.asStateFlow()

    private val _themes = MutableStateFlow<List<String>>(emptyList())
    val themes: StateFlow<List<String>> = _themes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTheme = MutableStateFlow<String?>(null)
    val selectedTheme: StateFlow<String?> = _selectedTheme.asStateFlow()

    init {
        reloadBank()
        reloadThemes()
    }

    fun setTab(tab: ConstructorTab) {
        _activeTab.value = tab
    }

    fun reloadBank() {
        _bankQuestions.value = bankRepository.questions()
    }

    fun reloadThemes() {
        _themes.value = themeStore.readThemes()
    }

    fun newTest() {
        _testId.value = generateId()
        _title.value = "Новый тест"
        _questionTimeSeconds.value = 0
        _questions.value = emptyList()
        _activeTab.value = ConstructorTab.TEST
    }

    fun load(testId: String) {
        val test = repository.test(testId) ?: return
        _testId.value = test.testId
        _title.value = test.title
        _questionTimeSeconds.value = test.questionTimeSeconds
        _questions.value = test.questions
        _activeTab.value = ConstructorTab.TEST
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
            text = "",
            options = listOf(
                TestOption(generateId(), "Опция 1"),
                TestOption(generateId(), "Опция 2")
            ),
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
            if (q.options.size >= 6) return@updateQuestion q
            q.copy(options = q.options + TestOption(generateId(), ""))
        }
    }

    fun removeOption(questionId: String, optionId: String) {
        updateQuestion(questionId) { q ->
            if (q.options.size <= 2) return@updateQuestion q
            val newOptions = q.options.filterNot { it.id == optionId }
            val newCorrectId = if (q.correctOptionId == optionId) "" else q.correctOptionId
            q.copy(options = newOptions, correctOptionId = newCorrectId)
        }
    }

    fun saveTest(): Boolean {
        if (_testId.value.isBlank()) return false
        val test = Test(
            testId = _testId.value,
            title = _title.value,
            questions = _questions.value,
            questionTimeSeconds = _questionTimeSeconds.value
        )
        return repository.writeTest(test)
    }

    fun deleteTest(): Boolean {
        return repository.deleteTest(_testId.value)
    }

    // Bank operations
    fun saveToBank(question: TestQuestion) {
        bankRepository.writeQuestion(question)
        reloadBank()
    }

    fun deleteFromBank(id: String) {
        bankRepository.deleteQuestion(id)
        reloadBank()
    }

    fun addFromBank(question: TestQuestion) {
        val nextNumber = (_questions.value.maxOfOrNull { it.number } ?: 0) + 1
        val newQuestion = question.copy(id = generateId(), number = nextNumber)
        _questions.value = _questions.value + newQuestion
    }

    fun updateBankQuestion(id: String, transform: (TestQuestion) -> TestQuestion) {
        val q = bankRepository.questions().find { it.id == id } ?: return
        bankRepository.writeQuestion(transform(q))
        reloadBank()
    }

    fun importBank(json: String) {
        runCatching {
            val questions = testJson.decodeFromString<List<TestQuestion>>(json)
            bankRepository.import(questions)
            reloadBank()
        }
    }

    fun exportBank(): String {
        return bankRepository.exportAll()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTheme(theme: String?) {
        _selectedTheme.value = theme
    }

    fun addTheme(theme: String) {
        val newThemes = (_themes.value + theme).distinct()
        themeStore.writeThemes(newThemes)
        reloadThemes()
    }

    fun deleteTheme(theme: String) {
        val newThemes = _themes.value.filterNot { it == theme }
        themeStore.writeThemes(newThemes)
        reloadThemes()
    }

    private fun generateId(): String = UUID.randomUUID().toString().take(8)
}
