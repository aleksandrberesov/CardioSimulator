package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.domain.ExamResult
import com.example.cardiosimulator.domain.ExamStudentInfo
import com.example.cardiosimulator.domain.MasteryReport
import com.example.cardiosimulator.domain.MasteryRollup
import com.example.cardiosimulator.domain.Taxonomy
import com.example.cardiosimulator.data.ExamResultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

@Serializable
enum class SectionStatus { Good, Warning, Critical }

@Serializable
enum class PlanTaskType { Critical, Growth, Fix }

@Serializable
data class LsSubtopic(
    val id: String,
    val name: String,
    var progress: Int
)

@Serializable
data class LsSection(
    val id: Int,
    val name: String,
    var progress: Int,
    var status: SectionStatus,
    val subtopics: List<LsSubtopic>
)

data class PlanTask(
    val id: String,
    val sectionId: Int,
    val sectionName: String,
    val subtopicId: String,
    val subtopicName: String,
    val type: PlanTaskType,
    val progress: Int
)

data class LearningScaleState(
    val sections: List<LsSection> = emptyList(),
    val tasks: List<PlanTask> = emptyList(),
    val globalProgress: Int = 0,
    val cases: Int = 0,
    val accuracy: String = "78.4",
    val accuracyChange: String = "▲2.1%",
    val rank: String = "#6",
    val avgSeconds: Int = 47,
    val hasInteracted: Boolean = false,
    val hasRealData: Boolean = false,
    val roster: List<ExamStudentInfo> = emptyList(),
    val selectedStudent: ExamStudentInfo? = null,
    val isDrawerOpen: Boolean = false
)

@Serializable
private data class StateDto(
    @SerialName("sections") val sections: List<SectionDto>? = null,
    @SerialName("completedTasks") val completedTasks: List<String>? = null
)

@Serializable
private data class SectionDto(
    @SerialName("id") val id: Int,
    @SerialName("progress") val progress: Int,
    @SerialName("status") val status: String? = null,
    @SerialName("subtopics") val subtopics: List<SubtopicDto>? = null
)

@Serializable
private data class SubtopicDto(
    @SerialName("id") val id: String,
    @SerialName("progress") val progress: Int
)

