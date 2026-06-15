package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object OskeJson {
    fun parseForm(json: String): OskeForm {
        val obj = JSONObject(json)
        val questions = obj.getJSONArray("questions").let { arr ->
            (0 until arr.length()).map { i ->
                val q = arr.getJSONObject(i)
                OskeQuestion(
                    id = q.getString("id"),
                    number = q.getInt("number"),
                    title = q.getString("title"),
                    kind = OskeAnswerKind.valueOf(q.getString("kind")),
                    options = q.getJSONArray("options").let { optArr ->
                        (0 until optArr.length()).map { j ->
                            val o = optArr.getJSONObject(j)
                            OskeOption(o.getString("id"), o.getString("text"))
                        }
                    }
                )
            }
        }
        return OskeForm(
            formId = obj.getString("formId"),
            specialty = OskeSpecialty.valueOf(obj.getString("specialty")),
            version = obj.getString("version"),
            questions = questions,
            passFraction = obj.optDouble("passFraction", 1.0)
        )
    }

    fun parseAnswerKey(json: String): OskeAnswerKey {
        val obj = JSONObject(json)
        val correct = obj.getJSONObject("correct")
        val correctMap = mutableMapOf<String, List<String>>()
        correct.keys().forEach { key ->
            val arr = correct.getJSONArray(key)
            val list = (0 until arr.length()).map { arr.getString(it) }
            correctMap[key] = list
        }
        return OskeAnswerKey(
            ecgId = obj.getString("ecgId"),
            formId = obj.getString("formId"),
            correctOptionIds = correctMap
        )
    }

    fun serializeAnswerKey(key: OskeAnswerKey): String {
        val obj = JSONObject()
        obj.put("ecgId", key.ecgId)
        obj.put("formId", key.formId)
        val correct = JSONObject()
        key.correctOptionIds.forEach { (qId, optIds) ->
            correct.put(qId, JSONArray(optIds))
        }
        obj.put("correct", correct)
        return obj.toString(2)
    }

    fun parseResult(json: String): OskeResult {
        val obj = JSONObject(json)
        val studentObj = obj.getJSONObject("student")
        val blocksArr = obj.getJSONArray("blocks")
        val blocks = (0 until blocksArr.length()).map { i ->
            val b = blocksArr.getJSONObject(i)
            OskeBlockResult(
                questionId = b.getString("questionId"),
                selected = b.getJSONArray("selected").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
                correct = b.getJSONArray("correct").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
                isCorrect = b.getBoolean("isCorrect")
            )
        }
        return OskeResult(
            student = OskeStudentInfo(studentObj.getString("fullName"), studentObj.getString("group")),
            specialty = OskeSpecialty.valueOf(obj.getString("specialty")),
            ecgId = obj.getString("ecgId"),
            formId = obj.getString("formId"),
            timestamp = obj.getLong("timestamp"),
            blocks = blocks,
            correctCount = obj.getInt("correctCount"),
            totalCount = obj.getInt("totalCount"),
            passed = obj.getBoolean("passed")
        )
    }

    fun serializeResult(result: OskeResult): String {
        val obj = JSONObject()
        val student = JSONObject()
        student.put("fullName", result.student.fullName)
        student.put("group", result.student.group)
        obj.put("student", student)
        obj.put("specialty", result.specialty.name)
        obj.put("ecgId", result.ecgId)
        obj.put("formId", result.formId)
        obj.put("timestamp", result.timestamp)
        val blocks = JSONArray()
        result.blocks.forEach { b ->
            val block = JSONObject()
            block.put("questionId", b.questionId)
            block.put("selected", JSONArray(b.selected))
            block.put("correct", JSONArray(b.correct))
            block.put("isCorrect", b.isCorrect)
            blocks.put(block)
        }
        obj.put("blocks", blocks)
        obj.put("correctCount", result.correctCount)
        obj.put("totalCount", result.totalCount)
        obj.put("passed", result.passed)
        return obj.toString(2)
    }
}

