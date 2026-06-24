package com.example.cardiosimulator.domain

import java.io.File

/**
 * Data-driven group taxonomy for pathologies.
 * Reference: Windows `App/Localization/PathologyGroups.cs`.
 */
class PathologyGroups {
    data class Group(val key: String, val names: Map<String, String>)

    private var groups = emptyList<Group>()
    private var orderedKeys = emptyList<String>()

    fun load(file: File) {
        if (!file.exists()) {
            groups = emptyList()
            orderedKeys = emptyList()
            return
        }

        val newGroups = mutableListOf<Group>()
        file.forEachLine { line ->
            if (line.startsWith("group:")) {
                val fields = line.split(';')
                val key = fields.first().removePrefix("group:")
                val names = fields.drop(1).associate {
                    val parts = it.split(':')
                    parts[0] to parts.drop(1).joinToString(":")
                }
                newGroups.add(Group(key, names))
            }
        }
        groups = newGroups
        orderedKeys = newGroups.map { it.key }
    }

    fun getOrderedKeys(): List<String> = orderedKeys

    fun isKnown(key: String): Boolean = orderedKeys.contains(key)

    fun displayName(key: String, languageTag: String): String {
        if (key == OTHER_KEY) return "Other" // Fallback, should be localized via resources in UI

        val group = groups.find { it.key == key }
        return group?.names?.get(languageTag)
            ?: group?.names?.get("en")
            ?: FALLBACK_NAMES[key]?.get(languageTag)
            ?: FALLBACK_NAMES[key]?.get("en")
            ?: key
    }

    companion object {
        const val OTHER_KEY = "OTHER"

        // Built-in fallback list for datasets that ship no groups.txt.
        // These keys should ideally match the ones in strings.xml for localization.
        private val FALLBACK_NAMES = mapOf(
            "sinus" to mapOf("ru" to "Синусовые ритмы", "en" to "Sinus Rhythms"),
            "arrhythmia" to mapOf("ru" to "Аритмии", "en" to "Arrhythmias"),
            "conduction" to mapOf("ru" to "Нарушения проводимости", "en" to "Conduction Disturbances"),
            "hypertrophy" to mapOf("ru" to "Гипертрофии", "en" to "Hypertrophy"),
            "ischemia" to mapOf("ru" to "Ишемия", "en" to "Ischemia"),
            "infarction" to mapOf("ru" to "Инфаркт миокарда", "en" to "Myocardial Infarction"),
            "electrolyte" to mapOf("ru" to "Электролитные нарушения", "en" to "Electrolyte Disturbances"),
            "syndromes" to mapOf("ru" to "Синдромы", "en" to "Syndromes"),
            "pacemaker" to mapOf("ru" to "Электрокардиостимуляция", "en" to "Pacemaker"),
            "special" to mapOf("ru" to "Особые состояния", "en" to "Special Conditions"),
            "pediatric" to mapOf("ru" to "Педиатрия", "en" to "Pediatric"),
            "newborn" to mapOf("ru" to "Новорожденные", "en" to "Newborn"),
            "pregnant" to mapOf("ru" to "Беременность", "en" to "Pregnancy"),
            "clinical" to mapOf("ru" to "Клинические случаи", "en" to "Clinical Cases")
        )

        val BUILTIN_KEYS = FALLBACK_NAMES.keys.toList()
    }
}
