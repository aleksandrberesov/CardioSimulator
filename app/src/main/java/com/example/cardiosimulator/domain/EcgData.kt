package com.example.cardiosimulator.domain

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class Lead {
    I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6;

    companion object {
        fun fromToken(raw: String): Lead? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

data class AnchorPoint(
    val x: Float,
    val y: Float,
)

/**
 * Block flags used in the series block list to mark behaviour: high-frequency,
 * sound, fixed-duration, broken (skip rendering on certain renderers),
 * beat-aware, skip-QRS.
 *
 * Serialized as a comma-separated subset of the constants below, prefixed by
 * `flags=` inside a tuple. Empty/missing flags decode to [NONE].
 */
@JvmInline
value class BlockFlags(val bits: Int) {
    fun has(flag: BlockFlags): Boolean = (bits and flag.bits) != 0
    operator fun plus(other: BlockFlags): BlockFlags = BlockFlags(bits or other.bits)
    operator fun minus(other: BlockFlags): BlockFlags = BlockFlags(bits and other.bits.inv())

    fun toTokenList(): List<String> = buildList {
        if (has(FREQUENTLY)) add("FREQUENTLY")
        if (has(WITHSOUND)) add("WITHSOUND")
        if (has(DURATIONFIXED)) add("DURATIONFIXED")
        if (has(BROKEN)) add("BROKEN")
        if (has(BEAT)) add("BEAT")
        if (has(SKIPQRS)) add("SKIPQRS")
    }

    companion object {
        val NONE = BlockFlags(0)
        val FREQUENTLY = BlockFlags(1 shl 0)
        val WITHSOUND = BlockFlags(1 shl 1)
        val DURATIONFIXED = BlockFlags(1 shl 2)
        val BROKEN = BlockFlags(1 shl 3)
        val BEAT = BlockFlags(1 shl 4)
        val SKIPQRS = BlockFlags(1 shl 5)

        fun fromToken(raw: String?): BlockFlags {
            if (raw.isNullOrBlank()) return NONE
            var b = NONE
            for (tok in raw.split('|', ',', ' ').map { it.trim().uppercase() }.filter { it.isNotEmpty() }) {
                b = b + when (tok) {
                    "FREQUENTLY" -> FREQUENTLY
                    "WITHSOUND" -> WITHSOUND
                    "DURATIONFIXED" -> DURATIONFIXED
                    "BROKEN" -> BROKEN
                    "BEAT" -> BEAT
                    "SKIPQRS" -> SKIPQRS
                    else -> NONE
                }
            }
            return b
        }
    }
}

data class SourceSpec(
    val lead: Lead?,
    val pathology: String?,
    val max: Int?,
    val value: Int?,
    val name: String?,
    val identy: String?,
    val localizationTag: Int?,
    val center: Pair<Float, Float>?,
    val anchors: List<AnchorPoint>,
    val seriesRefs: List<SeriesPartRef>,
    val extras: Map<String, String>,
)

data class SeriesPartRef(
    val x: Float,
    val y: Float,
    val partIdenty: String,
    val offset: Int,
    val flags: BlockFlags = BlockFlags.NONE,
)

data class WaveformPart(
    val identy: String,
    val title: String,
    val lead: Lead?,
    val pathology: String?,
    val amplitude: Float,
    val duration: Int,
    val samples: List<Int>,
    val source: SourceSpec?,
    /**
     * Per-part `AMax` (from `max:` inside `source:`). Defaults to
     * 200 when absent. Together with [aValue], defines the
     * source coordinate system: `AMax` source-units == `AValue` mV.
     */
    val aMax: Int = source?.max ?: 200,
    /** Per-part `AValue` (from `value:`). Defaults to 2. */
    val aValue: Int = source?.value ?: 2,
) {
    /** Effective sample rate (Hz), derived from `samples.size / duration`. */
    val effectiveSampleRateHz: Float
        get() = if (duration > 0 && samples.isNotEmpty()) {
            samples.size / (duration / 1000f)
        } else 500f

    /**
     * How many raw sample units correspond to 1 mV at this record's calibration.
     * Expressed as a sample-per-mV factor for the renderer.
     */
    val samplesPerMv: Float
        get() = aMax.toFloat() / aValue.coerceAtLeast(1).toFloat()

    companion object {
        fun parse(text: String): WaveformPart {
            val kv = EcgFileFormat.readKeyValues(text)
            val src = kv["source"]?.let { EcgFileFormat.parseSource(it) }
            return WaveformPart(
                identy = kv["identy"].orEmpty(),
                title = kv["title"].orEmpty(),
                lead = src?.lead ?: kv["lead"]?.let { Lead.fromToken(it) },
                pathology = kv["pathology"] ?: src?.pathology,
                amplitude = EcgFileFormat.parseDecimal(kv["amplitude"]) ?: 0f,
                duration = kv["duration"]?.trim()?.toIntOrNull() ?: 0,
                samples = kv["points"].orEmpty()
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() },
                source = src,
                aMax = src?.max ?: 200,
                aValue = src?.value ?: 2,
            )
        }

        fun parse(file: File): WaveformPart = parse(file.readBytes().decodeEcgText())
    }
}

data class EcgSeries(
    val identy: String,
    val title: String,
    val displayName: String,
    val lead: Lead?,
    val pathology: String?,
    val params: String,
    val partRefs: List<SeriesPartRef>,
    val center: Pair<Float, Float>?,
    val localizationTag: Int?,
    val source: SourceSpec?,
    val fileName: String = "",
    /** Series-level `AMax` (from `source.max`), or 200 if absent. */
    val aMax: Int = source?.max ?: 200,
    /** Series-level `AValue` (from `source.value`), or 2 if absent. */
    val aValue: Int = source?.value ?: 2,
) {
    companion object {
        fun parse(text: String, fileName: String = ""): EcgSeries {
            val kv = EcgFileFormat.readKeyValues(text)
            val src = kv["source"]?.let { EcgFileFormat.parseSource(it) }
            return EcgSeries(
                identy = kv["identy"].orEmpty(),
                title = kv["title"].orEmpty(),
                displayName = kv["name"].orEmpty(),
                lead = kv["lead"]?.let { Lead.fromToken(it) } ?: src?.lead,
                pathology = kv["pathology"] ?: src?.pathology,
                params = kv["params"].orEmpty(),
                partRefs = src?.seriesRefs.orEmpty(),
                center = src?.center,
                localizationTag = src?.localizationTag,
                source = src,
                fileName = fileName,
                aMax = src?.max ?: 200,
                aValue = src?.value ?: 2,
            )
        }

        fun parse(file: File): EcgSeries = parse(file.readBytes().decodeEcgText(), file.name)
    }
}

/**
 * Decodes a byte array into a string, attempting several encodings common in
 * ECG datasets (UTF-8, windows-1251, CP866).
 */
private fun ByteArray.decodeEcgText(): String {
    if (isEmpty()) return ""

    // 1. Check for UTF-16 BOMs
    if (size >= 2) {
        val b0 = get(0).toInt() and 0xFF
        val b1 = get(1).toInt() and 0xFF
        if (b0 == 0xFF && b1 == 0xFE) return String(this, 2, size - 2, Charset.forName("UTF-16LE"))
        if (b0 == 0xFE && b1 == 0xFF) return String(this, 2, size - 2, Charset.forName("UTF-16BE"))
    }

    fun countRussian(s: String): Int = s.count { it in '\u0410'..'\u044F' || it == 'ё' || it == 'Ё' }

    // 2. Try UTF-8 strictly
    val sUtf8 = try {
        val decoder = Charsets.UTF_8.newDecoder()
        decoder.onMalformedInput(CodingErrorAction.REPORT)
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(this)).toString()
    } catch (e: Exception) {
        null
    }

    val s1251 = String(this, Charset.forName("windows-1251"))
    val s866 = String(this, Charset.forName("IBM866"))

    val countUtf8 = sUtf8?.let { countRussian(it) } ?: -1
    val count1251 = countRussian(s1251)
    val count866 = countRussian(s866)

    if (sUtf8 != null && countUtf8 >= count1251 && countUtf8 >= count866) {
        val hasSuspiciousChars = sUtf8.any { it in '\u2000'..'\uDFFF' }
        if (!hasSuspiciousChars) return sUtf8
    }

    return when {
        count866 > count1251 && count866 > countUtf8 -> s866
        count1251 > countUtf8 -> s1251
        sUtf8 != null -> sUtf8
        else -> s1251
    }
}

