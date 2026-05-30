package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.CourseEntry
import com.example.cardiosimulator.domain.CourseManifest
import com.example.cardiosimulator.domain.CourseParser
import com.example.cardiosimulator.domain.Lecture
import java.io.File

/**
 * [CourseSource] backed by a directory on disk (typically
 * `filesDir/courses/`, populated by [CourseZipExtractor]).
 *
 * Adds write methods for the constructor save flow. All writes route
 * through [atomicWriteText] so an interrupted write can't leave a
 * half-written file in place. Callers should reach these through
 * [CourseRepository] rather than downcasting — the repository wraps
 * each write behind the `source as? FileCourseSource` check.
 */
class FileCourseSource(
    val root: File,
) : CourseSource {

    override fun readManifest(): CourseManifest? = runCatching {
        val text = File(root, "manifest.txt").takeIf { it.canRead() }?.readText(Charsets.UTF_8)
            ?: return null
        CourseParser.parseManifest(text)
    }.getOrNull()

    override fun readCourse(courseId: String): Course? = runCatching {
        val file = File(root, "$courseId/course.txt").takeIf { it.canRead() } ?: return null
        CourseParser.parseCourse(file.readText(Charsets.UTF_8))
    }.getOrNull()

    override fun readLecture(courseId: String, lectureId: String, language: String): Lecture? {
        for (lang in fallbackLanguages(language)) {
            val file = File(root, "$courseId/lectures/$lectureId.$lang.html")
            if (!file.canRead()) continue
            return runCatching {
                CourseParser.parseLecture(file.readText(Charsets.UTF_8), courseId, lang)
            }.getOrNull()
        }
        return null
    }

    override fun listCourses(): List<String> =
        root.listFiles { f -> f.isDirectory && File(f, "course.txt").canRead() }
            ?.map { it.name }
            ?: emptyList()

    override fun listLectures(courseId: String): List<String> =
        File(root, "$courseId/lectures")
            .listFiles { f -> f.isFile && f.name.endsWith(".html") }
            ?.map { it.name.substringBeforeLast('.').substringBeforeLast('.') }
            ?.distinct()
            ?: emptyList()

    // ─── writes ─────────────────────────────────────────────────────────

    /** Atomically writes [lecture] as `<courseId>/lectures/<id>.<lang>.html`. */
    fun writeLecture(lecture: Lecture): Boolean = atomicWriteText(
        File(root, "${lecture.courseId}/lectures/${lecture.id}.${lecture.language}.html"),
        CourseParser.serializeLecture(lecture),
    )

    /**
     * Atomically writes [course] as `<id>/course.txt`. If the manifest
     * exists, updates the matching [CourseEntry] (or appends a new one)
     * so the manifest stays in sync with the course's lecture count and
     * titles.
     */
    fun writeCourse(course: Course): Boolean {
        val ok = atomicWriteText(
            File(root, "${course.id}/course.txt"),
            CourseParser.serializeCourse(course),
        )
        if (!ok) return false
        return syncManifestEntry(course)
    }

    /**
     * Persists raw [body] (front matter + HTML) for a lecture without
     * re-parsing. Used by the constructor's text-editor flow where the
     * user has edited the source directly and we want to write back
     * byte-for-byte.
     */
    fun writeLectureRaw(courseId: String, lectureId: String, language: String, body: String): Boolean =
        atomicWriteText(File(root, "$courseId/lectures/$lectureId.$language.html"), body)

    /** Writes the sibling `<lecture-id>.<lang>.answers.json` cell-state file. */
    fun writeAnswers(courseId: String, lectureId: String, language: String, json: String): Boolean =
        atomicWriteText(File(root, "$courseId/lectures/$lectureId.$language.answers.json"), json)

    fun readAnswers(courseId: String, lectureId: String, language: String): String? = runCatching {
        File(root, "$courseId/lectures/$lectureId.$language.answers.json")
            .takeIf { it.canRead() }?.readText(Charsets.UTF_8)
    }.getOrNull()

    fun deleteLecture(courseId: String, lectureId: String, language: String): Boolean {
        val html = File(root, "$courseId/lectures/$lectureId.$language.html")
        val answers = File(root, "$courseId/lectures/$lectureId.$language.answers.json")
        var ok = true
        if (html.exists()) ok = html.delete() && ok
        if (answers.exists()) ok = answers.delete() && ok
        return ok
    }

    fun isValid(): Boolean =
        root.isDirectory && File(root, "manifest.txt").canRead()

    // ─── helpers ────────────────────────────────────────────────────────

    private fun syncManifestEntry(course: Course): Boolean {
        val manifest = readManifest() ?: return true   // no manifest yet, nothing to sync
        val entry = CourseEntry(
            id = course.id,
            titleEn = course.titleEn,
            nameRu = course.nameRu,
            lecturesCount = course.lectures.size,
            pathologies = course.pathologies,
        )
        val idx = manifest.entries.indexOfFirst { it.id == course.id }
        val updated = when {
            idx < 0 -> manifest.entries + entry
            manifest.entries[idx] == entry -> return true   // no-op, skip write
            else -> manifest.entries.toMutableList().also { it[idx] = entry }
        }
        return atomicWriteText(
            File(root, "manifest.txt"),
            CourseParser.serializeManifest(manifest.copy(entries = updated)),
        )
    }
}
