package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Lead
import kotlin.math.abs
import kotlin.math.ceil
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

    // Grid schemes
    private data class GridColors(val bg: String, val small: String, val large: String)
    private val gridSchemes = mapOf(
        "Pink" to GridColors("#FFF5F5", "#FDE4E4", "#F9BDBD"),
        "BlueGray" to GridColors("#F5F7FA", "#E4E9F2", "#C0CCDA"),
        "Blank" to GridColors("#FFFFFF", "#FFFFFF", "#FFFFFF")
    )
    private const val TRACE_COLOR = "#111111"
    private const val LABEL_AREA_WIDTH = 32f
    private const val CAL_AREA_WIDTH = 80f

    private val ecgTag = Regex("<ecg\\b((?:[^>\"]|\"[^\"]*\")*?)\\s*/?>(?:\\s*</ecg>)?", RegexOption.IGNORE_CASE)
    private val attr = Regex("([\\w-]+)\\s*=\\s*\"([^\"]*)\"")

    /**
     * Replaces every `<ecg …>` element in [html] with an inline-SVG figure.
     * [resolve] maps a `(pathologyId, requestedLeads)` to the traces to draw.
     */
    fun substituteEcgTags(
        html: String,
        showMonitorButton: Boolean = false,
        resolve: (pathologyId: String, leads: List<Lead>) -> List<EcgTrace>,
    ): String {
        var figureIndex = 0
        return ecgTag.replace(html) { match ->
            val attrs = attr.findAll(match.groupValues[1])
                .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val pathologyId = attrs["pathology"].orEmpty().trim()
            val leadToken = attrs["lead"]?.takeIf { it.isNotBlank() }
            val leadsAttr = attrs["leads"]?.takeIf { it.isNotBlank() }
            val gridScheme = attrs["gridscheme"] ?: "Pink"
            val count = attrs["count"]?.toIntOrNull() ?: 1
            val seriesScheme = attrs["seriesscheme"] ?: "OneColumn"
            val caption = attrs["caption"]?.takeIf { it.isNotBlank() }

            val requestedLeads = if (leadsAttr != null) {
                leadsAttr.split(",").mapNotNull { Lead.fromToken(it) }
            } else if (leadToken != null) {
                listOfNotNull(Lead.fromToken(leadToken))
            } else {
                emptyList()
            }

            val traces = if (pathologyId.isEmpty()) emptyList() else resolve(pathologyId, requestedLeads)
            if (traces.isEmpty()) missingFigure(pathologyId, leadToken ?: leadsAttr)
            else figureHtml(
                traces = traces,
                caption = caption,
                figureIndex = figureIndex++,
                gridScheme = gridScheme,
                seriesScheme = seriesScheme,
                count = count,
                showMonitorButton = showMonitorButton
            )
        }
    }

    /** Builds a `<figure>` with a single `<svg>` containing all traces. */
    fun figureHtml(
        traces: List<EcgTrace>,
        caption: String?,
        figureIndex: Int = 0,
        gridScheme: String = "Pink",
        seriesScheme: String = "OneColumn",
        count: Int = 1,
        showMonitorButton: Boolean = false
    ): String {
        val colors = gridSchemes[gridScheme] ?: gridSchemes["Pink"]!!
        val visibleTraces = traces.take(count)
        if (visibleTraces.isEmpty()) return ""

        val maxCols = when (seriesScheme) {
            "TwoColumn" -> 2
            "ThreeByFour" -> 4
            "Grid" -> 4
            else -> 1
        }
        val rows = ceil(visibleTraces.size.toFloat() / maxCols).toInt()
        val cols = if (rows > 0) ceil(visibleTraces.size.toFloat() / rows).toInt() else 1

        val leadHeight = 20f * PX_PER_MM // 20mm height per lead
        val maxTraceSamples = visibleTraces.maxOfOrNull { it.points.values.size } ?: 0
        val traceWidth = max(1f, (maxTraceSamples - 1) * pxPerSample)
        val leadWidth = traceWidth + CAL_AREA_WIDTH
        
        val totalWidth = leadWidth * cols
        val totalHeight = leadHeight * rows
        val uid = "ecg$figureIndex"

        val svg = buildString {
            val label = if (visibleTraces.size == 1) "ECG lead ${visibleTraces[0].lead.name}" else "ECG Monitor"
            append("<svg class=\"ecg-monitor\" xmlns=\"http://www.w3.org/2000/svg\" ")
            append("viewBox=\"0 0 ${fmt(totalWidth)} ${fmt(totalHeight)}\" ")
            append("width=\"${fmt(totalWidth)}\" height=\"${fmt(totalHeight)}\" ")
            append("preserveAspectRatio=\"xMidYMid meet\" role=\"img\" aria-label=\"$label\">")
            append(gridDefs(uid, colors))
            append("<rect width=\"${fmt(totalWidth)}\" height=\"${fmt(totalHeight)}\" fill=\"${colors.bg}\"/>")
            append("<rect width=\"${fmt(totalWidth)}\" height=\"${fmt(totalHeight)}\" fill=\"url(#$uid)\"/>")
            
            visibleTraces.forEachIndexed { i, trace ->
                val col = i / rows
                val row = i % rows
                val x = col * leadWidth
                val y = row * leadHeight
                val baselineY = leadHeight / 2f
                append("<g transform=\"translate(${fmt(x)}, ${fmt(y)})\">")
                
                // Calibration pulse
                append(calibrationPulsePath(baselineY))

                // Trace
                append("<g transform=\"translate($CAL_AREA_WIDTH, 0)\">")
                append(tracePath(trace, baselineY))
                append("</g>")

                // Lead Label
                append("<text x=\"${fmt(LABEL_AREA_WIDTH / 2f)}\" y=\"${fmt(baselineY)}\" ")
                append("font-family=\"serif\" font-weight=\"bold\" font-size=\"14\" ")
                append("fill=\"$TRACE_COLOR\" text-anchor=\"middle\" dominant-baseline=\"central\">")
                append("${trace.lead.name}</text>")

                append("</g>")
            }
            append("</svg>")
        }

        val cap = caption?.let { "\n  <figcaption>${escape(it)}</figcaption>" }.orEmpty()
        val monitorBtn = if (showMonitorButton) {
            "\n  <button class=\"monitor-btn\" onclick=\"if(window.Android)Android.onMonitor()\">Monitor</button>"
        } else ""
        return "<figure class=\"ecg-figure\">\n$svg$monitorBtn$cap\n</figure>"
    }

    private fun tracePath(trace: EcgTrace, baselineY: Float): String {
        val values = trace.points.values
        if (values.size < 2) return ""
        val d = pathData(values, baselineY)
        return "<path d=\"$d\" fill=\"none\" stroke=\"$TRACE_COLOR\" stroke-width=\"1.4\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>"
    }

    private fun calibrationPulsePath(baselineY: Float): String {
        val pulseHeight = 1f * pxPerMv
        val pulseWidth = 0.2f * pxPerSec
        val startX = LABEL_AREA_WIDTH + 8f
        val wingWidth = 4f

        val d = "M${fmt(startX)} ${fmt(baselineY)} " +
                "h${fmt(wingWidth)} " +
                "v${fmt(-pulseHeight)} " +
                "h${fmt(pulseWidth)} " +
                "v${fmt(pulseHeight)} " +
                "h${fmt(wingWidth)}"

        return "<path d=\"$d\" fill=\"none\" stroke=\"$TRACE_COLOR\" stroke-width=\"1.4\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>"
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

    private fun gridDefs(uid: String, colors: GridColors): String {
        val mm = fmt(PX_PER_MM)
        val mm5 = fmt(PX_PER_MM * 5f)
        return "<defs>" +
            "<pattern id=\"${uid}s\" width=\"$mm\" height=\"$mm\" patternUnits=\"userSpaceOnUse\">" +
            "<path d=\"M$mm 0 L0 0 0 $mm\" fill=\"none\" stroke=\"${colors.small}\" stroke-width=\"0.5\"/>" +
            "</pattern>" +
            "<pattern id=\"$uid\" width=\"$mm5\" height=\"$mm5\" patternUnits=\"userSpaceOnUse\">" +
            "<rect width=\"$mm5\" height=\"$mm5\" fill=\"url(#${uid}s)\"/>" +
            "<path d=\"M$mm5 0 L0 0 0 $mm5\" fill=\"none\" stroke=\"${colors.large}\" stroke-width=\"1\"/>" +
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
