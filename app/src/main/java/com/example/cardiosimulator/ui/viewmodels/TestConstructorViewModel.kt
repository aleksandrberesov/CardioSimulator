package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.EcgAssemblyBuilder
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.data.QuestionBankRepository
import com.example.cardiosimulator.data.TestRepository
import com.example.cardiosimulator.data.TestThemeStore
import com.example.cardiosimulator.data.testJson
import com.example.cardiosimulator.domain.*
import com.example.cardiosimulator.domain.generators.TestGenType
import com.example.cardiosimulator.domain.generators.TestGenerator
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class ConstructorTab { TEST, BANK, GENERATOR }

data class BankAcronym(
    val code: String,
    val name: String,
    val count: Int
)

class TestConstructorViewModel(
    private val repository: TestRepository,
    private val bankRepository: QuestionBankRepository,
    private val pathologyRepository: PathologyRepository,
    private val themeStore: TestThemeStore
) : ViewModel() {

    private val _activeTab = MutableStateFlow(ConstructorTab.GENERATOR)
    val activeTab: StateFlow<ConstructorTab> = _activeTab.asStateFlow()

    // Generator state
    private val _selectedGenTypes = MutableStateFlow<Set<TestGenType>>(setOf(TestGenType.Questions))
    val selectedGenTypes: StateFlow<Set<TestGenType>> = _selectedGenTypes.asStateFlow()

    private val _selectedGenThemes = MutableStateFlow<Set<String>>(emptySet())
    val selectedGenThemes: StateFlow<Set<String>> = _selectedGenThemes.asStateFlow()

    private val _selectedGenRhythms = MutableStateFlow<Set<String>>(emptySet())
    val selectedGenRhythms: StateFlow<Set<String>> = _selectedGenRhythms.asStateFlow()

    private val _isGenOrMode = MutableStateFlow(true)
    val isGenOrMode: StateFlow<Boolean> = _isGenOrMode.asStateFlow()

    private val _genCount = MutableStateFlow(10)
    val genCount: StateFlow<Int> = _genCount.asStateFlow()

    private val _genTimeMinutes = MutableStateFlow(15)
    val genTimeMinutes: StateFlow<Int> = _genTimeMinutes.asStateFlow()

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

    // Redesigned Bank state
    private val _editingQuestionId = MutableStateFlow<String?>(null)
    val editingQuestionId: StateFlow<String?> = _editingQuestionId.asStateFlow()

    private val _bankPage = MutableStateFlow(0)
    val bankPage: StateFlow<Int> = _bankPage.asStateFlow()

    private val _selectedBankRhythm = MutableStateFlow<String?>(null)
    val selectedBankRhythm: StateFlow<String?> = _selectedBankRhythm.asStateFlow()

    private val _selectedBankTypes = MutableStateFlow<Set<TestGenType>>(emptySet())
    val selectedBankTypes: StateFlow<Set<TestGenType>> = _selectedBankTypes.asStateFlow()

    val bankAcronyms: StateFlow<List<BankAcronym>> = _bankQuestions.map { questions ->
        val acronymCounts = mutableMapOf<String, Int>()
        val pathologies = pathologyRepository.pathologies()
        
        questions.forEach { q ->
            val acronyms = mutableSetOf<String>()
            acronyms.addAll(q.acronyms)
            q.pathologyId?.let { pid ->
                pathologies.find { it.id == pid }?.acronym?.let { 
                    acronyms.add(it)
                }
            }
            acronyms.forEach { acr ->
                val normalized = acr.trim().uppercase()
                if (normalized.isNotEmpty()) {
                    acronymCounts[normalized] = (acronymCounts[normalized] ?: 0) + 1
                }
            }
        }
        
        acronymCounts.map { (code, count) ->
            val entry = Taxonomy.shared.find(code)
            BankAcronym(
                code = code,
                name = entry?.nameRu ?: code,
                count = count
            )
        }.sortedBy { it.code }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBankQuestions: StateFlow<List<TestQuestion>> = combine(
        _bankQuestions, _searchQuery, _selectedTheme, _selectedBankRhythm, _selectedBankTypes
    ) { bank, query, theme, acronym, types ->
        val pathologies = pathologyRepository.pathologies()
        bank.filter { q ->
            val qAcronyms = q.acronyms.map { it.trim().uppercase() }.toMutableSet()
            q.pathologyId?.let { pid ->
                pathologies.find { it.id == pid }?.acronym?.let { 
                    qAcronyms.add(it.trim().uppercase())
                }
            }

            (theme == null || q.theme == theme) &&
            (acronym == null || qAcronyms.contains(acronym.trim().uppercase())) &&
            (types.isEmpty() || types.any { t ->
                when (t) {
                    TestGenType.Assemble -> q.isAssembly
                    TestGenType.Image -> q.stimulus == QuestionStimulus.Image
                    TestGenType.Detect -> q.stimulus == QuestionStimulus.Ecg
                    TestGenType.Questions -> q.stimulus == QuestionStimulus.Text
                    else -> true
                }
            }) &&
            (query.isBlank() || 
                q.id.contains(query, ignoreCase = true) ||
                q.text.contains(query, ignoreCase = true) ||
                q.theme?.contains(query, ignoreCase = true) == true ||
                q.pathologyId?.contains(query, ignoreCase = true) == true ||
                q.acronyms.any { it.contains(query, ignoreCase = true) } ||
                q.tagList.any { it.contains(query, ignoreCase = true) }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun updateAcronyms(id: String, acronyms: List<String>) {
        updateQuestion(id) { it.copy(acronyms = acronyms) }
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

    fun newBankQuestion() {
        val newQuestion = TestQuestion(
            id = generateId(),
            number = (_bankQuestions.value.maxOfOrNull { it.number } ?: 0) + 1,
            text = "Новый вопрос",
            options = listOf(
                TestOption(generateId(), "Опция 1"),
                TestOption(generateId(), "Опция 2")
            ),
            correctOptionId = "",
            comment = ""
        )
        bankRepository.writeQuestion(newQuestion)
        reloadBank()
        startEditingQuestion(newQuestion.id)
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

    fun updateBankAcronyms(id: String, acronyms: List<String>) {
        updateBankQuestion(id) { it.copy(acronyms = acronyms) }
    }

    fun toggleAssembly(questionId: String, isAssembly: Boolean) {
        updateQuestion(questionId) { q ->
            if (isAssembly) {
                if (q.assemble != null) q 
                else q.copy(
                    assemble = EcgAssembly(500, emptyList(), sliceLead = Lead.II),
                    options = emptyList(),
                    correctOptionId = ""
                )
            } else {
                q.copy(assemble = null)
            }
        }
    }

    fun buildAssembly(
        questionId: String, 
        repository: PathologyRepository, 
        sourceId: String, 
        lead: Lead, 
        partCount: Int
    ) {
        viewModelScope.launch {
            val built = EcgAssemblyBuilder.build(
                repository,
                sourceId,
                lead,
                partCount,
                500 // Assuming 500Hz baseline
            )
            updateQuestion(questionId) { q ->
                q.copy(assemble = built ?: EcgAssembly(500, emptyList(), sourceId, lead))
            }
        }
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
        _bankPage.value = 0
    }

    fun setSelectedTheme(theme: String?) {
        _selectedTheme.value = theme
        _bankPage.value = 0
    }

    fun setSelectedBankRhythm(rhythmId: String?) {
        _selectedBankRhythm.value = rhythmId
        _bankPage.value = 0
    }

    fun toggleBankType(type: TestGenType) {
        val current = _selectedBankTypes.value
        _selectedBankTypes.value = if (current.contains(type)) current - type else current + type
        _bankPage.value = 0
    }

    fun clearBankTypes() {
        _selectedBankTypes.value = emptySet()
        _bankPage.value = 0
    }

    fun setBankPage(page: Int) {
        _bankPage.value = page
    }

    fun startEditingQuestion(id: String) {
        _editingQuestionId.value = id
    }

    fun stopEditingQuestion() {
        _editingQuestionId.value = null
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

    // Generator operations
    fun toggleGenType(type: TestGenType) {
        val current = _selectedGenTypes.value
        _selectedGenTypes.value = if (current.contains(type)) current - type else current + type
    }

    fun toggleGenTheme(theme: String) {
        val current = _selectedGenThemes.value
        _selectedGenThemes.value = if (current.contains(theme)) current - theme else current + theme
    }

    fun toggleGenRhythm(rhythmId: String) {
        val current = _selectedGenRhythms.value
        _selectedGenRhythms.value = if (current.contains(rhythmId)) current - rhythmId else current + rhythmId
    }

    fun setGenOrMode(isOr: Boolean) {
        _isGenOrMode.value = isOr
    }

    fun setGenCount(count: Int) {
        _genCount.value = count
    }

    fun setGenTimeMinutes(minutes: Int) {
        _genTimeMinutes.value = minutes
    }

    fun generateTest() {
        val generated = TestGenerator.generate(
            bank = bankRepository.questions(),
            count = _genCount.value,
            types = _selectedGenTypes.value,
            themes = _selectedGenThemes.value,
            rhythms = _selectedGenRhythms.value,
            minutes = _genTimeMinutes.value,
            isOrMode = _isGenOrMode.value
        )
        repository.writeTest(generated)
        repository.reload()
        _activeTab.value = ConstructorTab.TEST
        load(generated.testId)
    }

    private fun generateId(): String = UUID.randomUUID().toString().take(8)
}
