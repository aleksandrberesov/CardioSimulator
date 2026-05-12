package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.EcgSeries
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.WaveformPart
import java.nio.charset.Charset

data class PathologyGroup(
    val pathology: String,
    val displayTitle: String,
    val seriesIdentityByLead: Map<Lead, String>,
    val fileName: String,
)

class EcgRepository(private var source: EcgSource) {

    @Volatile private var seriesIndex: List<EcgSeries> = emptyList()
    @Volatile private var partsIndex: Map<String, WaveformPart> = emptyMap()
    @Volatile private var loaded: Boolean = false

    /** Replace the backing source and force a reload on next [load] call. */
    fun setSource(newSource: EcgSource) {
        source = newSource
        loaded = false
        seriesIndex = emptyList()
        partsIndex = emptyMap()
    }

    fun load() {
        if (loaded) return
        seriesIndex = source.listSeries()
            .mapNotNull { name -> source.readSeries(name)?.let { runCatching { EcgSeries.parse(it, name) }.getOrNull() } }
        partsIndex = source.listParts()
            .mapNotNull { name -> source.readPart(name)?.let { runCatching { WaveformPart.parse(it) }.getOrNull() } }
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
                val title = stripTrailingLead(fixEncoding(list.first().title))
                val rawFileName = list.first().fileName
                val fileName = stripTrailingLead(fixEncoding(rawFileName.substringBeforeLast('.')
                    .replace('_', ' ')
                    .trim()))

                val byLead = list.mapNotNull { s -> s.lead?.let { it to s.identy } }.toMap()
                PathologyGroup(pathology, title, byLead, fileName)
            }
            .sortedBy { it.displayTitle.lowercase() }

    fun series(identy: String): EcgSeries? = seriesIndex.firstOrNull { it.identy == identy }

    fun allSeries(): List<EcgSeries> = seriesIndex

    fun allParts(): List<WaveformPart> = partsIndex.values.toList()

    /**
     * Concatenates the samples of all parts referenced by [seriesIdenty]
     * into a single baseline-zeroed float list. Retains the legacy
     * `amplitude` factor for viewer fixtures that bake their own gain;
     * see [assembleWaveformParts] for the editor-friendly version that
     * preserves per-part boundaries and skips the legacy amplitude
     * multiplication so the renderer can apply per-part `samplesPerMv`.
     */
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

    /**
     * Returns the parts of a series in render order, **without** applying
     * the legacy `amplitude` multiplication. The renderer is expected to
     * apply per-part `samplesPerMv` (`AMax/AValue`) instead — see
     * [com.example.cardiosimulator.domain.WaveformPart.samplesPerMv]. This
     * is the editor-mode path that avoids double-scaling against
     * `amplitude` and stays consistent with RP5's `Frame.Segments.pas`.
     */
    fun assembleWaveformParts(seriesIdenty: String): List<WaveformPart> {
        val series = series(seriesIdenty) ?: return emptyList()
        return series.partRefs.mapNotNull { partsIndex[it.partIdenty] }
    }

    /** Baseline-zeroed samples (no amplitude scaling). */
    fun baselineZeroedSamples(part: WaveformPart): List<Float> =
        part.samples.map { (it - SAMPLE_BASELINE).toFloat() }

    private fun stripTrailingLead(title: String): String {
        val delimiters = listOf(" ", "_", "-")
        for (l in LEAD_SUFFIXES) {
            val suffix = l.trim()
            for (d in delimiters) {
                val fullSuffix = "$d$suffix"
                if (title.endsWith(fullSuffix, ignoreCase = true)) {
                    return title.dropLast(fullSuffix.length).trimEnd(' ', ',', '-', '_').trim()
                }
            }
            if (title.endsWith(suffix, ignoreCase = true)) {
                return title.dropLast(suffix.length).trimEnd(' ', ',', '-', '_').trim()
            }
        }
        return title
    }

    /**
     * Attempts to heal strings that look like they were decoded with the wrong encoding.
     * Handles common Russian mojibake and encoding artifacts.
     */
    private fun fixEncoding(s: String): String {
        if (s.isEmpty()) return s
        
        // Remove null characters and other common control noise that can look like squares
        val clean = s.replace("\u0000", "").trim()
        if (clean.isEmpty()) return ""

        val hasCyrillic = clean.any { it in '\u0400'..'\u04FF' }
        val hasReplacement = clean.contains('\uFFFD')

        // Case 1: CP1251 as ISO-8859-1 (or other single-byte Western encoding)
        // Cyrillic letters in CP1251 are 0xC0-0xFF, which in ISO-8859-1 are À-ÿ.
        if (!hasCyrillic && !hasReplacement && clean.any { it in '\u00C0'..'\u00FF' }) {
            try {
                val bytes = clean.toByteArray(Charset.forName("ISO-8859-1"))
                val fixed = String(bytes, Charset.forName("windows-1251"))
                if (fixed.any { it in '\u0400'..'\u04FF' }) return fixed
            } catch (e: Exception) { /* ignore */ }
        }

        // Case 2: UTF-8 as windows-1251 (e.g. "РЎРёРЅСѓСЃ" -> "Синус")
        if ((hasCyrillic || hasReplacement) && clean.contains('Р') && (clean.contains('Ў') || clean.contains('°'))) {
            try {
                val bytes = clean.toByteArray(Charset.forName("windows-1251"))
                val fixed = String(bytes, Charsets.UTF_8)
                if (fixed.any { it in '\u0400'..'\u04FF' }) return fixed
            } catch (e: Exception) { /* ignore */ }
        }

        // Case 3: CP866 as CP1252/Western (e.g. "æ¿¡Òß«óá´" -> "ц...Тяы...б")
        // CP866 Russian letters are in 0x80-0xAF and 0xE0-0xEF.
        // In CP1252 these look like Western accented letters and symbols (¡, «, æ, á, etc.)
        if (!hasCyrillic && clean.any { it in '¡'..'¯' || it in 'à'..'ï' }) {
            try {
                // Try treating as CP1252 (Western) bytes and reading as CP866
                val bytes = clean.toByteArray(Charset.forName("windows-1252"))
                val fixed = String(bytes, Charset.forName("IBM866"))
                if (fixed.any { it in '\u0400'..'\u04FF' }) return fixed
            } catch (e: Exception) { /* ignore */ }
        }

        return clean
    }

    companion object {
        private const val SAMPLE_BASELINE = 1024f
        private val LEAD_SUFFIXES = listOf(
            " aVR", " aVL", " aVF",
            " V1", " V2", " V3", " V4", " V5", " V6",
            " III", " II", " I"
        )
    }
}
