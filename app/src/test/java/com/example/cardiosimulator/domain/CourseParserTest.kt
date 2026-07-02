package com.example.cardiosimulator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseParserTest {

    @Test
    fun testRoundTrip() {
        val course = Course(
            id = "test-course",
            titleEn = "Test Course",
            nameRu = "Тестовый курс",
            authors = "Author",
            languages = listOf("en", "ru"),
            topics = listOf(
                TopicEntry("topic-1", "Topic 1", "Тема 1"),
                TopicEntry("topic-2", "Empty Topic", null)
            ),
            lectures = listOf(
                LectureEntry("lec-1", "Lecture 1", "Лекция 1", topic = "topic-1"),
                LectureEntry("lec-2", "Lecture 2", null, topic = "topic-1"),
                LectureEntry("lec-orphan", "Orphan", null, topic = null)
            ),
            pathologies = listOf("path-1")
        )

        val serialized = CourseParser.serializeCourse(course)
        val parsed = CourseParser.parseCourse(serialized)

        assertEquals(course.id, parsed.id)
        assertEquals(course.titleEn, parsed.titleEn)
        assertEquals(course.nameRu, parsed.nameRu)
        assertEquals(course.topics, parsed.topics)
        assertEquals(course.lectures, parsed.lectures)
        assertEquals(course.pathologies, parsed.pathologies)
    }

    @Test
    fun testLegacyParse() {
        val legacyText = """
            course:legacy
            title:Legacy Course
            
            lecture:lec1;title:Lec 1
            lecture:lec2;title:Lec 2
        """.trimIndent()

        val parsed = CourseParser.parseCourse(legacyText)
        assertEquals("legacy", parsed.id)
        assertEquals(0, parsed.topics.size)
        assertEquals(2, parsed.lectures.size)
        assertEquals(null, parsed.lectures[0].topic)
        assertEquals(null, parsed.lectures[1].topic)
    }
}
