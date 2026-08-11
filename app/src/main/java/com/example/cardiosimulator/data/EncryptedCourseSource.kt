package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.CourseManifest
import com.example.cardiosimulator.domain.CourseParser
import com.example.cardiosimulator.domain.Lecture

/**
 * [CourseSource] implementation over an [EncryptedArchive].
 * Mirrors Windows EncryptedCourseSource.
 */
class EncryptedCourseSource(
    private val archive: EncryptedArchive
) : CourseSource, AutoCloseable {

    override fun readManifest(): CourseManifest? = runCatching {
        archive.readByName("manifest.txt")?.bufferedReader()?.use { it.readText() }
            ?.let { CourseParser.parseManifest(it) }
    }.getOrNull()

    override fun readCourse(courseId: String): Course? = runCatching {
        archive.readByName("$courseId/course.txt")?.bufferedReader()?.use { it.readText() }
            ?.let { CourseParser.parseCourse(it) }
    }.getOrNull()

    override fun readLecture(courseId: String, lectureId: String, language: String): Lecture? {
        // Try requested language, then fallback "en", then anything available.
        val searchOrder = LinkedHashSet<String>()
        searchOrder.add(language)
        searchOrder.add(COURSE_FALLBACK_LANG)
        
        // Find all available languages for this lecture in the archive
        val prefix = "$courseId/lectures/$lectureId."
        val suffix = ".html"
        archive.entryPaths().forEach { path ->
            if (path.startsWith(prefix) && path.endsWith(suffix)) {
                val lang = path.removePrefix(prefix).removeSuffix(suffix)
                if (lang.isNotEmpty() && !lang.contains('/')) {
                    searchOrder.add(lang)
                }
            }
        }

        for (lang in searchOrder) {
            val path = "$courseId/lectures/$lectureId.$lang.html"
            val text = runCatching {
                archive.readByName(path)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            
            if (text != null) {
                return runCatching {
                    CourseParser.parseLecture(text, courseId, lang)
                }.getOrNull()
            }
        }
        return null
    }

    override fun listCourses(): List<String> =
        archive.entryPaths().asSequence()
            .filter { it.endsWith("/course.txt") }
            .map { it.substringBefore('/') }
            .distinct()
            .toList()

    override fun listLectures(courseId: String): List<String> =
        archive.entryPaths().asSequence()
            .filter { it.startsWith("$courseId/lectures/") && it.endsWith(".html") }
            .map { it.substringAfterLast('/').substringBeforeLast('.').substringBeforeLast('.') }
            .distinct()
            .toList()

    override fun close() {
        archive.close()
    }
}
