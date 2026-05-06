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

    fun allSeries(): List<EcgSeries> = seriesIndex

    fun part(identy: String): WaveformPart? = partsIndex[identy]

    fun allParts(): List<WaveformPart> = partsIndex.values.toList()

    fun partsForSeries(identy: String): List<WaveformPart> =
        series(identy)?.partRefs
            ?.mapNotNull { partsIndex[it.partIdenty] }
            .orEmpty()

    fun assembleWaveform(seriesIdenty: String): List<Float> {
        val series = series(seriesIdenty) ?: return emptyList()
        val out = ArrayList<Float>(series.partRefs.size * 64)
        for (ref in series.partRefs) {
            val part = partsIndex[ref.partIdenty] ?: continue
            val amp = if (part.amplitude > 0f) part.amplitude else 1f
            for (sample in part.samples) {
                out += (sample - SAMPLE_BASELINE) * amp
            }
        }
        return out
    }

    private fun <T> readAll(dir: String, parse: (String) -> T): List<T> =
        (assets.list(dir) ?: emptyArray())
            .asSequence()
            .mapNotNull { name ->
                runCatching {
                    assets.open("$dir/$name").use { stream ->
                        parse(stream.readBytes().toString(java.nio.charset.Charset.forName("windows-1251")))
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
        private const val SAMPLE_BASELINE = 1024f
        private val LEAD_SUFFIXES = listOf(
            " aVR", " aVL", " aVF",
            " V1", " V2", " V3", " V4", " V5", " V6",
            " III", " II", " I"
        )
    }
}
