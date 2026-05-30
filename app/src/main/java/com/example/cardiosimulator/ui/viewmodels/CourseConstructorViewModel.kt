package com.example.cardiosimulator.ui.viewmodels

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.CourseRepository
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.domain.LectureEntry
import com.example.cardiosimulator.domain.OperatingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages authoring courses and lectures in [OperatingMode.CourseConstructor].
 * Mirrors [ConstructorViewModel] for the courses pipeline.
 */
class CourseConstructorViewModel(
    private val repository: CourseRepository,
    private val prefs: DataSourcePrefs? = null,
    private val mode: String,
) : ViewModel() {

    private val _selectedCourseId = MutableStateFlow<String?>(null)
    val selectedCourseId: StateFlow<String?> = _selectedCourseId.asStateFlow()

    private val _selectedCourse = MutableStateFlow<Course?>(null)
    val selectedCourse: StateFlow<Course?> = _selectedCourse.asStateFlow()

    private val _selectedLectureId = MutableStateFlow<String?>(null)
    val selectedLectureId: StateFlow<String?> = _selectedLectureId.asStateFlow()

    /** The lecture currently being edited. */
    private val _targetLecture = mutableStateOf<Lecture?>(null)
    val targetLecture: State<Lecture?> = _targetLecture

    /**
     * IDs of lectures that have unsaved changes.
     * Format: "courseId/lectureId.lang"
     */
    private val _dirtyLectures = MutableStateFlow<Set<String>>(emptySet())
    val dirtyLectures: StateFlow<Set<String>> = _dirtyLectures.asStateFlow()

    private val _isMetadataDirty = MutableStateFlow(false)
    val isMetadataDirty: StateFlow<Boolean> = _isMetadataDirty.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /**
     * List of lectures in the currently selected course. Derived from
     * [selectedCourse].
     */
    val lectureEntries: StateFlow<List<LectureEntry>> = _selectedCourse
        .map { it?.lectures ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Restore last selection from disk on boot.
            prefs?.lastCourseId?.first()?.let { id ->
                selectCourse(id)
                prefs.lastLectureId(OperatingMode.CourseConstructor.name).first()?.let { lId ->
                    selectLecture(lId)
                }
            }
        }
    }

    fun selectCourse(id: String?) {
        if (_selectedCourseId.value == id) return
        _selectedCourseId.value = id
        _selectedCourse.value = id?.let { repository.readCourse(it) }
        _selectedLectureId.value = null
        _targetLecture.value = null
        viewModelScope.launch { prefs?.setLastCourseId(id) }
    }

    fun selectLecture(id: String?) {
        if (_selectedLectureId.value == id) return
        val courseId = _selectedCourseId.value ?: return
        
        // TODO: Handle unsaved changes check here or in UI
        
        _selectedLectureId.value = id
        if (id != null) {
            viewModelScope.launch {
                val lang = prefs?.languageTag?.first() ?: "en"
                _targetLecture.value = repository.readLecture(courseId, id, lang)
                prefs?.setLastLectureId(OperatingMode.CourseConstructor.name, id)
            }
        } else {
            _targetLecture.value = null
        }
    }

    fun setMarkdown(text: String) {
        val current = _targetLecture.value ?: return
        if (current.rawMarkdown == text) return
        
        _targetLecture.value = current.copy(rawMarkdown = text)
        markCurrentLectureDirty()
    }

    fun setTitle(title: String) {
        val current = _targetLecture.value ?: return
        if (current.frontMatter.title == title) return
        
        _targetLecture.value = current.copy(
            frontMatter = current.frontMatter.copy(title = title)
        )
        markCurrentLectureDirty()
    }

    private fun markCurrentLectureDirty() {
        val l = _targetLecture.value ?: return
        val key = "${l.courseId}/${l.id}.${l.language}"
        _dirtyLectures.value += key
    }

    fun revertLecture() {
        val current = _targetLecture.value ?: return
        viewModelScope.launch {
            val original = repository.readLecture(current.courseId, current.id, current.language)
            _targetLecture.value = original
            val key = "${current.courseId}/${current.id}.${current.language}"
            _dirtyLectures.value -= key
        }
    }

    fun save() {
        val lecture = _targetLecture.value ?: return
        val key = "${lecture.courseId}/${lecture.id}.${lecture.language}"
        if (!_dirtyLectures.value.contains(key) && !_isMetadataDirty.value) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val success = repository.writeLecture(lecture)
                if (success) {
                    _dirtyLectures.value -= key
                    // If course metadata changed, we'd save it here too.
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun createLecture(id: String, title: String) {
        val courseId = _selectedCourseId.value ?: return
        
        viewModelScope.launch {
            val lang = prefs?.languageTag?.first() ?: "en"
            
            val newLecture = Lecture(
                id = id,
                courseId = courseId,
                language = lang,
                frontMatter = com.example.cardiosimulator.domain.LectureFrontMatter(
                    id = id,
                    title = title,
                    order = (lectureEntries.value.size + 1)
                ),
                blocks = emptyList(),
                rawMarkdown = "# $title\n\nStart typing here..."
            )

            if (repository.writeLecture(newLecture)) {
                // Update course manifest with the new lecture
                val course = _selectedCourse.value ?: return@launch
                val updatedLectures = course.lectures + com.example.cardiosimulator.domain.LectureEntry(id, title, null)
                val updatedCourse = course.copy(lectures = updatedLectures)
                if (repository.writeCourse(updatedCourse)) {
                    _selectedCourse.value = updatedCourse
                    selectLecture(id)
                }
            }
        }
    }

    fun deleteLecture(lectureId: String) {
        val courseId = _selectedCourseId.value ?: return
        val lang = _targetLecture.value?.language ?: "en"
        
        viewModelScope.launch {
            if (repository.deleteLecture(courseId, lectureId, lang)) {
                val course = _selectedCourse.value ?: return@launch
                val updatedLectures = course.lectures.filter { it.id != lectureId }
                val updatedCourse = course.copy(lectures = updatedLectures)
                if (repository.writeCourse(updatedCourse)) {
                    _selectedCourse.value = updatedCourse
                    if (_selectedLectureId.value == lectureId) {
                        selectLecture(null)
                    }
                }
            }
        }
    }

    fun insertBlock(blockTag: String) {
        val current = _targetLecture.value ?: return
        val snippet = when (blockTag) {
            "ecg" -> "\n\n```ecg\npathology: \nlead: II\ncaption: \n```\n"
            "table" -> "\n\n```table\nid: \neditable: true\n---\n| Col 1 | Col 2 |\n|-------|-------|\n| Value | Value |\n```\n"
            else -> ""
        }
        setMarkdown(current.rawMarkdown + snippet)
    }
}
