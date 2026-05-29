package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.CourseManifest
import com.example.cardiosimulator.domain.Lecture

/** Language tag used as the fallback when a lecture is missing in the requested language. */
internal const val COURSE_FALLBACK_LANG: String = "en"

/**
 * Returns the language tags to try in order when reading a lecture: the
 * requested [language] first (unless it's already the fallback), then
 * [COURSE_FALLBACK_LANG].
 */
internal fun fallbackLanguages(language: String): List<String> =
    listOfNotNull(language.takeIf { it != COURSE_FALLBACK_LANG }, COURSE_FALLBACK_LANG)

/**
 * Storage-agnostic source for the course bundle (see
 * `docs/course-format.md`). Implementations expose:
 *
 * - one `manifest.txt` at the root,
 * - one `<course-id>/course.txt` per course,
 * - one `<lecture-id>.<lang>.md` per (lecture × language) pair under
 *   `<course-id>/lectures/`.
 *
 * This interface is **read-only** by contract. The constructor writes
 * back through [FileCourseSource.writeLecture] on the file-backed
 * subclass — asset-backed sources cannot be written.
 *
 * [readLecture] takes a [language] tag (`"en"`, `"ru"`, …); when a file
 * for that language is absent, the implementation falls back to the
 * English variant.
 */
interface CourseSource {
    fun readManifest(): CourseManifest?
    fun readCourse(courseId: String): Course?
    fun readLecture(courseId: String, lectureId: String, language: String): Lecture?
    fun listCourses(): List<String>
    fun listLectures(courseId: String): List<String>
}
