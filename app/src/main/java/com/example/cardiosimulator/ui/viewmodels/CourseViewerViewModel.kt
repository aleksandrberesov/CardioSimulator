package com.example.cardiosimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardiosimulator.data.CourseRepository
import com.example.cardiosimulator.data.DataSourcePrefs
import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.domain.LectureEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Manages the "Teaching" mode state: selected course, selected lecture,
 * and the parsed lecture content. Keyed by [mode] so selections are
 * persisted independently for Teaching vs. (future) Examination modes.
 */
class CourseViewerViewModel(
    private val repository: CourseRepository,
    private val prefs: DataSourcePrefs,
    private val mode: String,
) : ViewModel() {

    private val _selectedCourseId = MutableStateFlow<String?>(null)
    val selectedCourseId: StateFlow<String?> = _selectedCourseId.asStateFlow()

    private val _selectedCourse = MutableStateFlow<Course?>(null)
    val selectedCourse: StateFlow<Course?> = _selectedCourse.asStateFlow()

    private val _selectedLectureId = MutableStateFlow<String?>(null)
    val selectedLectureId: StateFlow<String?> = _selectedLectureId.asStateFlow()

    /** Currently active lecture. Fetched whenever course, lecture, or language changes. */
    private val _lectureContent = MutableStateFlow<Lecture?>(null)
    val lectureContent: StateFlow<Lecture?> = _lectureContent.asStateFlow()

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
            val lastCourseId = prefs.lastCourseId.first()
            if (lastCourseId != null) {
                selectCourse(lastCourseId)
                val lastLectureId = prefs.lastLectureId(mode).first()
                if (lastLectureId != null) {
                    selectLecture(lastLectureId)
                }
            }

            // Keep _lectureContent in sync with language changes.
            combine(selectedCourseId, selectedLectureId, prefs.languageTag) { c, l, lang ->
                Triple(c, l, lang ?: "en")
            }.collect { (c, l, lang) ->
                _lectureContent.value = if (c != null && l != null) {
                    repository.readLecture(c, l, lang)
                } else null
            }
        }
    }

    fun selectCourse(id: String?) {
        if (_selectedCourseId.value == id) return
        _selectedCourseId.value = id
        _selectedCourse.value = id?.let { repository.readCourse(it) }
        _selectedLectureId.value = null
        viewModelScope.launch { prefs.setLastCourseId(id) }
    }

    fun selectLecture(id: String?) {
        if (_selectedLectureId.value == id) return
        _selectedLectureId.value = id
        viewModelScope.launch { prefs.setLastLectureId(mode, id) }
    }
}
