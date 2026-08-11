package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Lecture
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CourseLanguageFallbackTests {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `readLecture falls back to available language when requested and en missing`() {
        val root = tempFolder.newFolder("courses")
        val courseId = "course1"
        val lectureId = "lec1"
        
        val courseDir = File(root, courseId).apply { mkdirs() }
        File(courseDir, "course.txt").writeText("course: $courseId\ntitle: Course 1\nlanguage: en")
        
        val lecturesDir = File(courseDir, "lectures").apply { mkdirs() }
        // Only .ru.html exists
        val ruContent = "---\nid: $lectureId\ntitle: RU Title\n---\nRU Body"
        File(lecturesDir, "$lectureId.ru.html").writeText(ruContent)
        
        val source = FileCourseSource(root)
        
        // Request "en" (or anything else)
        val lecture = source.readLecture(courseId, lectureId, "en")
        
        assertNotNull("Lecture should not be null", lecture)
        assertEquals("ru", lecture?.language)
        assertTrue(lecture?.rawHtml?.contains("RU Body") == true)
    }

    @Test
    fun `readLecture prefers requested language over fallback`() {
        val root = tempFolder.newFolder("courses")
        val courseId = "course1"
        val lectureId = "lec1"
        
        val courseDir = File(root, courseId).apply { mkdirs() }
        File(courseDir, "course.txt").writeText("course: $courseId\ntitle: Course 1")
        val lecturesDir = File(courseDir, "lectures").apply { mkdirs() }
        
        val enContent = "---\nid: $lectureId\ntitle: EN Title\n---\nEN Body"
        val ruContent = "---\nid: $lectureId\ntitle: RU Title\n---\nRU Body"
        
        File(lecturesDir, "$lectureId.en.html").writeText(enContent)
        File(lecturesDir, "$lectureId.ru.html").writeText(ruContent)
        
        val source = FileCourseSource(root)
        
        // Request "en", should get EN
        val lectureEn = source.readLecture(courseId, lectureId, "en")
        assertEquals("en", lectureEn?.language)
        assertTrue(lectureEn?.rawHtml?.contains("EN Body") == true)
        
        // Request "ru", should get RU
        val lectureRu = source.readLecture(courseId, lectureId, "ru")
        assertEquals("ru", lectureRu?.language)
        assertTrue(lectureRu?.rawHtml?.contains("RU Body") == true)
    }
    
    @Test
    fun `readLecture prefers en fallback over other available languages`() {
        val root = tempFolder.newFolder("courses")
        val courseId = "course1"
        val lectureId = "lec1"
        
        val courseDir = File(root, courseId).apply { mkdirs() }
        File(courseDir, "course.txt").writeText("course: $courseId\ntitle: Course 1")
        val lecturesDir = File(courseDir, "lectures").apply { mkdirs() }
        
        val enContent = "---\nid: $lectureId\ntitle: EN Title\n---\nEN Body"
        val ruContent = "---\nid: $lectureId\ntitle: RU Title\n---\nRU Body"
        
        File(lecturesDir, "$lectureId.en.html").writeText(enContent)
        File(lecturesDir, "$lectureId.ru.html").writeText(ruContent)
        
        val source = FileCourseSource(root)
        
        // Request "fr", which doesn't exist. Should fall back to "en" first.
        val lecture = source.readLecture(courseId, lectureId, "fr")
        assertEquals("en", lecture?.language)
        assertTrue(lecture?.rawHtml?.contains("EN Body") == true)
    }
}
