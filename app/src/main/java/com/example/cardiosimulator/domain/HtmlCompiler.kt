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
                "figure" -> {
                    val img = element.selectFirst("img")
                    if (img != null) {
                        val figcaption = element.selectFirst("figcaption")
                        val alt = figcaption?.text() ?: img.attr("alt")
                        if (elementId != null) HtmlBlock.Image(id = elementId, src = img.attr("src"), alt = alt)
                        else HtmlBlock.Image(src = img.attr("src"), alt = alt)
                    } else {
                        if (elementId != null) HtmlBlock.Paragraph(id = elementId, html = element.outerHtml())
                        else HtmlBlock.Paragraph(html = element.outerHtml())
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
                "table" -> parseTable(element)
                // Handle unknown tags as paragraphs
                else -> {
                    val nestedTable = element.selectFirst("table")
                    // If this element contains ONLY a table (ignoring whitespace), treat it as a table block
                    if (nestedTable != null && element.text().trim() == nestedTable.text().trim()) {
                        parseTable(nestedTable)
                    } else {
                        if (elementId != null) HtmlBlock.Paragraph(id = elementId, html = element.outerHtml())
                        else HtmlBlock.Paragraph(html = element.outerHtml())
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
                    append("<ecg id=\"${block.id}\" pathology=\"").append(block.pathology).append("\"")
                    if (block.lead != null) append(" lead=\"").append(block.lead).append("\"")
                    if (block.leads.isNotEmpty()) append(" leads=\"").append(block.leads.joinToString(",")).append("\"")
                    append(" gridScheme=\"").append(block.gridScheme).append("\"")
                    append(" count=\"").append(block.count).append("\"")
                    append(" seriesScheme=\"").append(block.seriesScheme).append("\"")
                    append(" caption=\"").append(block.caption).append("\"></ecg>\n")
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
            }
            append("\n") // Spacer between blocks
        }
    }.trim()
}
