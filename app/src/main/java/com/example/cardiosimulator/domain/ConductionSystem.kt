package com.example.cardiosimulator.domain

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class ConductionNode(
    val key: String,
    val labelEn: String,
    val labelRu: String,
    val anchor: FloatArray, // [x, y, z] in model space
    val arrivalMs: Float
)

object ConductionSystem {
    val Template = listOf(
        ConductionNode("sa", "SA node", "СА-узел", floatArrayOf(0f, 0f, 0f), 0f),
        ConductionNode("atria", "Atria", "Предсердия", floatArrayOf(0f, 0f, 0f), 60f),
        ConductionNode("av", "AV node", "АВ-узел", floatArrayOf(0f, 0f, 0f), 100f),
        ConductionNode("his", "His bundle", "Пучок Гиса", floatArrayOf(0f, 0f, 0f), 170f),
        ConductionNode("bundles", "Bundle branches", "Ножки пучка Гиса", floatArrayOf(0f, 0f, 0f), 190f),
        ConductionNode("purkinje", "Purkinje fibers", "Волокна Пуркинье", floatArrayOf(0f, 0f, 0f), 210f),
        ConductionNode("apex", "Ventricular apex", "Верхушка желудочков", floatArrayOf(0f, 0f, 0f), 245f)
    )

    fun getPhaseName(nodeKey: String, isEntering: Boolean, locale: String): String {
        return when (nodeKey) {
            "sa" -> if (isEntering) "" else if (locale == "ru") "P-волна" else "P wave"
            "atria" -> if (isEntering) (if (locale == "ru") "P-волна" else "P wave") else (if (locale == "ru") "Сегмент PR" else "PR segment")
            "av" -> if (isEntering) (if (locale == "ru") "Сегмент PR" else "PR segment") else (if (locale == "ru") "Комплекс QRS" else "QRS complex")
            "his", "bundles", "purkinje", "apex" -> if (isEntering) (if (locale == "ru") "Комплекс QRS" else "QRS complex") else (if (locale == "ru") "Диастола" else "Diastole")
            else -> ""
        }
    }
}

class ConductionStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val fileName = "heart.conduction.json"

    fun load(): List<ConductionNode>? {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return null
            val text = file.readText()
            json.decodeFromString<List<ConductionNode>>(text)
        } catch (e: Exception) {
            null
        }
    }

    fun save(nodes: List<ConductionNode>) {
        try {
            val file = File(context.filesDir, fileName)
            val text = json.encodeToString(nodes)
            file.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
