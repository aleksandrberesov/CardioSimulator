package com.example.cardiosimulator.domain

object HtmlComponents {
    fun list(items: String, numbered: Boolean): String {
        val tag = if (numbered) "ol" else "ul"
        val listItems = items.lines().filter { it.isNotBlank() }.joinToString("\n") { "  <li>$it</li>" }
        return "<$tag class=\"lecture-list\">\n$listItems\n</$tag>"
    }

    fun card(title: String?, body: String): String = buildString {
        append("<div class=\"lecture-card\">\n")
        if (!title.isNullOrBlank()) {
            append("  <div class=\"lecture-card-title\">").append(title).append("</div>\n")
        }
        append("  <div class=\"lecture-card-body\">\n").append(body).append("\n  </div>\n")
        append("</div>")
    }

    fun section(title: String?, body: String): String = buildString {
        append("<section class=\"lecture-section\">\n")
        if (!title.isNullOrBlank()) {
            append("  <h3 class=\"lecture-section-title\">").append(title).append("</h3>\n")
        }
        append(body)
        append("\n</section>")
    }

    fun note(variant: String, body: String): String {
        val variantClass = when (variant.lowercase()) {
            "tip", "warning", "important" -> "lecture-note-$variant"
            else -> "lecture-note-info"
        }
        return "<div class=\"lecture-note $variantClass\">\n$body\n</div>"
    }

    fun quote(body: String, cite: String?): String = buildString {
        append("<blockquote class=\"lecture-quote\">\n").append(body)
        if (!cite.isNullOrBlank()) {
            append("\n  <cite>").append(cite).append("</cite>")
        }
        append("\n</blockquote>")
    }

    fun figure(body: String, caption: String?): String = buildString {
        append("<figure class=\"lecture-figure\">\n")
        append("  <div class=\"lecture-figure-body\">\n").append(body).append("\n  </div>\n")
        if (!caption.isNullOrBlank()) {
            append("  <figcaption>").append(caption).append("</figcaption>\n")
        }
        append("</figure>")
    }

    fun divider(): String = "<hr class=\"lecture-divider\">"

    fun container(body: String): String = "<div class=\"lecture-container\">\n$body\n</div>"

    const val Css = """
.lecture-list { padding-left: 24px; margin: 12px 0; }
.lecture-card { border: 1px solid #ddd; border-radius: 8px; margin: 16px 0; overflow: hidden; background: #fff; }
.lecture-card-title { background: #f5f5f5; padding: 8px 16px; font-weight: bold; border-bottom: 1px solid #ddd; }
.lecture-card-body { padding: 16px; }
.lecture-section { margin: 24px 0; }
.lecture-section-title { color: #2196F3; border-bottom: 2px solid #2196F3; padding-bottom: 4px; margin-bottom: 12px; }
.lecture-note { padding: 12px 16px; margin: 16px 0; border-left: 4px solid #2196F3; background: #e3f2fd; border-radius: 4px; }
.lecture-note-tip { border-left-color: #4CAF50; background: #e8f5e9; }
.lecture-note-warning { border-left-color: #FF9800; background: #fff3e0; }
.lecture-note-important { border-left-color: #F44336; background: #ffebee; }
.lecture-quote { font-style: italic; border-left: 4px solid #ccc; padding-left: 16px; margin: 16px 0; color: #555; }
.lecture-quote cite { display: block; font-style: normal; font-weight: bold; margin-top: 8px; font-size: 0.9em; text-align: right; }
.lecture-figure { margin: 16px 0; text-align: center; }
.lecture-figure-body { display: inline-block; width: 100%; }
.lecture-figure figcaption { font-size: 0.9em; color: #666; margin-top: 8px; font-style: italic; }
.lecture-divider { border: 0; border-top: 1px solid #eee; margin: 24px 0; }
"""
}
