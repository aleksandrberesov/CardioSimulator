package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object TestJson {
    fun parse(json: String): Test {
        val obj = JSONObject(json)
        val questions = obj.getJSONArray("questions").let { arr ->
            (0 until arr.length()).map { i ->
                val q = arr.getJSONObject(i)
                TestQuestion(
                    id = q.getString("id"),
                    number = q.getInt("number"),
                    text = q.getString("text"),
                    options = q.getJSONArray("options").let { optArr ->
                        (0 until optArr.length()).map { j ->
                            val o = optArr.getJSONObject(j)
                            TestOption(o.getString("id"), o.getString("text"))
                        }
                    },
                    correctOptionId = q.getString("correctOptionId"),
                    comment = q.getString("comment"),
                    pathologyId = q.optString("pathologyId", null),
                    leads = q.optJSONArray("leads")?.let { leadsArr ->
                        (0 until leadsArr.length()).mapNotNull { j ->
                            Lead.fromToken(leadsArr.getString(j))
                        }
                    } ?: emptyList(),
                    scheme = q.optString("scheme", "Grid").let { s ->
                        SeriesScheme.entries.firstOrNull { it.name == s } ?: SeriesScheme.Grid
                    }
                )
            }
        }
        return Test(
            testId = obj.getString("testId"),
            title = obj.getString("title"),
            questions = questions,
            questionTimeSeconds = obj.optInt("questionTimeSeconds", 0)
        )
    }

    fun serialize(test: Test): String {
        val obj = JSONObject()
        obj.put("testId", test.testId)
        obj.put("title", test.title)
        obj.put("questionTimeSeconds", test.questionTimeSeconds)
        val questions = JSONArray()
        test.questions.forEach { q ->
            val qObj = JSONObject()
            qObj.put("id", q.id)
            qObj.put("number", q.number)
            qObj.put("text", q.text)
            val options = JSONArray()
            q.options.forEach { o ->
                val oObj = JSONObject()
                oObj.put("id", o.id)
                oObj.put("text", o.text)
                options.put(oObj)
            }
            qObj.put("options", options)
            qObj.put("correctOptionId", q.correctOptionId)
            qObj.put("comment", q.comment)
            if (q.pathologyId != null) qObj.put("pathologyId", q.pathologyId)
            if (q.leads.isNotEmpty()) {
                qObj.put("leads", JSONArray(q.leads.map { it.name }))
            }
            if (q.scheme != SeriesScheme.Grid) {
                qObj.put("scheme", q.scheme.name)
            }
            questions.put(qObj)
        }
        obj.put("questions", questions)
        return obj.toString(2)
    }

    fun parseResult(json: String): ExamResult {
        val obj = JSONObject(json)
        val studentObj = obj.getJSONObject("student")
        val questionsArr = obj.getJSONArray("questions")
        val questions = (0 until questionsArr.length()).map { i ->
            val q = questionsArr.getJSONObject(i)
            ExamQuestionResult(
                questionId = q.getString("questionId"),
                selected = q.optString("selected", null),
                correct = q.getString("correct"),
                isCorrect = q.getBoolean("isCorrect")
            )
        }
        return ExamResult(
            student = ExamStudentInfo(studentObj.getString("fullName"), studentObj.getString("group")),
            testId = obj.getString("testId"),
            testTitle = obj.getString("testTitle"),
            timestamp = obj.getLong("timestamp"),
            questions = questions,
            correctCount = obj.getInt("correctCount"),
            totalCount = obj.getInt("totalCount"),
            passed = obj.getBoolean("passed")
        )
    }

    fun serializeResult(result: ExamResult): String {
        val obj = JSONObject()
        val student = JSONObject()
        student.put("fullName", result.student.fullName)
        student.put("group", result.student.group)
        obj.put("student", student)
        obj.put("testId", result.testId)
        obj.put("testTitle", result.testTitle)
        obj.put("timestamp", result.timestamp)
        val questions = JSONArray()
        result.questions.forEach { q ->
            val qObj = JSONObject()
            qObj.put("questionId", q.questionId)
            if (q.selected != null) qObj.put("selected", q.selected)
            qObj.put("correct", q.correct)
            qObj.put("isCorrect", q.isCorrect)
            questions.put(qObj)
        }
        obj.put("questions", questions)
        obj.put("correctCount", result.correctCount)
        obj.put("totalCount", result.totalCount)
        obj.put("passed", result.passed)
        return obj.toString(2)
    }
}

interface ITestSource {
    fun readTests(): List<Test>
    fun readTest(id: String): Test?
    fun writeTest(test: Test): Boolean
    fun deleteTest(id: String): Boolean
}

class FileTestSource(val root: File) : ITestSource {
    init {
        root.mkdirs()
    }

    override fun readTests(): List<Test> {
        return root.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { readTestFromFile(it) } ?: emptyList()
    }

    override fun readTest(id: String): Test? {
        val file = File(root, "$id.json")
        return if (file.exists()) readTestFromFile(file) else null
    }

    override fun writeTest(test: Test): Boolean {
        val file = File(root, "${test.testId}.json")
        return atomicWriteText(file, TestJson.serialize(test))
    }

    override fun deleteTest(id: String): Boolean {
        val file = File(root, "$id.json")
        return if (file.exists()) file.delete() else false
    }

    private fun readTestFromFile(file: File): Test? = runCatching {
        TestJson.parse(file.readText())
    }.getOrNull()
}

class TestRepository(private var source: ITestSource) {
    private var cachedTests: List<Test>? = null

    fun tests(): List<Test> {
        if (cachedTests == null) cachedTests = source.readTests()
        return cachedTests!!
    }

    fun test(id: String): Test? = tests().find { it.testId == id }

    fun writeTest(test: Test): Boolean {
        val ok = source.writeTest(test)
        if (ok) reload()
        return ok
    }

    fun deleteTest(id: String): Boolean {
        val ok = source.deleteTest(id)
        if (ok) reload()
        return ok
    }

    fun reload() {
        cachedTests = null
    }

    fun swapSource(newSource: ITestSource) {
        source = newSource
        reload()
    }
}

class ExamResultStore(val root: File) {
    init {
        root.mkdirs()
    }

    fun save(result: ExamResult): Boolean {
        val filename = "${result.timestamp}_${result.student.fullName.replace(" ", "_")}.json"
        val file = File(root, filename)
        return atomicWriteText(file, TestJson.serializeResult(result))
    }

    fun list(): List<ExamResult> {
        return root.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching { TestJson.parseResult(file.readText()) }.getOrNull()
            }?.sortedByDescending { it.timestamp } ?: emptyList()
    }
}
