package com.example.cardiosimulator.domain

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Bi-directional compiler between raw HTML strings and List<HtmlBlock>.
 */
object HtmlCompiler {

    /**
     * Parses an HTML body string into a list of structured blocks.
     */
    fun parse(html: String): List<HtmlBlock> {
        if (html.isBlank()) return emptyList()
        if (isFullDocument(html)) return listOf(HtmlBlock.Raw(html = html))
        
        val doc = Jsoup.parseBodyFragment(html)
        val body = doc.body()
        val blocks = mutableListOf<HtmlBlock>()

        for (element in body.children()) {
            val elementId = element.id().takeIf { it.isNotBlank() }
            val block = when (element.tagName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val level = element.tagName().substring(1).toInt()
                    if (elementId != null) HtmlBlock.Header(id = elementId, level = level, text = element.text())
                    else HtmlBlock.Header(level = level, text = element.text())
                }
                "p" -> {
                    val content = element.html().trim()
                    // Check if it's a KaTeX display block: $$ expression $$
                    if (content.startsWith("$$") && content.endsWith("$$")) {
                        if (elementId != null) HtmlBlock.KaTeX(id = elementId, expression = content.substring(2, content.length - 2).trim(), displayMode = true)
                        else HtmlBlock.KaTeX(expression = content.substring(2, content.length - 2).trim(), displayMode = true)
                    } else {
                        // Sometimes tables are incorrectly wrapped in <p> tags
                        val nestedTable = element.selectFirst("table")
                        if (nestedTable != null && element.children().size == 1 && element.child(0) == nestedTable) {
                            parseTable(nestedTable)
                        } else {
                            if (elementId != null) HtmlBlock.Paragraph(id = elementId, html = content)
                            else HtmlBlock.Paragraph(html = content)
                        }
                    }
                }
                "img" -> {
                    if (elementId != null) HtmlBlock.Image(id = elementId, src = element.attr("src"), alt = element.attr("alt"))
                    else HtmlBlock.Image(src = element.attr("src"), alt = element.attr("alt"))
                }
                "ul", "ol" -> {
                    val items = element.select("li").joinToString("\n") { it.html() }
                    if (elementId != null) HtmlBlock.HtmlList(id = elementId, items = items, numbered = element.tagName() == "ol")
                    else HtmlBlock.HtmlList(items = items, numbered = element.tagName() == "ol")
                }
                "blockquote" -> {
                    if (elementId != null) HtmlBlock.Quote(id = elementId, html = element.html())
                    else HtmlBlock.Quote(html = element.html())
                }
                "hr" -> {
                    if (elementId != null) HtmlBlock.Divider(id = elementId)
                    else HtmlBlock.Divider()
                }
                "div" -> {
                    when {
                        element.hasClass("lecture-card") -> {
                            val title = element.selectFirst(".lecture-card-title")?.text() ?: ""
                            val bodyHtml = element.selectFirst(".lecture-card-body")?.html() ?: element.html()
                            if (elementId != null) HtmlBlock.Card(id = elementId, title = title, html = bodyHtml)
                            else HtmlBlock.Card(title = title, html = bodyHtml)
                        }
                        element.hasClass("lecture-note") -> {
                            val variant = element.classNames().find { it.startsWith("lecture-note-") }?.removePrefix("lecture-note-") ?: "info"
                            if (elementId != null) HtmlBlock.Note(id = elementId, variant = variant, html = element.html())
                            else HtmlBlock.Note(variant = variant, html = element.html())
                        }
                        element.hasClass("lecture-container") -> {
                            if (elementId != null) HtmlBlock.Container(id = elementId, html = element.html())
                            else HtmlBlock.Container(html = element.html())
                        }
                        else -> {
                            val nestedTable = element.selectFirst("table")
                            if (nestedTable != null && element.text().trim() == nestedTable.text().trim()) {
                                parseTable(nestedTable)
                            } else {
                                if (elementId != null) HtmlBlock.Raw(id = elementId, html = element.outerHtml())
                                else HtmlBlock.Raw(html = element.outerHtml())
                            }
                        }
                    }
                }
                "section" -> {
                    if (element.hasClass("lecture-section")) {
                        val title = element.selectFirst(".lecture-section-title")?.text() ?: ""
                        val temp = element.clone()
                        temp.selectFirst(".lecture-section-title")?.remove()
                        if (elementId != null) HtmlBlock.Section(id = elementId, title = title, html = temp.html())
                        else HtmlBlock.Section(title = title, html = temp.html())
                    } else {
                        if (elementId != null) HtmlBlock.Raw(id = elementId, html = element.outerHtml())
                        else HtmlBlock.Raw(html = element.outerHtml())
                    }
                }
                "figure" -> {
                    if (element.hasClass("lecture-figure")) {
                        val caption = element.selectFirst("figcaption")?.text() ?: ""
                        val bodyHtml = element.selectFirst(".lecture-figure-body")?.html() ?: element.html()
                        if (elementId != null) HtmlBlock.Figure(id = elementId, html = bodyHtml, caption = caption)
                        else HtmlBlock.Figure(html = bodyHtml, caption = caption)
                    } else {
                        val img = element.selectFirst("img")
                        if (img != null) {
                            val figcaption = element.selectFirst("figcaption")
                            val alt = figcaption?.text() ?: img.attr("alt")
                            if (elementId != null) HtmlBlock.Image(id = elementId, src = img.attr("src"), alt = alt)
                            else HtmlBlock.Image(src = img.attr("src"), alt = alt)
                        } else {
                            if (elementId != null) HtmlBlock.Raw(id = elementId, html = element.outerHtml())
                            else HtmlBlock.Raw(html = element.outerHtml())
                        }
                    }
                }
                "ecg" -> {
                    val pathology = element.attr("pathology")
                    val lead = element.attr("lead").takeIf { it.isNotBlank() }
                    val leadsAttr = element.attr("leads")
                    val leads = if (leadsAttr.isNotBlank()) leadsAttr.split(",") else emptyList()
                    val gridScheme = element.attr("gridScheme").takeIf { it.isNotBlank() } ?: "Pink"
                    val count = element.attr("count").toIntOrNull() ?: 1
                    val seriesScheme = element.attr("seriesScheme").takeIf { it.isNotBlank() } ?: "OneColumn"
                    val caption = element.attr("caption")

                    if (elementId != null) HtmlBlock.Ecg(
                        id = elementId,
                        pathology = pathology,
                        lead = lead,
                        leads = leads,
                        gridScheme = gridScheme,
                        count = count,
                        seriesScheme = seriesScheme,
                        caption = caption
                    )
                    else HtmlBlock.Ecg(
                        pathology = pathology,
                        lead = lead,
                        leads = leads,
                        gridScheme = gridScheme,
                        count = count,
                        seriesScheme = seriesScheme,
                        caption = caption
                    )
                }
                "ecgsegment" -> {
                    val pathology = element.attr("pathology")
                    val lead = element.attr("lead")
                    val start = element.attr("start").toFloatOrNull() ?: 0f
                    val duration = element.attr("duration").toFloatOrNull() ?: 2.5f
                    val caption = element.attr("caption").takeIf { it.isNotBlank() }
                    val tips = TipOverlaySerializer.decodeAttribute(element.attr("tips"))

                    if (elementId != null) HtmlBlock.EcgSegment(
                        id = elementId,
                        pathology = pathology,
                        lead = lead,
                        startSec = start,
                        durationSec = duration,
                        caption = caption,
                        tips = tips
                    )
                    else HtmlBlock.EcgSegment(
                        pathology = pathology,
                        lead = lead,
                        startSec = start,
                        durationSec = duration,
                        caption = caption,
                        tips = tips
                    )
                }
                "table" -> parseTable(element)
                // Handle unknown tags as paragraphs
                else -> {
                    val nestedTable = element.selectFirst("table")
                    // If this element contains ONLY a table (ignoring whitespace), treat it as a table block
                    if (nestedTable != null && element.text().trim() == nestedTable.text().trim()) {
                        parseTable(nestedTable)
                    } else {
                        if (elementId != null) HtmlBlock.Raw(id = elementId, html = element.outerHtml())
                        else HtmlBlock.Raw(html = element.outerHtml())
                    }
                }
            }
            blocks.add(block)
        }
        
