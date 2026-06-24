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

    fun load(text: String?) {
        if (text.isNullOrBlank()) {
            groups = emptyList()
            orderedKeys = emptyList()
            return
        }

        val newGroups = mutableListOf<Group>()
        text.lineSequence().forEach { line ->
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

    fun displayName(key: String, languageTag: String, resourceResolver: (String) -> String?): String {
        if (key == OTHER_KEY) return resourceResolver("group_other") ?: "Other"

        val group = groups.find { it.key == key }
        val nameFromTxt = group?.names?.get(languageTag) ?: group?.names?.get("en")
        if (nameFromTxt != null) return nameFromTxt

        return resourceResolver("group_$key") ?: key
    }

    companion object {
        const val OTHER_KEY = "OTHER"

        val BUILTIN_KEYS = listOf(
            "sinus", "arrhythmia", "conduction", "hypertrophy", "ischemia",
            "infarction", "electrolyte", "syndromes", "pacemaker", "special",
            "pediatric", "newborn", "pregnant", "clinical"
        )
    }
}