internal object EcgFileFormat {
    fun readKeyValues(text: String): Map<String, String> =
        text.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trimEnd('\r', '\n')
                val i = trimmed.indexOf(':')
                if (i <= 0) null else trimmed.substring(0, i).trim() to trimmed.substring(i + 1)
            }
            .toMap()

    fun parseDecimal(raw: String?): Float? =
        raw?.trim()?.replace(',', '.')?.toFloatOrNull()

    fun parseSource(raw: String): SourceSpec {
        val fields = splitTopLevel(raw, ';')
            .mapNotNull { f ->
                val i = f.indexOf(':')
                if (i <= 0) null else f.substring(0, i).trim() to f.substring(i + 1).trim()
            }
            .toMap()

        val pointsField = fields["points"].orEmpty()
        val tuples = splitParenTuples(pointsField)
        val anchors = mutableListOf<AnchorPoint>()
        val refs = mutableListOf<SeriesPartRef>()
        for (t in tuples) {
            val parts = t.split(',').map { it.trim() }
            if (parts.size >= 3 && parts[2].toIntOrNull() == null && parts.getOrNull(3)?.toIntOrNull() != null) {
                refs += SeriesPartRef(
                    x = parseDecimal(parts[0]) ?: 0f,
                    y = parseDecimal(parts[1]) ?: 0f,
                    partIdenty = parts[2],
                    offset = parts[3].toIntOrNull() ?: 0,
                    // Flags live in the optional 5th slot. Accept either a
                    // pipe/comma-joined token list or a plain integer bitmask.
                    flags = parts.getOrNull(4)?.let { raw ->
                        raw.toIntOrNull()?.let { BlockFlags(it) }
                            ?: BlockFlags.fromToken(raw)
                    } ?: BlockFlags.NONE,
                )
            } else {
                // A third tuple field (legacy easing-curve token) is ignored.
                anchors += AnchorPoint(
                    x = parseDecimal(parts.getOrNull(0)) ?: 0f,
                    y = parseDecimal(parts.getOrNull(1)) ?: 0f,
                )
            }
        }

        val center = fields["center"]?.let { c ->
            val inside = c.trim().removePrefix("(").removeSuffix(")")
            val xy = inside.split(',').map { it.trim() }
            if (xy.size >= 2) (parseDecimal(xy[0]) ?: 0f) to (parseDecimal(xy[1]) ?: 0f) else null
        }

        val known = setOf("lead", "pathology", "max", "value", "name", "identy",
            "LocalizationTag", "center", "points")
        return SourceSpec(
            lead = fields["lead"]?.let { Lead.fromToken(it) },
            pathology = fields["pathology"],
            max = fields["max"]?.toIntOrNull(),
            value = fields["value"]?.toIntOrNull(),
            name = fields["name"],
            identy = fields["identy"],
            localizationTag = fields["LocalizationTag"]?.toIntOrNull(),
            center = center,
            anchors = anchors,
            seriesRefs = refs,
            extras = fields.filterKeys { it !in known },
        )
    }

    // splits on `sep` outside of parentheses
    private fun splitTopLevel(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var depth = 0
        for (ch in s) {
            when (ch) {
                '(' -> { depth++; buf.append(ch) }
                ')' -> { depth--; buf.append(ch) }
                sep -> if (depth == 0) { out += buf.toString(); buf.clear() } else buf.append(ch)
                else -> buf.append(ch)
            }
        }
        if (buf.isNotEmpty()) out += buf.toString()
        return out
    }

    private fun splitParenTuples(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val start = s.indexOf('(', i)
            if (start < 0) break
            val end = s.indexOf(')', start + 1)
            if (end < 0) break
            out += s.substring(start + 1, end)
            i = end + 1
        }
        return out
    }

    // ─── Serializer (symmetric with the parser above) ───────────────────

    /** Trim trailing zeros so 1.0 -> "1", 1.500 -> "1.5". */
    fun formatDecimal(value: Float): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val s = "%.4f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
        return if (s.isEmpty() || s == "-") "0" else s
    }

    fun writeKeyValues(map: Map<String, String?>): String {
        val sb = StringBuilder()
        for ((k, v) in map) {
            if (v == null) continue
            sb.append(k).append(':').append(v).append('\n')
        }
        return sb.toString()
    }

    fun writeSource(src: SourceSpec): String {
        val sb = StringBuilder()
        fun field(k: String, v: String?) {
            if (!v.isNullOrEmpty()) {
                if (sb.isNotEmpty()) sb.append(';')
                sb.append(k).append(':').append(v)
            }
        }
        field("lead", src.lead?.name)
        field("pathology", src.pathology)
        field("max", src.max?.toString())
        field("value", src.value?.toString())
        field("name", src.name)
        field("identy", src.identy)
        field("LocalizationTag", src.localizationTag?.toString())
        src.center?.let { (cx, cy) ->
            field("center", "(${formatDecimal(cx)},${formatDecimal(cy)})")
        }

        val tupleBuf = StringBuilder()
        if (src.anchors.isNotEmpty()) {
            for (a in src.anchors) {
                if (tupleBuf.isNotEmpty()) tupleBuf.append(',')
                tupleBuf.append('(')
                    .append(formatDecimal(a.x)).append(',')
                    .append(formatDecimal(a.y))
                    .append(')')
            }
        }
        if (src.seriesRefs.isNotEmpty()) {
            for (r in src.seriesRefs) {
                if (tupleBuf.isNotEmpty()) tupleBuf.append(',')
                tupleBuf.append('(')
                    .append(formatDecimal(r.x)).append(',')
                    .append(formatDecimal(r.y)).append(',')
                    .append(r.partIdenty).append(',')
                    .append(r.offset)
                if (r.flags.bits != 0) {
                    tupleBuf.append(',').append(r.flags.toTokenList().joinToString("|"))
                }
                tupleBuf.append(')')
            }
        }
        if (tupleBuf.isNotEmpty()) field("points", tupleBuf.toString())

        for ((k, v) in src.extras) {
            field(k, v)
        }
        return sb.toString()
    }

    fun writePart(part: WaveformPart): String =
        writeKeyValues(linkedMapOf(
            "identy" to part.identy.ifEmpty { null },
            "title" to part.title.ifEmpty { null },
            "lead" to part.lead?.name,
            "pathology" to part.pathology,
            "amplitude" to if (part.amplitude != 0f) formatDecimal(part.amplitude) else null,
            "duration" to if (part.duration != 0) part.duration.toString() else null,
            "source" to part.source?.let { writeSource(it) },
            "points" to if (part.samples.isNotEmpty()) part.samples.joinToString(",") else null,
        ))

    fun writeSeries(series: EcgSeries): String =
        writeKeyValues(linkedMapOf(
            "identy" to series.identy.ifEmpty { null },
            "title" to series.title.ifEmpty { null },
            "name" to series.displayName.ifEmpty { null },
            "lead" to series.lead?.name,
            "pathology" to series.pathology,
            "params" to series.params.ifEmpty { null },
            "source" to (series.source ?: SourceSpec(
                lead = series.lead,
                pathology = series.pathology,
                max = series.aMax,
                value = series.aValue,
                name = series.displayName.ifEmpty { null },
                identy = series.identy.ifEmpty { null },
                localizationTag = series.localizationTag,
                center = series.center,
                anchors = emptyList(),
                seriesRefs = series.partRefs,
                extras = emptyMap(),
            )).let { writeSource(it) },
        ))
}