class LearningScaleViewModel(
    private val persistenceFile: File,
    private val examResultStore: ExamResultStore? = null,
    private val taxonomy: Taxonomy = Taxonomy.shared,
    private val initialStudent: ExamStudentInfo? = null
) : ViewModel() {

    private val _state = MutableStateFlow(LearningScaleState())
    val state: StateFlow<LearningScaleState> = _state.asStateFlow()

    private val _selectedStudent = MutableStateFlow<ExamStudentInfo?>(initialStudent)
    private val _isDrawerOpen = MutableStateFlow(false)

    private var _sections: List<LsSection> = seedCourse()
    private val _completed = mutableSetOf<String>()
    private var _hasInteracted = false
    private var _currentReport: MasteryReport = MasteryReport.Empty

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    init {
        load()
        if (examResultStore != null) {
            viewModelScope.launch {
                combine(
                    examResultStore.resultsChanged,
                    _selectedStudent
                ) { _, student ->
                    val allResults = examResultStore.list()
                    val filtered = if (student == null) {
                        allResults
                    } else {
                        allResults.filter { it.student.fullName == student.fullName && it.student.group == student.group }
                    }
                    val roster = allResults.map { it.student }.distinctBy { it.fullName + it.group }
                        .sortedBy { it.fullName }
                    
                    val report = MasteryRollup.compute(filtered, taxonomy)
                    Triple(report, roster, student)
                }.collectLatest { (report, roster, student) ->
                    _currentReport = report
                    if (report.hasData) {
                        applyReport(report)
                    } else {
                        // Reset to seed if no real data for this student
                        _sections = seedCourse()
                    }
                    updateState(roster, student)
                }
            }
        } else {
            updateState(emptyList(), null)
        }
    }

    private fun applyReport(report: MasteryReport) {
        for (section in _sections) {
            val sectionStat = report.section(section.id)
            for (sub in section.subtopics) {
                val subStat = report.subtopic(sub.id)
                sub.progress = if (subStat.answered > 0) subStat.progress else 0
            }
            
            val assessed = section.subtopics.filter { report.subtopic(it.id).answered > 0 }
            section.progress = if (assessed.isNotEmpty()) {
                assessed.map { it.progress }.average().roundToInt()
            } else {
                0
            }
            section.status = bandFor(section.progress)
        }
    }

    private fun updateState(roster: List<ExamStudentInfo> = emptyList(), student: ExamStudentInfo? = null) {
        val report = _currentReport
        val hasRealData = report.hasData
        
        val avgProgress = if (_sections.isEmpty()) 0.0 else _sections.map { it.progress }.average()
        
        val globalProgress = if (hasRealData) {
            if (report.totalAnswered > 0) (100.0 * report.totalCorrect / report.totalAnswered).roundToInt() else 0
        } else {
            avgProgress.roundToInt()
        }
        
        val cases = if (hasRealData) report.totalAnswered else 184 + _completed.size
        
        val accuracyValue = if (hasRealData) {
            if (report.totalAnswered > 0) 100.0 * report.totalCorrect / report.totalAnswered else 0.0
        } else {
            78.4 + (avgProgress - 50) * 0.2
        }
        
        val accuracy = String.format(Locale.US, "%.1f", accuracyValue)
        val accuracyChange = if (hasRealData) {
            "▲0.0%"
        } else if (_completed.isNotEmpty()) {
            "▲" + String.format(Locale.US, "%.1f", 2.1 + _completed.size * 0.1) + "%"
        } else {
            "▲2.1%"
        }
        
        val rankIdx = if (hasRealData) {
            (accuracyValue / 15).toInt().coerceAtMost(6)
        } else {
            (floor(_completed.size / 1.5)).toInt().coerceAtMost(6)
        }
        val rank = if (rankIdx >= 6) "🏆" else "#${6 - rankIdx}"

        _state.value = LearningScaleState(
            sections = _sections.map { it.copy(subtopics = it.subtopics.map { sub -> sub.copy() }) },
            tasks = generateTasks(),
            globalProgress = globalProgress,
            cases = cases,
            accuracy = accuracy,
            accuracyChange = accuracyChange,
            rank = rank,
            avgSeconds = 47,
            hasInteracted = _hasInteracted,
            hasRealData = hasRealData,
            roster = roster,
            selectedStudent = student,
            isDrawerOpen = _isDrawerOpen.value
        )
    }

    fun selectStudent(student: ExamStudentInfo?) {
        _selectedStudent.value = student
    }

    fun setDrawerOpen(open: Boolean) {
        _isDrawerOpen.value = open
        updateState(_state.value.roster, _state.value.selectedStudent)
    }

    private fun generateTasks(): List<PlanTask> {
        // Adaptive plan only recommends assessed subtopics when real data is present?
        // Actually the plan says: "the adaptive plan only recommends assessed subtopics"
        val report = _currentReport
        val hasRealData = report.hasData

        val all = _sections.flatMap { section ->
            section.subtopics.map { sub -> section to sub }
        }.filter { (section, sub) ->
            if (hasRealData) report.subtopic(sub.id).answered > 0 else true
        }.sortedBy { it.second.progress }

        val critical = all.filter { it.second.progress < 30 }.take(3)
            .map { makeTask(it.first, it.second, PlanTaskType.Critical, "c") }
        
        val growth = all.filter { it.second.progress in 30..59 }.take(2)
            .map { makeTask(it.first, it.second, PlanTaskType.Growth, "g") }
            
        val fix = all.filter { it.second.progress >= 70 }.take(2)
            .map { makeTask(it.first, it.second, PlanTaskType.Fix, "f") }

        val tasks = (critical + growth + fix).filter { !isCompleted(it.id) }
        
        val order = mapOf(PlanTaskType.Critical to 0, PlanTaskType.Growth to 1, PlanTaskType.Fix to 2)
        return tasks.sortedBy { order[it.type] ?: 3 }
    }

    private fun makeTask(section: LsSection, sub: LsSubtopic, type: PlanTaskType, prefix: String): PlanTask {
        return PlanTask(
            id = "$prefix-${section.id}-${sub.id}",
            sectionId = section.id,
            sectionName = section.name,
            subtopicId = sub.id,
            subtopicName = sub.name,
            type = type,
            progress = sub.progress
        )
    }

    fun isCompleted(taskId: String): Boolean = _completed.contains(taskId)

    /**
     * Marks a task solved: bumps its subtopic (+8, capped at 100), recomputes the section's
     * aggregate + band, persists, and notifies. Returns the section's new progress.
     */
    fun markDone(taskId: String): Int? {
        if (_completed.contains(taskId)) return null
        
        val currentTasks = generateTasks()
        val task = currentTasks.find { it.id == taskId } ?: return null

        _completed.add(taskId)
        _hasInteracted = true

        val section = _sections.find { it.id == task.sectionId } ?: run {
            save()
            updateState()
            return null
        }

        val sub = section.subtopics.find { it.id == task.subtopicId }
        if (sub != null && !_currentReport.hasData) {
            sub.progress = (sub.progress + 8).coerceAtMost(100)
        }

        section.progress = section.subtopics.map { it.progress }.average().roundToInt()
        section.status = bandFor(section.progress)

        save()
        updateState()
        return section.progress
    }

    private fun bandFor(progress: Int): SectionStatus = when {
        progress >= 80 -> SectionStatus.Good
        progress >= 50 -> SectionStatus.Warning
        else -> SectionStatus.Critical
    }

    private fun load() {
        try {
            if (!persistenceFile.exists()) return
            val text = persistenceFile.readText()
            val dto = json.decodeFromString<StateDto>(text)

            dto.sections?.forEach { sDto ->
                val section = _sections.find { it.id == sDto.id }
                if (section != null) {
                    section.progress = sDto.progress
                    section.status = try {
                        SectionStatus.valueOf(sDto.status?.replaceFirstChar { it.uppercase() } ?: "Critical")
                    } catch (e: Exception) {
                        section.status
                    }
                    sDto.subtopics?.forEach { subDto ->
                        val sub = section.subtopics.find { it.id == subDto.id }
                        if (sub != null) {
                            sub.progress = subDto.progress
                        }
                    }
                }
            }

            dto.completedTasks?.let {
                _completed.addAll(it)
            }
        } catch (e: Exception) {
            // Fallback to seed on error
        }
    }

    private fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dto = StateDto(
                    sections = _sections.map { s ->
                        SectionDto(
                            id = s.id,
                            progress = s.progress,
                            status = s.status.name.lowercase(),
                            subtopics = s.subtopics.map { sub -> SubtopicDto(sub.id, sub.progress) }
                        )
                    },
                    completedTasks = _completed.toList()
                )
                val text = json.encodeToString(dto)
                persistenceFile.writeText(text)
            } catch (e: Exception) {
                // Best-effort
            }
        }
    }

    private fun isTableOfContents(subName: String?, sectionName: String?): Boolean {
        if (subName.isNullOrBlank() || sectionName.isNullOrBlank()) return true

        // Check for leading digits/numeration (e.g. "1.1", "4.6.1", "1.")
        val leadingNumeration = Regex("""^\d+(\.\d+)*\.?\s+""")
        if (leadingNumeration.containsMatchIn(subName)) return false

        // If no numeration, exclude if it repeats the section name
        return sectionName.contains(subName, ignoreCase = true)
    }

    private fun seedCourse(): List<LsSection> = listOf(
        LsSection(1, "Теоретические основы и техника регистрации ЭКГ", 92, SectionStatus.Good, listOf(
            LsSubtopic("1.1", "Мембранная теория биопотенциалов", 95),
            LsSubtopic("1.2", "Основные функции сердца", 90),
            LsSubtopic("1.3", "Формирование нормальной ЭКГ", 88),
            LsSubtopic("1.4", "ЭКГ-аппаратура", 92),
            LsSubtopic("1.5", "ЭКГ-отведения", 90),
            LsSubtopic("1.6", "Техника регистрации ЭКГ", 94),
            LsSubtopic("1.7", "Функциональные пробы", 85),
            LsSubtopic("1.8", "Дополнительные методы", 80)
        ).filter { !isTableOfContents(it.name, "Теоретические основы и техника регистрации ЭКГ") }),
        LsSection(2, "Анализ нормальной ЭКГ", 85, SectionStatus.Good, listOf(
            LsSubtopic("2.1", "Зуबेц P", 88),
            LsSubtopic("2.2", "Интервал P–Q(R)", 85),
            LsSubtopic("2.3", "Желудочковый комплекс QRST", 82),
            LsSubtopic("2.4", "Анализ ритма и проводимости", 80),
            LsSubtopic("2.5", "Повороты сердца вокруг осей", 78),
            LsSubtopic("2.6", "Анализ предсердного зубца P", 90),
            LsSubtopic("2.7", "Анализ желудочкового комплекса", 85),
            LsSubtopic("2.8", "ЭКГ-заключение", 82)
        ).filter { !isTableOfContents(it.name, "Анализ нормальной ЭКГ") }),
        LsSection(3, "Нарушения ритма сердца", 45, SectionStatus.Warning, listOf(
            LsSubtopic("3.1", "Нарушения автоматизма СА-узла", 55),
            LsSubtopic("3.2", "Эктопические (гетеротопные) ритмы", 45),
            LsSubtopic("3.3", "Экстрасистолия", 40),
            LsSubtopic("3.4", "Пароксизмальная тахикардия", 35),
            LsSubtopic("3.5", "Трепетание предсердий", 30),
            LsSubtopic("3.6", "Фибрилляция предсердий", 25),
            LsSubtopic("3.7", "Трепетание и фибрилляция желудочков", 20),
            LsSubtopic("3.8", "Холтеровское мониторирование", 50)
        ).filter { !isTableOfContents(it.name, "Нарушения ритма сердца") }),
        LsSection(4, "Нарушения функции проводимости", 30, SectionStatus.Critical, listOf(
            LsSubtopic("4.1", "Синдром слабости СА-узла", 35),
            LsSubtopic("4.2", "Синоатриальная блокада", 30),
            LsSubtopic("4.3", "Остановка СА-узла", 25),
            LsSubtopic("4.4", "Синдром брадикардии-тахикардии", 28),
            LsSubtopic("4.5", "Межпредсердная блокада", 20),
            LsSubtopic("4.6", "АВ-блокады (I, II, III степени)", 25),
            LsSubtopic("4.7", "Синдром Морганьи–Адамса–Стокса", 30),
            LsSubtopic("4.8", "Синдром Фредерика", 20),
            LsSubtopic("4.9", "Электрограмма пучка Гиса", 15),
            LsSubtopic("4.10", "Блокады ножек пучка Гиса", 25),
            LsSubtopic("4.11", "Синдромы преждевременного возбуждения", 20)
        ).filter { !isTableOfContents(it.name, "Нарушения функции проводимости") }),
        LsSection(5, "Гипертрофия предсердий и желудочков", 65, SectionStatus.Warning, listOf(
            LsSubtopic("5.1", "Гипертрофия левого предсердия", 70),
            LsSubtopic("5.2", "Гипертрофия правого предсердия", 65),
            LsSubtopic("5.3", "Перегрузка предсердий", 60),
            LsSubtopic("5.4", "Гипертрофия левого желудочка", 68),
            LsSubtopic("5.5", "Гиपरтрофия правого желудочка", 55),
            LsSubtopic("5.6", "Комбинированная гипертрофия", 50),
            LsSubtopic("5.7", "Перегрузка желудочков", 60)
        ).filter { !isTableOfContents(it.name, "Гиपरтрофия предсердий и желудочков") }),
        LsSection(6, "Ишемическая болезнь сердца и инфаркт", 55, SectionStatus.Warning, listOf(
            LsSubtopic("6.1", "Общие сведения об ИБС", 65),
            LsSubtopic("6.2", "ЭКГ при ишемии и повреждении", 55),
            LsSubtopic("6.3", "ЭКГ при остром ИМ с ST↑", 50),
            LsSubtopic("6.4", "Локализация ИМ (передняя/задняя)", 45),
            LsSubtopic("6.5", "Аневризма сердца", 40),
            LsSubtopic("6.6", "ИМ без подъема ST", 45),
            LsSubtopic("6.7", "Нестабильная стенокардия", 55),
            LsSubtopic("6.8", "Стабильная стенокардия", 60),
            LsSubtopic("6.9", "Хроническая ИБС", 50)
        ).filter { !isTableOfContents(it.name, "Ишемическая болезнь сердца и инфаркт") }),
        LsSection(7, "ЭКГ при заболеваниях сердца и синдромах", 20, SectionStatus.Critical, listOf(
            LsSubtopic("7.1", "Приобретенные пороки сердца", 25),
            LsSubtopic("7.2", "Острое легочное сердце", 20),
            LsSubtopic("7.3", "Перикардиты", 18),
            LsSubtopic("7.4", "Миокардиты", 15),
            LsSubtopic("7.5", "Нарушения электролитного обмена", 20),
            LsSubtopic("7.6", "Передозировка гликозидов", 10),
            LsSubtopic("7.7", "Имплантированный ЭКС", 15)
        ).filter { !isTableOfContents(it.name, "ЭКГ при заболеваниях сердца и синдромах") })
    )
}
