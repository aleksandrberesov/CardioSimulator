package com.example.cardiosimulator.data

import android.content.res.AssetManager
import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.CourseManifest
import com.example.cardiosimulator.domain.CourseParser
import com.example.cardiosimulator.domain.Lecture

/**
 * [CourseSource] backed by Android assets. Reads `assets/courses/`
 * (nested layout, UTF-8, see `docs/course-format.md`).
 *
 * Used as the boot-time default when no user-provided ZIP has been
 * extracted to `filesDir/courses/` yet.
 */
class AssetCourseSource(
    private val assets: AssetManager,
    private val baseDir: String = DEFAULT_DIR,
) : CourseSource {

    override fun readManifest(): CourseManifest? = runCatching {
        val text = readText("$baseDir/manifest.txt") ?: return null
        CourseParser.parseManifest(text)
    }.getOrNull()

    override fun readCourse(courseId: String): Course? = runCatching {
        val text = readText("$baseDir/$courseId/course.txt") ?: return null
        CourseParser.parseCourse(text)
    }.getOrNull()

    override fun readLecture(courseId: String, lectureId: String, language: String): Lecture? {
        for (lang in fallbackLanguages(language)) {
            val text = readText("$baseDir/$courseId/lectures/$lectureId.$lang.md") ?: continue
            return runCatching {
                CourseParser.parseLecture(text, courseId, lang)
            }.getOrNull()
        }
        return null
    }

    override fun listCourses(): List<String> = runCatching {
        assets.list(baseDir)?.toList().orEmpty()
            .filter { courseId ->
                assets.list("$baseDir/$courseId")?.contains("course.txt") == true
            }
    }.getOrDefault(emptyList())

    override fun listLectures(courseId: String): List<String> = runCatching {
        assets.list("$baseDir/$courseId/lectures")?.toList().orEmpty()
            .filter { it.endsWith(".md") }
            .map { it.substringBeforeLast('.').substringBeforeLast('.') }
            .distinct()
    }.getOrDefault(emptyList())

    private fun readText(path: String): String? = runCatching {
        assets.open(path).use { String(it.readBytes(), Charsets.UTF_8) }
    }.getOrNull()

    companion object {
        const val DEFAULT_DIR: String = "courses"
    }
}
