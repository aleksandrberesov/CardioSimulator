package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.CourseManifest
import com.example.cardiosimulator.domain.Lecture
import com.example.cardiosimulator.domain.LectureEntry
import com.example.cardiosimulator.domain.TopicEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current [CourseSource], caches the manifest, lazily reads
 * course and lecture files on demand, and routes writes through to the
 * file-backed source. Mirrors [PathologyRepository] one-to-one in shape
 * so the two pipelines compose the same way inside `AppViewModel`.
 *
 * Writes are routed through [withWritableSource] so asset-backed sources
 * (which can't be written to) short-circuit to `false` cleanly. Callers
 * never see the source instance directly — the wrapping keeps the
 * file/asset asymmetry encapsulated here.
 */
class CourseRepository(private var source: CourseSource) {

    private val _manifest = MutableStateFlow<CourseManifest?>(null)
    val manifestFlow: StateFlow<CourseManifest?> = _manifest.asStateFlow()

    fun setSource(newSource: CourseSource) {
        source = newSource
        _manifest.value = null
    }

    fun loadManifest(): Boolean {
        val m = source.readManifest()
        _manifest.value = null
        _manifest.value = m
        return m != null
    }

    fun manifest(): CourseManifest? = _manifest.value

    fun courses(): List<CourseEntry> =
        _manifest.value?.entries?.sortedBy { it.titleEn.lowercase() } ?: emptyList()

    // ─── reads ──────────────────────────────────────────────────────────

    fun readCourse(courseId: String): Course? = source.readCourse(courseId)

    fun readLecture(courseId: String, lectureId: String, language: String): Lecture? =
        source.readLecture(courseId, lectureId, language)

    /** Convenience wrapper around [readCourse] returning only the lecture index. */
    fun lectureEntries(courseId: String): List<LectureEntry> =
        readCourse(courseId)?.lectures ?: emptyList()

    /** Convenience wrapper around [readCourse] returning only the topic index. */
    fun topicEntries(courseId: String): List<TopicEntry> =
        readCourse(courseId)?.topics ?: emptyList()

    fun readAnswers(courseId: String, lectureId: String, language: String): String? =
        (source as? FileCourseSource)?.readAnswers(courseId, lectureId, language)

    private inline fun <T> withWritableSource(block: (CourseSource) -> T): T? {
        val s = source
        return if (s is FileCourseSource || s is OverlayCourseSource) {
            block(s)
        } else null
    }

    fun writeLecture(lecture: Lecture): Boolean = withWritableSource {
        when (it) {
            is FileCourseSource -> it.writeLecture(lecture)
            is OverlayCourseSource -> {
                it.writeLecture(lecture.courseId, lecture.id, lecture.language, lecture.rawHtml)
                true
            }
            else -> false
        }
    } ?: false

    fun writeLectureRaw(courseId: String, lectureId: String, language: String, body: String): Boolean =
        withWritableSource {
            when (it) {
                is FileCourseSource -> it.writeLectureRaw(courseId, lectureId, language, body)
                is OverlayCourseSource -> {
                    it.writeLecture(courseId, lectureId, language, body)
                    true
                }
                else -> false
            }
        } ?: false

    fun writeAnswers(courseId: String, lectureId: String, language: String, json: String): Boolean =
        withWritableSource {
            when (it) {
                is FileCourseSource -> it.writeAnswers(courseId, lectureId, language, json)
                else -> false // Not implemented in overlay
            }
        } ?: false

    fun importAsset(courseId: String, fileName: String, bytes: ByteArray): Boolean =
        withWritableSource {
            when (it) {
                is FileCourseSource -> it.writeAsset(courseId, fileName, bytes)
                else -> false // Not implemented in overlay
            }
        } ?: false

    fun deleteLecture(courseId: String, lectureId: String, language: String): Boolean =
        withWritableSource {
            when (it) {
                is FileCourseSource -> it.deleteLecture(courseId, lectureId, language)
                else -> false // Not implemented in overlay
            }
        } ?: false

    /**
     * Persists [course] (course.txt + manifest sync). Refreshes the
     * cached manifest on success so subsequent reads see the new entry.
     */
    fun writeCourse(course: Course): Boolean = withWritableSource {
        val ok = when (it) {
            is FileCourseSource -> it.writeCourse(course)
            else -> false // Not implemented in overlay
        }
        if (ok) loadManifest()
        ok
    } ?: false
}
