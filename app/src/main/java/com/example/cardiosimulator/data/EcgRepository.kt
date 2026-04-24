package com.example.cardiosimulator.data

import android.content.res.AssetManager
import com.example.cardiosimulator.domain.EcgSeries
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.WaveformPart

data class PathologyGroup(
    val pathology: String,
    val displayTitle: String,
    val seriesIdentyByLead: Map<Lead, String>,
)

class EcgRepository(private val assets: AssetManager) {

    @Volatile private var seriesIndex: List<EcgSeries> = emptyList()
    @Volatile private var partsIndex: Map<String, WaveformPart> = emptyMap()
    @Volatile private var loaded: Boolean = false

    fun load() {
        if (loaded) return
        seriesIndex = readAll(SERIES_DIR) { EcgSeries.parse(it) }
        partsIndex = readAll(PARTS_DIR) { WaveformPart.parse(it) }
            .filter { it.identy.isNotBlank() }
            .associateBy { it.identy }
        loaded = true
    }

    fun pathologies(): List<PathologyGroup> =
        seriesIndex
            .asSequence()
            .filter { !it.pathology.isNullOrBlank() && it.title.isNotBlank() }
            .groupBy { it.pathology!! }
            .map { (pathology, list) ->
                val title = stripTrailingLead(list.first().title)
                val byLead = list.mapNotNull { s -> s.lead?.let { it to s.identy } }.toMap()
                PathologyGroup(pathology, title, byLead)
            }
            .sortedBy { it.displayTitle.lowercase() }

    fun series(identy: String): EcgSeries? = seriesIndex.firstOrNull { it.identy == identy }

    fun part(identy: String): WaveformPart? = partsIndex[identy]

    fun partsForSeries(identy: String): List<WaveformPart> =
        series(identy)?.partRefs
            ?.mapNotNull { partsIndex[it.partIdenty] }
            .orEmpty()

    private fun <T> readAll(dir: String, parse: (String) -> T): List<T> =
        (assets.list(dir) ?: emptyArray())
            .asSequence()
            .mapNotNull { name ->
                runCatching {
                    assets.open("$dir/$name").use { stream ->
                        parse(stream.readBytes().toString(Charsets.ISO_8859_1))
                    }
                }.getOrNull()
            }
            .toList()

    private fun stripTrailingLead(title: String): String {
        for (l in LEAD_SUFFIXES) if (title.endsWith(l, ignoreCase = true)) {
            return title.dropLast(l.length).trimEnd(' ', ',', '-').trim()
        }
        return title
    }

    companion object {
        private const val PARTS_DIR = "Parts"
        private const val SERIES_DIR = "Series"
        private val LEAD_SUFFIXES = listOf(
            " aVR", " aVL", " aVF",
            " V1", " V2", " V3", " V4", " V5", " V6",
            " III", " II", " I"
        )
    }
}