        // Handle loose text nodes directly in body if any (though unusual for our format)
        return blocks
    }

    private fun parseTable(element: Element): HtmlBlock.Table {
        val elementId = element.id().takeIf { it.isNotBlank() }
        val rows = element.select("tr").map { tr ->
            tr.select("td, th").map { it.html().trim() }
        }
        return if (elementId != null) HtmlBlock.Table(id = elementId, rows = rows)
        else HtmlBlock.Table(rows = rows)
    }

    /**
     * Compiles a list of blocks back into a standards-compliant HTML string.
     */
    fun compile(blocks: List<HtmlBlock>): String = buildString {
        for (block in blocks) {
            when (block) {
                is HtmlBlock.Header -> {
                    append("<h${block.level} id=\"${block.id}\">").append(block.text).append("</h${block.level}>\n")
                }
                is HtmlBlock.Paragraph -> {
                    append("<p id=\"${block.id}\">").append(block.html).append("</p>\n")
                }
                is HtmlBlock.Image -> {
                    if (block.alt.isNotBlank()) {
                        append("<figure id=\"${block.id}\" class=\"image-figure\">\n")
                        append("  <img src=\"").append(block.src).append("\" alt=\"").append(block.alt).append("\">\n")
                        append("  <figcaption>").append(block.alt).append("</figcaption>\n")
                        append("</figure>\n")
                    } else {
                        append("<img id=\"${block.id}\" src=\"").append(block.src).append("\" alt=\"\">\n")
                    }
                }
                is HtmlBlock.KaTeX -> {
                    if (block.displayMode) {
                        append("<p id=\"${block.id}\">$$ ").append(block.expression).append(" $$</p>\n")
                    } else {
                        // Inline KaTeX doesn't easily support a wrapper ID without a span
                        append("<span id=\"${block.id}\">$").append(block.expression).append("$</span>\n")
                    }
                }
                is HtmlBlock.Ecg -> {
                    append(buildEcgTag(block)).append("\n")
                }
                is HtmlBlock.Table -> {
                    append("<table id=\"${block.id}\">\n")
                    for (row in block.rows) {
                        append("  <tr>\n")
                        for (cell in row) {
                            append("    <td>").append(cell).append("</td>\n")
                        }
                        append("  </tr>\n")
                    }
                    append("</table>\n")
                }
                is HtmlBlock.HtmlList -> {
                    append(ensureRootId(HtmlComponents.list(block.items, block.numbered), block.id)).append("\n")
                }
                is HtmlBlock.Quote -> {
                    append(ensureRootId(HtmlComponents.quote(block.html, null), block.id)).append("\n")
                }
                is HtmlBlock.Note -> {
                    append(ensureRootId(HtmlComponents.note(block.variant, block.html), block.id)).append("\n")
                }
                is HtmlBlock.Card -> {
                    append(ensureRootId(HtmlComponents.card(block.title, block.html), block.id)).append("\n")
                }
                is HtmlBlock.Section -> {
                    append(ensureRootId(HtmlComponents.section(block.title, block.html), block.id)).append("\n")
                }
                is HtmlBlock.Figure -> {
                    append(ensureRootId(HtmlComponents.figure(block.html, block.caption), block.id)).append("\n")
                }
                is HtmlBlock.Divider -> {
                    append(ensureRootId(HtmlComponents.divider(), block.id)).append("\n")
                }
                is HtmlBlock.EcgSegment -> {
                    append(buildEcgSegmentTag(block)).append("\n")
                }
                is HtmlBlock.Raw -> {
                    append(ensureRootId(block.html, block.id)).append("\n")
                }
                is HtmlBlock.Container -> {
                    append(ensureRootId(HtmlComponents.container(block.html), block.id)).append("\n")
                }
            }
            append("\n") // Spacer between blocks
        }
    }.trim()

    fun buildEcgTag(block: HtmlBlock.Ecg): String = buildString {
        append("<ecg id=\"${block.id}\" pathology=\"").append(block.pathology).append("\"")
        if (block.lead != null) append(" lead=\"").append(block.lead).append("\"")
        if (block.leads.isNotEmpty()) append(" leads=\"").append(block.leads.joinToString(",")).append("\"")
        append(" gridScheme=\"").append(block.gridScheme).append("\"")
        append(" count=\"").append(block.count).append("\"")
        append(" seriesScheme=\"").append(block.seriesScheme).append("\"")
        append(" caption=\"").append(block.caption).append("\"></ecg>")
    }

    fun buildEcgSegmentTag(block: HtmlBlock.EcgSegment): String = buildString {
        append("<ecgsegment id=\"${block.id}\" pathology=\"").append(block.pathology).append("\"")
        append(" lead=\"").append(block.lead).append("\"")
        append(" start=\"").append(String.format(java.util.Locale.US, "%.3f", block.startSec)).append("\"")
        append(" duration=\"").append(String.format(java.util.Locale.US, "%.3f", block.durationSec)).append("\"")
        if (block.caption != null) append(" caption=\"").append(block.caption).append("\"")
        if (block.tips.isNotEmpty()) {
            append(" tips=\"").append(TipOverlaySerializer.encodeAttribute(block.tips)).append("\"")
        }
        append("></ecgsegment>")
    }

    fun isFullDocument(html: String): Boolean {
        val t = html.trimStart()
        return t.startsWith("<!doctype", true) || t.startsWith("<html", true)
    }

    /**
     * Decomposes a full document into a composable fragment scoped under .lecture-embed.
     */
    fun embedDocument(fullDoc: String): String {
        if (!isFullDocument(fullDoc)) return fullDoc
        
        val doc = Jsoup.parse(fullDoc)
        val styles = doc.select("style")
        val css = styles.joinToString("\n") { it.data() }
        val scopedCss = scopeCss(css)
        
        // Remove scripts and styles from body to avoid side effects in the host doc
        doc.body().select("script, style").remove()
        
        val bodyContent = doc.body().html()
        
        return buildString {
            append("<div class=\"lecture-embed\">\n")
            if (scopedCss.isNotBlank()) {
                append("<style>\n").append(scopedCss).append("\n</style>\n")
            }
            append(bodyContent)
            append("\n</div>")
        }
    }

    /**
     * Simple brace-aware CSS scoper that prefixes rules with .lecture-embed.
     */
    fun scopeCss(css: String): String {
        if (css.isBlank()) return ""
        
        val result = StringBuilder()
        var pos = 0
        val len = css.length
        
        while (pos < len) {
            val brace = css.indexOf('{', pos)
            if (brace == -1) break
            
            val selector = css.substring(pos, brace).trim()
            val nextBrace = findClosingBrace(css, brace)
            if (nextBrace == -1) break
            
            val content = css.substring(brace + 1, nextBrace)
            
            if (selector.startsWith("@media") || selector.startsWith("@supports") || selector.startsWith("@container")) {
                result.append(selector).append(" {\n")
                result.append(scopeCss(content))
                result.append("\n}\n")
            } else if (selector.startsWith("@keyframes") || selector.startsWith("@font-face")) {
                result.append(selector).append(" {").append(content).append("}\n")
            } else if (selector.isNotBlank()) {
                val scopedSelector = splitSelectors(selector).joinToString(", ") { s ->
                    val ts = s.trim()
                    when {
                        ts == "html" || ts == "body" || ts == ":root" -> ".lecture-embed"
                        ts == "*" -> ".lecture-embed *"
                        ts.startsWith("@") -> ts
                        else -> ".lecture-embed $ts"
                    }
                }
                // Drop viewport-height to avoid reserving full screen in embed
                val filteredContent = content.replace(Regex("""\b(min-)?height\s*:\s*[^;]*vh[^;]*""", RegexOption.IGNORE_CASE), "/* $0 */")
                result.append(scopedSelector).append(" {").append(filteredContent).append("}\n")
            }
            
            pos = nextBrace + 1
        }
        
        return result.toString()
    }

    private fun splitSelectors(selector: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var depth = 0
        for (i in selector.indices) {
            when (selector[i]) {
                '(', '[' -> depth++
                ')', ']' -> depth--
                ',' -> if (depth == 0) {
                    parts.add(selector.substring(start, i))
                    start = i + 1
                }
            }
        }
        parts.add(selector.substring(start))
        return parts
    }

    private fun findClosingBrace(text: String, openBracePos: Int): Int {
        var depth = 0
        for (i in openBracePos until text.length) {
            if (text[i] == '{') depth++
            else if (text[i] == '}') {
                depth--
                if (depth == 0) return i
            }
        }
        return -1
    }

    private fun ensureRootId(html: String, id: String): String {
        if (isFullDocument(html)) return html
        val open = Regex("""<\s*[A-Za-z][\w:-]*""").find(html) ?: return html
        val gt = html.indexOf('>', open.range.first); if (gt < 0) return html
        if (Regex("""\sid\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(html.substring(open.range.first, gt))) return html
        return StringBuilder(html).insert(open.range.last + 1, " id=\"$id\"").toString()
    }
}
