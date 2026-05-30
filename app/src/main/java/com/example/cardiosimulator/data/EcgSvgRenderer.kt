package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Lead
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * One lead's baseline-zeroed samples, ready to draw as a static SVG trace.
 * Obtain [points] from `PathologyRepository.leadWaveform`.
 */
data class EcgTrace(val lead: Lead, val points: Points)

/**
 * Renders embedded ECG references (the `<ecg>` elements in lecture HTML,
 * see `docs/course-format.md` §5.1) as static inline SVG.
 *
 * Reuses the same projection as the live monitor — see
 * `ui/components/ChartCanvas.projectPath` and
 * `docs/ecg-rendering-pipeline.md` — only the output sink changes
 * (Compose `Path` → SVG `<path d>`). A lecture figure is a still
 * reference: no scrolling, sweep, or animation, and a fixed scale rather
 * than the live monitor zoom.
 *
 * Pure / Android-free, so it can run off the main thread and be unit
 * tested (see `EcgSvgRendererTest`).
 */
object EcgSvgRenderer {

    /** Fixed figure scale (mm → px). Reference figures don't use the live zoom. */
    const val PX_PER_MM: Float = 6f

    private val cal = EcgCalibration()
    private val pxPerSec = 25f * PX_PER_MM                 // 25 mm/s standard paper speed
    private val pxPerSample = pxPerSec / cal.sampleRateHz
    private val pxPerMv = cal.gainMmPerMv * PX_PER_MM
    private val pxPerAdcCount = pxPerMv / cal.adcCountsPerMv

    // Pink grid scheme — mirrors GridScheme.Pink (docs/ecg-rendering-pipeline.md §5d).
    private const val GRID_BG = "#FFF5F5"
    private const val GRID_SMALL = "#FDE4E4"
    private const val GRID_LARGE = "#F9BDBD"
    private const val TRACE_COLOR = "#111111"

    // Quoted attribute values may contain '>' but not an unescaped '"' (escape as &quot;).
    private val ecgTag = Regex("<ecg\\b((?:[^>\"]|\"[^\"]*\")*?)\\s*/?>(?:\\s*</ecg>)?", RegexOption.IGNORE_CASE)
    private val attr = Regex("([\\w-]+)\\s*=\\s*\"([^\"]*)\"")

    /**
     * Replaces every `<ecg …>` element in [html] with an inline-SVG figure.
     * [resolve] maps a `(pathologyId, lead)` to the traces to draw; a null
     * lead means "all leads" and is the caller's responsibility to expand
     * (e.g. over `LEAD_ORDER`). When [resolve] yields nothing (unknown
     * pathology, no data) a small placeholder figure is emitted so the
     * page never breaks.
     */
    fun substituteEcgTags(
        html: String,
        resolve: (pathologyId: String, lead: Lead?) -> List<EcgTrace>,
    ): String {
        var figureIndex = 0
        return ecgTag.replace(html) { match ->
            val attrs = attr.findAll(match.groupValues[1])
                .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val pathologyId = attrs["pathology"].orEmpty().trim()
            val leadToken = attrs["lead"]?.takeIf { it.isNotBlank() }
            val lead = leadToken?.let { Lead.fromToken(it) }
            val caption = attrs["caption"]?.takeIf { it.isNotBlank() }
            val traces = if (pathologyId.isEmpty()) emptyList() else resolve(pathologyId, lead)
            if (traces.isEmpty()) missingFigure(pathologyId, leadToken)
            else figureHtml(traces, caption, figureIndex++)
        }
    }

    /** Builds a `<figure>` with one stacked `<svg>` per trace. */
    fun figureHtml(traces: List<EcgTrace>, caption: String?, figureIndex: Int = 0): String {
        val rows = traces.mapIndexed { i, t -> leadSvg(t, "ecg$figureIndex-$i") }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        val cap = caption?.let { "\n  <figcaption>${escape(it)}</figcaption>" }.orEmpty()
        return "<figure class=\"ecg-figure\">\n$rows$cap\n</figure>"
    }

