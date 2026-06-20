package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TestDataTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `TestJson round-trip`() {
        val test = Test(
            testId = "test1",
            title = "Title",
            questions = listOf(
                TestQuestion(
                    id = "q1",
                    number = 1,
                    text = "Q?",
                    options = listOf(TestOption("o1", "O1"), TestOption("o2", "O2")),
                    correctOptionId = "o1",
                    comment = "Comment",
                    pathologyId = "ecg1",
                    leads = listOf(Lead.I, Lead.II),
                    scheme = SeriesScheme.TwoColumn
                )
            ),
            questionTimeSeconds = 60
        )

        val json = TestJson.serialize(test)
        val parsed = TestJson.parse(json)

        assertEquals(test.testId, parsed.testId)
        assertEquals(test.title, parsed.title)
        assertEquals(test.questionTimeSeconds, parsed.questionTimeSeconds)
        assertEquals(test.questions.size, parsed.questions.size)
        
        val q = parsed.questions[0]
        assertEquals("q1", q.id)
        assertEquals(1, q.number)
        assertEquals("Q?", q.text)
        assertEquals(2, q.options.size)
        assertEquals("o1", q.correctOptionId)
        assertEquals("Comment", q.comment)
        assertEquals("ecg1", q.pathologyId)
        assertEquals(listOf(Lead.I, Lead.II), q.leads)
        assertEquals(SeriesScheme.TwoColumn, q.scheme)
    }

    @Test
    fun `ExamResult round-trip`() {
        val result = ExamResult(
            student = ExamStudentInfo("Name", "Group"),
            testId = "test1",
            testTitle = "Title",
            timestamp = 123456789L,
            questions = listOf(
                ExamQuestionResult("q1", "o1", "o1", true),
                ExamQuestionResult("q2", null, "o2", false)
            ),
            correctCount = 1,
            totalCount = 2,
            passed = false
        )

        val json = TestJson.serializeResult(result)
        val parsed = TestJson.parseResult(json)

        assertEquals(result.student, parsed.student)
        assertEquals(result.testId, parsed.testId)
        assertEquals(result.timestamp, parsed.timestamp)
        assertEquals(result.questions.size, parsed.questions.size)
        assertEquals(result.correctCount, parsed.correctCount)
        assertEquals(result.passed, parsed.passed)
    }

    @Test
    fun `FileTestSource read write delete`() {
        val root = tempFolder.newFolder("tests")
        val source = FileTestSource(root)
        val test = Test("t1", "Title", emptyList())

        assertTrue(source.writeTest(test))
        val read = source.readTest("t1")
        assertNotNull(read)
        assertEquals("t1", read?.testId)

        assertEquals(1, source.readTests().size)

        assertTrue(source.deleteTest("t1"))
        assertNull(source.readTest("t1"))
        assertEquals(0, source.readTests().size)
    }

    @Test
    fun `ExamResultStore save and list newest-first`() {
        val root = tempFolder.newFolder("results")
        val store = ExamResultStore(root)
        
        val r1 = ExamResult(ExamStudentInfo("A", "G"), "t", "T", 1000L, emptyList(), 0, 0, false)
        val r2 = ExamResult(ExamStudentInfo("B", "G"), "t", "T", 2000L, emptyList(), 0, 0, false)

        store.save(r1)
        store.save(r2)

        val list = store.list()
        assertEquals(2, list.size)
        assertEquals(2000L, list[0].timestamp)
        assertEquals(1000L, list[1].timestamp)
    }
}
