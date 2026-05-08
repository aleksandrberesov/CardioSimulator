package com.example.cardiosimulator.domain

import java.io.File
import java.nio.charset.Charset

enum class Lead {
    I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6;

    companion object {
        fun fromToken(raw: String): Lead? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

enum class EasingCurve {
    LINEAR, SINE, SINE_IN, SINE_OUT,
    QUAD, QUAD_IN, QUAD_OUT,
    CUBIC, CUBIC_IN, CUBIC_OUT,
    QUART, QUART_IN, QUART_OUT,
    CIRC, CIRC_IN, CIRC_OUT;

    companion object {
        fun fromToken(raw: String?): EasingCurve = when (val t = raw?.trim()?.lowercase()) {
            null, "" -> LINEAR
            else -> entries.firstOrNull { it.name.equals(t.replace('-', '_'), ignoreCase = true) } ?: LINEAR
        }
    }
}

data class AnchorPoint(
    val x: Float,
    val y: Float,
    val curve: EasingCurve = EasingCurve.LINEAR,
)

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
) {
    companion object {
        fun parse(text: String): WaveformPart {
            val kv = EcgFileFormat.readKeyValues(text)
            return WaveformPart(
                identy = kv["identy"].orEmpty(),
                title = kv["title"].orEmpty(),
                lead = kv["source"]?.let { EcgFileFormat.parseSource(it).lead }
                    ?: kv["lead"]?.let { Lead.fromToken(it) },
                pathology = kv["pathology"] ?: kv["source"]?.let { EcgFileFormat.parseSource(it).pathology },
                amplitude = EcgFileFormat.parseDecimal(kv["amplitude"]) ?: 0f,
                duration = kv["duration"]?.trim()?.toIntOrNull() ?: 0,
                samples = kv["points"].orEmpty()
                    .split(',')
                    .mapNotNull { it.trim().toIntOrNull() },
                source = kv["source"]?.let { EcgFileFormat.parseSource(it) },
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
            )
        }

        fun parse(file: File): EcgSeries = parse(file.readBytes().decodeEcgText(), file.name)
    }
}

/**
 * Decodes a byte array into a string, attempting UTF-8 first and falling back
 * to windows-1251 if it doesn't look like valid UTF-8.
 */
private fun ByteArray.decodeEcgText(): String {
    val utf8 = Charsets.UTF_8
    val decoded = toString(utf8)
    return if (decoded.contains('\uFFFD')) {
        toString(Charset.forName("windows-1251"))
    } else {
        decoded
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
                )
            } else {
                anchors += AnchorPoint(
                    x = parseDecimal(parts.getOrNull(0)) ?: 0f,
                    y = parseDecimal(parts.getOrNull(1)) ?: 0f,
                    curve = EasingCurve.fromToken(parts.getOrNull(2)),
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
}
