package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.CourseRepository
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.domain.LectureEntry
import com.example.cardiosimulator.domain.OperatingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Read-only course/lecture viewer state (Phase 2 of
 * docs/plans/active/2026-05-course-constructor.md). Per-mode keyed so each
 * operating mode that hosts the viewer keeps its own selection.
 *
 * Disk reads (lecture index, lecture body) run on [Dispatchers.IO]; the
 * resulting [lecture] body is HTML handed verbatim to
 * `ui/components/LectureWebView`.
 */
class CourseViewerViewModel(
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

    private val _lecture = MutableStateFlow<Lecture?>(null)
    val lecture: StateFlow<Lecture?> = _lecture.asStateFlow()

    private var languageTag: String = "en"
    private var restored = false

    /** Updates the active language and reloads the open lecture in it. */
    fun setLanguage(tag: String) {
        if (tag == languageTag) return
        languageTag = tag
        val courseId = _selectedCourseId.value
        val lectureId = _selectedLectureId.value
        if (courseId != null && lectureId != null) loadLecture(courseId, lectureId)
    }

    fun selectCourse(courseId: String) {
        if (_selectedCourseId.value == courseId) return
        _selectedCourseId.value = courseId
        _selectedLectureId.value = null
        _lecture.value = null
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
        viewModelScope.launch { prefs?.setLastLectureId(mode.name, lectureId) }
        loadLecture(courseId, lectureId)
    }

    /** Clears the open lecture so the host screen shows its default view. */
    fun closeLecture() {
        _selectedLectureId.value = null
        _lecture.value = null
    }

    private fun loadLecture(courseId: String, lectureId: String) {
        viewModelScope.launch {
            _lecture.value = withContext(Dispatchers.IO) {
                repository.readLecture(courseId, lectureId, languageTag)
            }
        }
    }

    /**
     * One-shot restore of the last viewed course from prefs. Loads the
     * lecture index but does not auto-open a lecture, so the host screen
     * (e.g. Teaching) shows its default view first.
     */
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
}
