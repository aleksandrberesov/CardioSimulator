package com.example.cardiosimulator.domain

import android.util.Base64

/**
 * Serializes and parses [TipOverlay] lists for persistence and embedding in HTML.
 */
object TipOverlaySerializer {

    fun parse(field: String?): List<TipOverlay> {
        if (field.isNullOrBlank()) return emptyList()
        val out = mutableListOf<TipOverlay>()
        for (token in field.split('~')) {
            val parts = token.split('|')
            if (parts.size < 5) continue
            val kind = runCatching { TipOverlayKind.valueOf(parts[0]) }.getOrNull() ?: continue
            val endCap = runCatching { TipLineEndCap.valueOf(parts[1]) }.getOrNull() ?: TipLineEndCap.Plain
            val lead = Lead.fromToken(parts[2])
            val text = unescapeTipText(parts[3])
            val pointsParts = parts[4].split(';')
            val points = mutableListOf<TipPoint>()
            for (pt in pointsParts) {
                val coords = pt.split(':')
                if (coords.size != 2) continue
                val sample = coords[0].toFloatOrNull() ?: continue
                val amp = coords[1].toFloatOrNull() ?: continue
                points.add(TipPoint(sample, amp))
            }
            out.add(TipOverlay(kind, points, text, lead, endCap))
        }
        return out
    }

    fun serialize(tips: List<TipOverlay>): String {
        return tips.joinToString("~") { tip ->
            val pointsStr = tip.points.joinToString(";") {
                String.format(java.util.Locale.US, "%.3f:%.3f", it.sample, it.adc)
            }
            "${tip.kind.name}|${tip.endCap.name}|${tip.lead?.name ?: ""}|${escapeTipText(tip.text)}|$pointsStr"
        }
    }

    fun encodeAttribute(tips: List<TipOverlay>): String {
        val raw = serialize(tips)
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decodeAttribute(base64: String?): List<TipOverlay> {
        if (base64.isNullOrBlank()) return emptyList()
        return try {
            val raw = String(Base64.decode(base64, Base64.NO_WRAP), Charsets.UTF_8)
            parse(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun escapeTipText(text: String?): String {
        if (text == null) return ""
        return text.replace("%", "%25")
            .replace("|", "%7C")
            .replace("~", "%7E")
            .replace("\r", "%0D")
            .replace("\n", "%0A")
    }

    private fun unescapeTipText(text: String): String? {
        if (text.isEmpty()) return null
        return text.replace("%0A", "\n")
            .replace("%0D", "\r")
            .replace("%7E", "~")
            .replace("%7C", "|")
            .replace("%25", "%")
    }
}
