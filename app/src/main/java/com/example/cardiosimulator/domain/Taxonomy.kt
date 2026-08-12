package com.example.cardiosimulator.domain

import android.content.res.AssetManager
import android.util.Log
import java.util.Locale

/**
 * One acronym in the canonical ECG taxonomy — the fixed clinical dictionary the customer supplied.
 * Each acronym is the join key that ties a rhythm (pathology), a course subsection/lecture,
 * and a test/exam question to the same clinical concept, and the anchor student results roll up through.
 */
data class TaxonomyEntry(
    val acronym: String,
    val nameRu: String,
    val group: String,
    val section: Int,
    val subsection: String,
    val subsectionTitle: String,
    val altSubsections: List<String>
) {
    /**
     * The two-level key (X.Y) the Learning Scale groups subtopics by — the primary subsection
     * trimmed to its first two dotted components (4.6.2 → 4.6; 3.2 stays 3.2).
     * This is the node student mastery is aggregated into.
     */
    val subtopicKey: String get() = Taxonomy.subtopicKeyOf(subsection)
}

/**
 * The canonical ECG acronym taxonomy: a read-only, case-insensitive dictionary of
 * [TaxonomyEntry] loaded from the embedded `taxonomy.tsv`.
 */
class Taxonomy(private val entries: List<TaxonomyEntry>) {
    private val byAcronym: Map<String, TaxonomyEntry> = entries.associateBy { it.acronym.uppercase() }

    /** Every acronym, in the file's order (section → subsection → acronym). */
    val allEntries: List<TaxonomyEntry> get() = entries

    /** Number of acronyms in the taxonomy. */
    val count: Int get() = entries.size

    /**
     * Normalizes a raw acronym token to its canonical form (trim + upper-case), or null
     * when blank. Does *not* check membership — see [find].
     */
    fun find(acronym: String?): TaxonomyEntry? {
        val key = normalize(acronym) ?: return null
        return byAcronym[key]
    }

    /** True when [acronym] is a known taxonomy code. */
    fun contains(acronym: String?): Boolean = find(acronym) != null

    /** All acronyms whose primary subsection rolls up into the given subtopic key (X.Y). */
    fun forSubtopic(subtopicKey: String): List<TaxonomyEntry> =
        entries.filter { it.subtopicKey.equals(subtopicKey, ignoreCase = true) }

    /** All acronyms in a top-level section («Раздел N»). */
    fun forSection(section: Int): List<TaxonomyEntry> =
        entries.filter { it.section == section }

    /** All acronyms in a rhythm group (groups.txt key). */
    fun forGroup(groupKey: String): List<TaxonomyEntry> =
        entries.filter { it.group.equals(groupKey, ignoreCase = true) }

    companion object {
        private const val TAG = "Taxonomy"
        private const val ASSET_PATH = "taxonomy.tsv"

        private var _shared: Taxonomy? = null

        /**
         * The app-wide taxonomy, loaded once from the bundled `taxonomy.tsv`.
         * Falls back to [Empty] if not initialized or unreadable.
         */
        val shared: Taxonomy get() = _shared ?: Empty

        /**
         * Initializes the shared taxonomy instance from assets.
         */
        fun initialize(assets: AssetManager) {
            if (_shared != null) return
            _shared = try {
                val tsv = assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
                parse(tsv)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load taxonomy from assets", e)
                Empty
            }
        }

        /**
         * Normalizes a raw acronym token to its canonical form (trim + upper-case), or null
         * when blank.
         */
        fun normalize(acronym: String?): String? {
            val t = acronym?.trim() ?: ""
            return if (t.isEmpty()) null else t.uppercase()
        }

        /**
         * The two-level subtopic key (X.Y) for a subsection node.
         */
        fun subtopicKeyOf(subsection: String): String {
            val s = subsection.trim()
            val parts = s.split('.')
            return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else s
        }

        /**
         * Parses the tab-separated taxonomy text.
         */
        fun parse(tsv: String): Taxonomy {
            val entries = mutableListOf<TaxonomyEntry>()
            tsv.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach

                val c = line.split('\t')
                if (c.size < 6) return@forEach

                val acronym = normalize(c[0]) ?: return@forEach
                if (acronym.equals("ACRONYM", ignoreCase = true)) return@forEach

                val subsection = c[4].trim()
                val section = c[3].trim().toIntOrNull() ?: sectionOf(subsection)
                val alt = if (c.size > 6) {
                    c[6].split(';').map { it.trim() }.filter { it.isNotEmpty() }
                } else emptyList()

                entries.add(
                    TaxonomyEntry(
                        acronym = acronym,
                        nameRu = c[1].trim(),
                        group = c[2].trim(),
                        section = section,
                        subsection = subsection,
                        subsectionTitle = c[5].trim(),
                        altSubsections = alt
                    )
                )
            }
            return Taxonomy(entries)
        }

        private fun sectionOf(subsection: String): Int {
            val head = subsection.split('.').firstOrNull() ?: ""
            return head.toIntOrNull() ?: 0
        }

        /** An empty taxonomy fallback. */
        val Empty = Taxonomy(emptyList())
    }
}
