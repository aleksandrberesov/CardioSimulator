package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.CourseRepository
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.CourseParser
import com.example.cardiosimulator.domain.HtmlBlock
import com.example.cardiosimulator.domain.HtmlCompiler
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.domain.LectureEntry
import com.example.cardiosimulator.domain.LectureFrontMatter
import com.example.cardiosimulator.domain.OperatingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Collections

enum class ConstructorViewMode { EDITOR, PREVIEW, BOTH }

/**
 * Course Constructor editing state (Phase 3 of
 * docs/plans/active/2026-05-course-constructor.md). The author edits the
 * raw lecture source (front matter + HTML body) as text; a debounced parse
 * feeds the live `LectureWebView` preview, and [save] writes the source
 * back verbatim via `CourseRepository.writeLectureRaw` plus any editable
 * quiz-table answers as the sibling `.answers.json`.
 *
 * Per-mode keyed. Disk reads/writes run on [Dispatchers.IO]; parsing for
 * the preview runs on [Dispatchers.Default].
 */
class CourseConstructorViewModel(
    val repository: CourseRepository,
    val mode: OperatingMode,
    val prefs: DataSourcePrefs?,
) : ViewModel() {

    private val _selectedCourseId = MutableStateFlow<String?>(null)
    val selectedCourseId: StateFlow<String?> = _selectedCourseId.asStateFlow()

    private val _lectures = MutableStateFlow<List<LectureEntry>>(emptyList())
    val lectures: StateFlow<List<LectureEntry>> = _lectures.asStateFlow()

    private val _selectedLectureId = MutableStateFlow<String?>(null)
    val selectedLectureId: StateFlow<String?> = _selectedLectureId.asStateFlow()

    /** Editable source text: front matter + HTML body, exactly as saved to disk. */
    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _savedText = MutableStateFlow("")

    /** Editable-table cell answers: quizId → ("row,col" → value). */
    private val _answers = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val answers: StateFlow<Map<String, Map<String, String>>> = _answers.asStateFlow()

    private val _savedAnswers = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Debounced parse of [draft] for the live preview. */
    private val _previewLecture = MutableStateFlow<Lecture?>(null)
    val previewLecture: StateFlow<Lecture?> = _previewLecture.asStateFlow()

    private val _blocks = MutableStateFlow<List<HtmlBlock>>(emptyList())
    val blocks: StateFlow<List<HtmlBlock>> = _blocks.asStateFlow()

    private val _focusedBlockId = MutableStateFlow<String?>(null)
    val focusedBlockId: StateFlow<String?> = _focusedBlockId.asStateFlow()

    val isDirty: StateFlow<Boolean> =
        combine(_draft, _savedText, _answers, _savedAnswers) { draft, saved, ans, savedAns ->
            draft != saved || ans != savedAns
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    private val _viewMode = MutableStateFlow(ConstructorViewMode.BOTH)
    val viewMode: StateFlow<ConstructorViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: ConstructorViewMode) {
        _viewMode.value = mode
    }

    private var languageTag: String = "en"
    private var loadedLang: String = "en"
    private var previewJob: Job? = null
    private var restored = false

    fun setLanguage(tag: String) { languageTag = tag }

    fun selectCourse(courseId: String) {
        if (_selectedCourseId.value == courseId) return
        _selectedCourseId.value = courseId
        clearLecture()
        viewModelScope.launch {
            prefs?.setLastCourseId(courseId)
            val entries = withContext(Dispatchers.IO) { repository.lectureEntries(courseId) }
            _lectures.value = entries
            entries.firstOrNull()?.let { selectLecture(it.id) }
        }
    }

    fun selectLecture(lectureId: String) {
        val courseId = _selectedCourseId.value ?: return
        _selectedLectureId.value = lectureId
        _focusedBlockId.value = null
        viewModelScope.launch {
            prefs?.setLastLectureId(mode.name, lectureId)
            val lecture = withContext(Dispatchers.IO) {
                repository.readLecture(courseId, lectureId, languageTag)
            }
            loadedLang = lecture?.language ?: languageTag
            val text = lecture?.let { CourseParser.serializeLecture(it) } ?: ""
            val decoded = decodeAnswers(
                withContext(Dispatchers.IO) { repository.readAnswers(courseId, lectureId, loadedLang) }
            )
            _draft.value = text
            _savedText.value = text
            _answers.value = decoded
            _savedAnswers.value = decoded
            _previewLecture.value = lecture
            _blocks.value = HtmlCompiler.parse(lecture?.rawHtml ?: "")
        }
    }

    fun setHtml(text: String) {
        _draft.value = text
        val courseId = _selectedCourseId.value ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val parsed = withContext(Dispatchers.Default) {
                runCatching { CourseParser.parseLecture(text, courseId, loadedLang) }.getOrNull()
            }
            if (parsed != null) {
                _previewLecture.value = parsed
                // Sync blocks if they weren't just edited visually (or always sync if preferred)
                // For now, we only sync blocks on initial load or if text is edited manually.
                // But wait, if the user edits text, blocks should update too.
                _blocks.value = HtmlCompiler.parse(parsed.rawHtml)
            }
        }
    }

    /** Updates blocks from UI and compiles back to [draft]. */
    fun setBlocks(newBlocks: List<HtmlBlock>) {
        _blocks.value = newBlocks
        val newHtml = HtmlCompiler.compile(newBlocks)
        val currentLec = _previewLecture.value ?: return
        val updatedLec = currentLec.copy(rawHtml = newHtml)
        _previewLecture.value = updatedLec
        _draft.value = CourseParser.serializeLecture(updatedLec)
    }

    fun addBlock(block: HtmlBlock) {
        setBlocks(_blocks.value + block)
        _focusedBlockId.value = block.id
    }

    fun clearFocusedBlockId() {
        _focusedBlockId.value = null
    }

    fun updateBlock(id: String, updated: HtmlBlock) {
        _focusedBlockId.value = id
        setBlocks(_blocks.value.map { if (it.id == id) updated else it })
    }

    /**
     * Saves an image to the course's asset folder and returns the relative path.
     */
    fun importImage(fileName: String, bytes: ByteArray): String? {
        val courseId = _selectedCourseId.value ?: return null
        val ok = repository.importAsset(courseId, fileName, bytes)
        return if (ok) "assets/$fileName" else null
    }

    fun deleteBlock(id: String) {
        setBlocks(_blocks.value.filterNot { it.id == id })
    }

    fun moveBlock(id: String, delta: Int) {
        val current = _blocks.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return
        val newIndex = index + delta
        if (newIndex in current.indices) {
            Collections.swap(current, index, newIndex)
            setBlocks(current)
        }
    }

    /** Records an edited quiz cell; persisted to `.answers.json` on [save]. */
    fun setTableCell(quizId: String, row: Int, col: Int, value: String) {
        val key = "$row,$col"
        val current = _answers.value
        val cells = current[quizId].orEmpty().toMutableMap().apply { put(key, value) }
        _answers.value = current.toMutableMap().apply { put(quizId, cells) }
    }

    fun importFullPage(html: String) {
        val courseId = _selectedCourseId.value ?: return
        val isFull = html.contains("<!doctype", ignoreCase = true) || html.contains("<html", ignoreCase = true)
        val currentLecture = _previewLecture.value

        val newFm = if (isFull) {
            val fm = currentLecture?.frontMatter ?: LectureFrontMatter(id = _selectedLectureId.value ?: "new_lecture")
            fm.copy(extras = fm.extras + ("layout" to "standalone"))
        } else {
            currentLecture?.frontMatter ?: LectureFrontMatter(id = _selectedLectureId.value ?: "new_lecture")
        }

        val newLecture = currentLecture?.copy(frontMatter = newFm, rawHtml = html)
            ?: Lecture(
                id = newFm.id,
                courseId = courseId,
                language = languageTag,
                frontMatter = newFm,
                rawHtml = html
            )

        setHtml(CourseParser.serializeLecture(newLecture))
    }

    fun revert() {
        _answers.value = _savedAnswers.value
        setHtml(_savedText.value)
    }

    fun save() {
        val courseId = _selectedCourseId.value ?: return
        val lectureId = _selectedLectureId.value ?: return
        val text = _draft.value
        val answersSnapshot = _answers.value
        viewModelScope.launch {
            _isSaving.value = true
            val ok = withContext(Dispatchers.IO) {
                val wrote = repository.writeLectureRaw(courseId, lectureId, loadedLang, text)
                if (wrote && answersSnapshot.isNotEmpty()) {
                    repository.writeAnswers(courseId, lectureId, loadedLang, encodeAnswers(answersSnapshot))
                }
                wrote
            }
            if (ok) {
                _savedText.value = text
                _savedAnswers.value = answersSnapshot
            }
            _isSaving.value = false
        }
    }

    // ─── course/lecture management ──────────────────────────────────────

    fun createCourse(courseId: String, title: String) {
        val id = courseId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                repository.writeCourse(
                    Course(
                        id = id,
                        titleEn = title.trim().ifEmpty { id },
                        nameRu = null,
                        authors = null,
                        languages = listOf(languageTag),
                        lectures = emptyList(),
                    )
                )
            }
            if (ok) {
                _selectedCourseId.value = id
                _lectures.value = emptyList()
                clearLecture()
            }
        }
    }

    fun createLecture(lectureId: String, title: String) {
        val courseId = _selectedCourseId.value ?: return
        val id = lectureId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            val displayTitle = title.trim().ifEmpty { id }
            val body = CourseParser.serializeLecture(
                Lecture(
                    id = id,
                    courseId = courseId,
                    language = languageTag,
                    frontMatter = LectureFrontMatter(id = id, title = displayTitle),
                    rawHtml = "<h1>$displayTitle</h1>\n",
                )
            )
            val ok = withContext(Dispatchers.IO) {
                val wrote = repository.writeLectureRaw(courseId, id, languageTag, body)
                if (wrote) {
                    val course = repository.readCourse(courseId)
                    if (course != null && course.lectures.none { it.id == id }) {
                        repository.writeCourse(
                            course.copy(lectures = course.lectures + LectureEntry(id, displayTitle, null))
                        )
                    }
                }
                wrote
            }
            if (ok) {
                _lectures.value = withContext(Dispatchers.IO) { repository.lectureEntries(courseId) }
                selectLecture(id)
            }
        }
    }

    fun renameLecture(newTitle: String) {
        val courseId = _selectedCourseId.value ?: return
        val lectureId = _selectedLectureId.value ?: return
        val title = newTitle.trim()
        if (title.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.readCourse(courseId)?.let { course ->
                    repository.writeCourse(
                        course.copy(
                            lectures = course.lectures.map {
                                if (it.id == lectureId) it.copy(titleEn = title) else it
                            }
                        )
                    )
                }
                repository.readLecture(courseId, lectureId, loadedLang)?.let { lec ->
                    repository.writeLecture(lec.copy(frontMatter = lec.frontMatter.copy(title = title)))
                }
            }
            _lectures.value = withContext(Dispatchers.IO) { repository.lectureEntries(courseId) }
            selectLecture(lectureId)
        }
    }

    fun deleteLecture() {
        val courseId = _selectedCourseId.value ?: return
        val lectureId = _selectedLectureId.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteLecture(courseId, lectureId, loadedLang)
                repository.readCourse(courseId)?.let { course ->
                    repository.writeCourse(
                        course.copy(lectures = course.lectures.filterNot { it.id == lectureId })
                    )
                }
            }
            clearLecture()
            val entries = withContext(Dispatchers.IO) { repository.lectureEntries(courseId) }
            _lectures.value = entries
            entries.firstOrNull()?.let { selectLecture(it.id) }
        }
    }

    /** One-shot restore of the last edited course (lecture index only). */
    fun restore() {
        if (restored) return
        restored = true
        val p = prefs ?: return
        viewModelScope.launch {
            val courseId = p.lastCourseId.first() ?: return@launch
            _selectedCourseId.value = courseId
            _lectures.value = withContext(Dispatchers.IO) { repository.lectureEntries(courseId) }
        }
    }

    private fun clearLecture() {
        previewJob?.cancel()
        _selectedLectureId.value = null
        _focusedBlockId.value = null
        _draft.value = ""
        _savedText.value = ""
        _answers.value = emptyMap()
        _savedAnswers.value = emptyMap()
        _previewLecture.value = null
    }

    private fun encodeAnswers(answers: Map<String, Map<String, String>>): String {
        val root = JSONObject()
        for ((quizId, cells) in answers) {
            val obj = JSONObject()
            for ((key, value) in cells) obj.put(key, value)
            root.put(quizId, obj)
        }
        return root.toString()
    }

    private fun decodeAnswers(json: String?): Map<String, Map<String, String>> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            buildMap {
                for (quizId in root.keys()) {
                    val obj = root.getJSONObject(quizId)
                    val cells = buildMap<String, String> {
                        for (key in obj.keys()) put(key, obj.getString(key))
                    }
                    put(quizId, cells)
                }
            }
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 200L
    }
}
