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
            val block = when (element.tagName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val level = element.tagName().substring(1).toInt()
                    HtmlBlock.Header(level = level, text = element.text())
                }
                "p" -> {
                    val content = element.html().trim()
                    // Check if it's a KaTeX display block: $$ expression $$
                    if (content.startsWith("$$") && content.endsWith("$$")) {
                        HtmlBlock.KaTeX(
                            expression = content.substring(2, content.length - 2).trim(),
                            displayMode = true
                        )
                    } else {
                        HtmlBlock.Paragraph(html = content)
                    }
                }
                "img" -> {
                    HtmlBlock.Image(
                        src = element.attr("src"),
                        alt = element.attr("alt")
                    )
                }
                "ecg" -> {
                    HtmlBlock.Ecg(
                        pathology = element.attr("pathology"),
                        lead = element.attr("lead").takeIf { it.isNotBlank() },
                        caption = element.attr("caption")
                    )
                }
                // Handle raw HTML for anything else (divs, tables, etc.)
                else -> {
                    HtmlBlock.RawHtml(html = element.outerHtml())
                }
            }
            blocks.add(block)
        }
        
        // Handle loose text nodes directly in body if any (though unusual for our format)
        return blocks
    }

    /**
     * Compiles a list of blocks back into a standards-compliant HTML string.
     */
    fun compile(blocks: List<HtmlBlock>): String = buildString {
        for (block in blocks) {
            when (block) {
                is HtmlBlock.Header -> {
                    append("<h${block.level}>").append(block.text).append("</h${block.level}>\n")
                }
                is HtmlBlock.Paragraph -> {
                    append("<p>").append(block.html).append("</p>\n")
                }
                is HtmlBlock.Image -> {
                    append("<img src=\"").append(block.src).append("\" alt=\"").append(block.alt).append("\">\n")
                }
                is HtmlBlock.KaTeX -> {
                    if (block.displayMode) {
                        append("<p>$$ ").append(block.expression).append(" $$</p>\n")
                    } else {
                        append("$").append(block.expression).append("$\n")
                    }
                }
                is HtmlBlock.Ecg -> {
                    append("<ecg pathology=\"").append(block.pathology).append("\"")
                    if (block.lead != null) append(" lead=\"").append(block.lead).append("\"")
                    append(" caption=\"").append(block.caption).append("\"></ecg>\n")
                }
                is HtmlBlock.RawHtml -> {
                    append(block.html).append("\n")
                }
            }
            append("\n") // Spacer between blocks
        }
    }.trim()
}
