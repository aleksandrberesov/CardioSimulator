package com.example.cardiosimulator.data

import com.example.cardiosimulator.data.crypto.WritableEncryptedOverlay
import com.example.cardiosimulator.domain.Course
import com.example.cardiosimulator.domain.CourseManifest
import com.example.cardiosimulator.domain.CourseParser
import com.example.cardiosimulator.domain.Lecture

/**
 * Decorator that merges a read-only [EncryptedCourseSource] (base) with a
 * [WritableEncryptedOverlay] (writable).
 */
class OverlayCourseSource(
    private val base: EncryptedCourseSource,
    private val overlay: WritableEncryptedOverlay
) : CourseSource, AutoCloseable {

    override fun readManifest(): CourseManifest? {
        val baseManifest = base.readManifest() ?: return null
        val overlayManifestBytes = overlay.readEntry("manifest.txt")
        val overlayManifest = overlayManifestBytes?.let {
            CourseParser.parseManifest(String(it, Charsets.UTF_8))
        }

        if (overlayManifest == null) return baseManifest

        // Merge courses in manifest
        val mergedEntries = baseManifest.entries.toMutableList()
        overlayManifest.entries.forEach { overlayEntry ->
            val idx = mergedEntries.indexOfFirst { it.id == overlayEntry.id }
            if (idx != -1) {
                mergedEntries[idx] = overlayEntry
            } else {
                mergedEntries.add(overlayEntry)
            }
        }
        
        mergedEntries.removeIf { overlay.isTombstoned("${it.id}/course.txt") }

        return baseManifest.copy(entries = mergedEntries)
    }

    override fun readCourse(courseId: String): Course? {
        val bytes = overlay.readEntry("$courseId/course.txt")
        if (bytes != null) return CourseParser.parseCourse(String(bytes, Charsets.UTF_8))
        if (overlay.isTombstoned("$courseId/course.txt")) return null
        return base.readCourse(courseId)
    }

    override fun readLecture(courseId: String, lectureId: String, language: String): Lecture? {
        // Try requested language in overlay, then fallback "en" in overlay
        val overlayBytes = overlay.readEntry("$courseId/lectures/$lectureId.$language.html")
            ?: overlay.readEntry("$courseId/lectures/$lectureId.en.html")
            
        if (overlayBytes != null) {
            val lang = if (overlay.readEntry("$courseId/lectures/$lectureId.$language.html") != null) language else "en"
            return CourseParser.parseLecture(String(overlayBytes, Charsets.UTF_8), courseId, lang)
        }
        
        if (overlay.isTombstoned("$courseId/lectures/$lectureId.$language.html")) return null
        
        return base.readLecture(courseId, lectureId, language)
    }

    override fun listCourses(): List<String> {
        val all = mutableSetOf<String>()
        all.addAll(base.listCourses())
        all.removeIf { overlay.isTombstoned("$it/course.txt") }
        
        overlay.listEntries().forEach { name ->
            if (name.endsWith("/course.txt")) {
                all.add(name.substringBefore('/'))
            }
        }
        return all.toList()
    }

    override fun listLectures(courseId: String): List<String> {
        val all = mutableSetOf<String>()
        all.addAll(base.listLectures(courseId))
        
        all.removeIf { lectureId ->
            overlay.isTombstoned("$courseId/lectures/$lectureId.en.html")
        }
        
        overlay.listEntries().forEach { name ->
            if (name.startsWith("$courseId/lectures/") && name.endsWith(".html")) {
                val lectureId = name.substringAfterLast('/').substringBeforeLast('.').substringBeforeLast('.')
                all.add(lectureId)
            }
        }
        return all.toList()
    }
    
    /**
     * Writes a lecture to the overlay.
     */
    fun writeLecture(courseId: String, lectureId: String, language: String, content: String) {
        overlay.writeEntry("$courseId/lectures/$lectureId.$language.html", content.toByteArray(Charsets.UTF_8))
    }

    override fun close() {
        base.close()
        overlay.close()
    }
}