    private fun leadSvg(trace: EcgTrace, uid: String): String {
        val values = trace.points.values
        if (values.size < 2) return ""
        val widthPx = max(1f, (values.size - 1) * pxPerSample)
        val maxAbs = values.maxOf { abs(it) }
        // Half-height: enough to fit the signal, at least 5 mm, plus 2 mm padding.
        val halfPx = max(5f * PX_PER_MM, maxAbs * pxPerAdcCount + 2f * PX_PER_MM)
        val heightPx = halfPx * 2f
        val d = pathData(values, halfPx)
        val label = trace.lead.name
        return buildString {
            append("<svg class=\"ecg-lead\" xmlns=\"http://www.w3.org/2000/svg\" ")
            append("viewBox=\"0 0 ${fmt(widthPx)} ${fmt(heightPx)}\" ")
            append("width=\"${fmt(widthPx)}\" height=\"${fmt(heightPx)}\" ")
            append("preserveAspectRatio=\"xMidYMid meet\" role=\"img\" aria-label=\"ECG lead $label\">")
            append(gridDefs(uid))
            append("<rect width=\"${fmt(widthPx)}\" height=\"${fmt(heightPx)}\" fill=\"$GRID_BG\"/>")
            append("<rect width=\"${fmt(widthPx)}\" height=\"${fmt(heightPx)}\" fill=\"url(#$uid)\"/>")
            append("<path d=\"$d\" fill=\"none\" stroke=\"$TRACE_COLOR\" stroke-width=\"1.4\" ")
            append("stroke-linejoin=\"round\" stroke-linecap=\"round\"/>")
            append("<text x=\"6\" y=\"18\" font-family=\"serif\" font-weight=\"bold\" ")
            append("font-size=\"16\" fill=\"#000\">$label</text>")
            append("</svg>")
        }
    }

    private fun pathData(values: List<Float>, baselineY: Float): String {
        val sb = StringBuilder(values.size * 8)
        sb.append("M0 ").append(fmt(baselineY - values[0] * pxPerAdcCount))
        for (i in 1 until values.size) {
            sb.append(" L").append(fmt(i * pxPerSample))
                .append(' ').append(fmt(baselineY - values[i] * pxPerAdcCount))
        }
        return sb.toString()
    }

    private fun gridDefs(uid: String): String {
        val mm = fmt(PX_PER_MM)
        val mm5 = fmt(PX_PER_MM * 5f)
        return "<defs>" +
            "<pattern id=\"${uid}s\" width=\"$mm\" height=\"$mm\" patternUnits=\"userSpaceOnUse\">" +
            "<path d=\"M$mm 0 L0 0 0 $mm\" fill=\"none\" stroke=\"$GRID_SMALL\" stroke-width=\"0.5\"/>" +
            "</pattern>" +
            "<pattern id=\"$uid\" width=\"$mm5\" height=\"$mm5\" patternUnits=\"userSpaceOnUse\">" +
            "<rect width=\"$mm5\" height=\"$mm5\" fill=\"url(#${uid}s)\"/>" +
            "<path d=\"M$mm5 0 L0 0 0 $mm5\" fill=\"none\" stroke=\"$GRID_LARGE\" stroke-width=\"1\"/>" +
            "</pattern></defs>"
    }

    private fun missingFigure(pathologyId: String, leadToken: String?): String {
        val leadPart = leadToken?.let { " (lead ${escape(it)})" }.orEmpty()
        val id = if (pathologyId.isEmpty()) "(unspecified)" else escape(pathologyId)
        return "<figure class=\"ecg-figure ecg-missing\">" +
            "<figcaption>ECG unavailable: $id$leadPart</figcaption></figure>"
    }

    /** 0.1-px precision, locale-independent (Float.toString always uses '.'). */
    private fun fmt(v: Float): String {
        val r = round(v * 10f) / 10f
        val asLong = r.toLong()
        return if (r == asLong.toFloat()) asLong.toString() else r.toString()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
