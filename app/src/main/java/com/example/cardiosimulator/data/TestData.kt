package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

val testJson = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
    prettyPrint = true
}

object TestJson {
    fun parse(json: String): Test = testJson.decodeFromString(json)
    fun serialize(test: Test): String = testJson.encodeToString(test)

    fun parseResult(json: String): ExamResult = testJson.decodeFromString(json)
    fun serializeResult(result: ExamResult): String = testJson.encodeToString(result)

    fun parseQuestion(json: String): TestQuestion = testJson.decodeFromString(json)
    fun serializeQuestion(question: TestQuestion): String = testJson.encodeToString(question)
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

interface IQuestionBankSource {
    fun readQuestions(): List<TestQuestion>
    fun writeQuestion(question: TestQuestion): Boolean
    fun deleteQuestion(id: String): Boolean
}

class FileQuestionBankSource(val root: File) : IQuestionBankSource {
    init {
        root.mkdirs()
    }

    override fun readQuestions(): List<TestQuestion> {
        // Skip themes.json
        return root.listFiles { f -> f.isFile && f.name.endsWith(".json") && f.name != "themes.json" }
            ?.mapNotNull { readQuestionFromFile(it) } ?: emptyList()
    }

    override fun writeQuestion(question: TestQuestion): Boolean {
        val file = File(root, "${question.id}.json")
        return atomicWriteText(file, TestJson.serializeQuestion(question))
    }

    override fun deleteQuestion(id: String): Boolean {
        val file = File(root, "$id.json")
        return if (file.exists()) file.delete() else false
    }

    private fun readQuestionFromFile(file: File): TestQuestion? = runCatching {
        TestJson.parseQuestion(file.readText())
    }.getOrNull()
}

class QuestionBankRepository(private var source: IQuestionBankSource) {
    private var cachedQuestions: List<TestQuestion>? = null

    fun questions(): List<TestQuestion> {
        if (cachedQuestions == null) cachedQuestions = source.readQuestions()
        return cachedQuestions!!
    }

    fun writeQuestion(question: TestQuestion): Boolean {
        val ok = source.writeQuestion(question)
        if (ok) reload()
        return ok
    }

    fun deleteQuestion(id: String): Boolean {
        val ok = source.deleteQuestion(id)
        if (ok) reload()
        return ok
    }

    fun import(questions: List<TestQuestion>) {
        questions.forEach { source.writeQuestion(it) }
        reload()
    }

    fun exportAll(): String {
        return testJson.encodeToString(questions())
    }

    fun reload() {
        cachedQuestions = null
    }
}

class TestThemeStore(private val themeFile: File) {
    companion object {
        val DefaultThemes = listOf(
            "Норма",
            "Нарушения ритма",
            "Нарушения проводимости",
            "Гипертрофии",
            "Ишемия и инфаркт",
            "Прочее"
        )
    }

    fun readThemes(): List<String> {
        if (!themeFile.exists()) {
            seedIfMissing()
        }
        return runCatching {
            testJson.decodeFromString<List<String>>(themeFile.readText())
        }.getOrDefault(DefaultThemes)
    }

    fun writeThemes(themes: List<String>): Boolean {
        return atomicWriteText(themeFile, testJson.encodeToString(themes))
    }

    private fun seedIfMissing() {
        writeThemes(DefaultThemes)
    }
}