interface IOskeSource {
    fun readManifest(): OskeManifest?
    fun readForm(formId: String): OskeForm?
    fun readAnswerKey(ecgId: String, formId: String): OskeAnswerKey?
    fun writeAnswerKey(key: OskeAnswerKey): Boolean
    fun listAnswerKeyEcgIds(formId: String): List<String>
}

class FileOskeSource(val root: File) : IOskeSource {
    override fun readManifest(): OskeManifest? = runCatching {
        val file = File(root, "manifest.txt").takeIf { it.canRead() } ?: return null
        val lines = file.readLines()
        val version = lines.firstOrNull()?.substringAfter("version:", "1.0") ?: "1.0"
        val entries = lines.drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            val fields = line.split(";").associate {
                val (k, v) = it.split(":")
                k to v
            }
            OskeManifestEntry(
                formId = fields["form"] ?: return@mapNotNull null,
                version = fields["version"] ?: "1.0",
                answerKeyCount = fields["keys"]?.toIntOrNull() ?: 0
            )
        }
        OskeManifest(version, entries)
    }.getOrNull()

    override fun readForm(formId: String): OskeForm? = runCatching {
        val file = File(root, "forms/$formId.json").takeIf { it.canRead() } ?: return null
        OskeJson.parseForm(file.readText())
    }.getOrNull()

    override fun readAnswerKey(ecgId: String, formId: String): OskeAnswerKey? = runCatching {
        val file = File(root, "answers/$ecgId/$formId.json").takeIf { it.canRead() } ?: return null
        OskeJson.parseAnswerKey(file.readText())
    }.getOrNull()

    override fun writeAnswerKey(key: OskeAnswerKey): Boolean {
        val file = File(root, "answers/${key.ecgId}/${key.formId}.json")
        file.parentFile?.mkdirs()
        return atomicWriteText(file, OskeJson.serializeAnswerKey(key))
    }

    override fun listAnswerKeyEcgIds(formId: String): List<String> {
        val answersDir = File(root, "answers")
        return answersDir.listFiles { f -> f.isDirectory && File(f, "$formId.json").exists() }
            ?.map { it.name } ?: emptyList()
    }

    private fun atomicWriteText(file: File, text: String): Boolean = runCatching {
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { it.write(text.toByteArray()) }
        if (file.exists()) file.delete()
        temp.renameTo(file)
    }.getOrDefault(false)
}

class OskeRepository(private var source: IOskeSource) {
    private var manifest: OskeManifest? = null
    private val formCache = mutableMapOf<String, OskeForm>()

    fun getManifest(): OskeManifest? {
        if (manifest == null) manifest = source.readManifest()
        return manifest
    }

    fun getForm(formId: String): OskeForm? {
        if (!formCache.containsKey(formId)) {
            source.readForm(formId)?.let { formCache[formId] = it }
        }
        return formCache[formId]
    }

    fun getAnswerKey(ecgId: String, formId: String): OskeAnswerKey? = source.readAnswerKey(ecgId, formId)

    fun saveAnswerKey(key: OskeAnswerKey): Boolean {
        val ok = source.writeAnswerKey(key)
        if (ok) {
            manifest = null // invalidate to refresh counts if needed
        }
        return ok
    }

    fun getAnswerKeyEcgIds(formId: String): List<String> = source.listAnswerKeyEcgIds(formId)

    fun swapSource(newSource: IOskeSource) {
        source = newSource
        manifest = null
        formCache.clear()
    }
}

class OskeResultStore(val root: File) {
    init {
        root.mkdirs()
    }

    fun save(result: OskeResult): Boolean {
        val filename = "${result.timestamp}_${result.student.fullName.replace(" ", "_")}.json"
        val file = File(root, filename)
        return runCatching {
            file.writeText(OskeJson.serializeResult(result))
            true
        }.getOrDefault(false)
    }

    fun list(): List<OskeResult> {
        return root.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching { OskeJson.parseResult(file.readText()) }.getOrNull()
            }?.sortedByDescending { it.timestamp } ?: emptyList()
    }
}
