package com.example.cardiosimulator.domain

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Helper for navigating and performing surgical edits on a nested HTML DOM.
 */
object HtmlStructure {
    enum class Kind { Container, Heading, Text, Math, Image, Ecg, Table, List, Diagram, Other }

    data class Node(
        val tag: String,
        val id: String?,
        val className: String?,
        val kind: Kind,
        val label: String,
        val preview: String?,
        val path: List<Int>,
        val children: List<Node>,
    )

    private val BlockTags = setOf(
        "div", "section", "article", "aside", "header", "footer", "main", "nav",
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "ul", "ol", "li", "table", "figure", "blockquote", "hr", "ecg", "ecgsegment"
    )

    fun outline(html: String): List<Node> {
        if (html.isBlank()) return emptyList()
        val doc = parseAny(html)
        val roots = doc.body().children()
        return roots.mapIndexed { index, element -> buildNode(element, listOf(index)) }
    }

    private fun buildNode(element: Element, path: List<Int>): Node {
        val kind = classify(element)
        val label = getLabel(element, kind)
        val preview = getPreview(element, kind)

        val children = if (kind == Kind.Container) {
            element.children().mapIndexedNotNull { index, child ->
                if (isBlockLevel(child)) buildNode(child, path + index) else null
            }
        } else {
            emptyList()
        }

        return Node(
            tag = element.tagName(),
            id = element.id().takeIf { it.isNotBlank() },
            className = element.className().takeIf { it.isNotBlank() },
            kind = kind,
            label = label,
            preview = preview,
            path = path,
            children = children
        )
    }

    private fun isBlockLevel(element: Element): Boolean {
        return element.tagName().lowercase() in BlockTags
    }

    private fun classify(element: Element): Kind {
        val tag = element.tagName().lowercase()
        return when {
            tag.startsWith("h") && tag.length == 2 && tag[1].isDigit() -> Kind.Heading
            tag == "p" -> {
                val content = element.html().trim()
                if (content.startsWith("$$") && content.endsWith("$$")) Kind.Math else Kind.Text
            }
            tag == "img" || tag == "figure" -> Kind.Image
            tag == "ecg" || tag == "ecgsegment" -> Kind.Ecg
            tag == "table" -> Kind.Table
            tag == "ul" || tag == "ol" -> Kind.List
            tag == "svg" -> Kind.Diagram
            tag in setOf("div", "section", "article", "aside", "header", "footer", "main", "nav") -> {
                if (element.children().any { isBlockLevel(it) }) Kind.Container
                else if (element.hasText()) Kind.Text
                else Kind.Other
            }
            else -> Kind.Other
        }
    }

    private fun getLabel(element: Element, kind: Kind): String {
        return when (kind) {
            Kind.Heading -> "Heading ${element.tagName().substring(1)}"
            Kind.Container -> {
                val cls = element.className().lowercase()
                when {
                    cls.contains("card") -> "Card"
                    cls.contains("section") -> "Section"
                    cls.contains("figure") -> "Figure"
                    cls.contains("header") -> "Header"
                    else -> "Group"
                }
            }
            Kind.Text -> {
                val cls = element.className().lowercase()
                when {
                    cls.contains("title") -> "Title"
                    cls.contains("subtitle") -> "Subtitle"
                    cls.contains("caption") -> "Caption"
                    cls.contains("note") -> "Note"
                    cls.contains("badge") -> "Badge"
                    cls.contains("breadcrumb") -> "Breadcrumb"
                    else -> "Text"
                }
            }
            Kind.Table -> {
                val rows = element.select("tr").size
                val cols = element.select("tr").firstOrNull()?.select("td, th")?.size ?: 0
                "Table ${rows}×${cols}"
            }
            Kind.List -> {
                val items = element.select("li").size
                "List · $items items"
            }
            Kind.Diagram -> "Diagram (SVG)"
            Kind.Math -> "Math"
            Kind.Image -> "Image"
            Kind.Ecg -> if (element.tagName() == "ecgsegment") "ECG segment" else "ECG"
            Kind.Other -> "Element"
        }
    }

    private fun getPreview(element: Element, kind: Kind): String? {
        return when (kind) {
            Kind.Heading, Kind.Text, Kind.Math -> element.text().take(50).let { if (it.length == 50) "$it..." else it }
            Kind.Image -> {
                val img = if (element.tagName() == "img") element else element.selectFirst("img")
                img?.attr("alt")?.takeIf { it.isNotBlank() } ?: img?.attr("src")
            }
            Kind.Ecg -> element.attr("pathology")
            Kind.Diagram -> {
                val w = element.attr("width")
                val h = element.attr("height")
                if (w.isNotBlank() && h.isNotBlank()) "${w}×${h}" else element.attr("viewBox").takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    fun replaceElement(html: String, path: List<Int>, replacement: String): String {
        val doc = parseAny(html)
        val target = navigate(doc.body().children(), path) ?: return html
        target.after(replacement)
        target.remove()
        return serialize(doc, html)
    }

    fun insertAdjacent(html: String, path: List<Int>, fragment: String, after: Boolean): String {
        val doc = parseAny(html)
        val target = navigate(doc.body().children(), path) ?: return html
        if (after) target.after(fragment) else target.before(fragment)
        return serialize(doc, html)
    }

    fun appendChild(html: String, path: List<Int>, fragment: String): String {
        val doc = parseAny(html)
        val target = navigate(doc.body().children(), path) ?: return html
        target.append(fragment)
        return serialize(doc, html)
    }

    fun removeElement(html: String, path: List<Int>): String {
        val doc = parseAny(html)
        val target = navigate(doc.body().children(), path) ?: return html
        target.remove()
        return serialize(doc, html)
    }

    fun getOuterHtml(html: String, path: List<Int>): String {
        val doc = parseAny(html)
        val target = navigate(doc.body().children(), path) ?: return ""
        return target.outerHtml()
    }

    fun appendToRoot(html: String, fragment: String): String {
        val doc = parseAny(html)
        doc.body().append(fragment)
        return serialize(doc, html)
    }

    private fun navigate(children: List<Element>, path: List<Int>): Element? {
        if (path.isEmpty()) return null
        var current: Element? = children.getOrNull(path[0]) ?: return null
        for (i in 1 until path.size) {
            current = current?.children()?.getOrNull(path[i]) ?: return null
        }
        return current
    }

    private fun parseAny(html: String): Document {
        val doc = if (HtmlCompiler.isFullDocument(html)) Jsoup.parse(html) else Jsoup.parseBodyFragment(html)
        doc.outputSettings().prettyPrint(false)
        return doc
    }

    private fun serialize(doc: Document, original: String): String =
        if (HtmlCompiler.isFullDocument(original)) doc.outerHtml() else doc.body().html()
}
