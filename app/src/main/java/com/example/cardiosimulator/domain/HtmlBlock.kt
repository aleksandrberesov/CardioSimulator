package com.example.cardiosimulator.domain

import java.util.UUID

/**
 * Represents a discrete structural unit of a lecture.
 */
sealed interface HtmlBlock {
    val id: String

    data class Header(
        override val id: String = UUID.randomUUID().toString(),
        val level: Int,
        val text: String
    ) : HtmlBlock

    data class Paragraph(
        override val id: String = UUID.randomUUID().toString(),
        val html: String
    ) : HtmlBlock

    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        val src: String,
        val alt: String
    ) : HtmlBlock

    data class KaTeX(
        override val id: String = UUID.randomUUID().toString(),
        val expression: String,
        val displayMode: Boolean
    ) : HtmlBlock

    data class Ecg(
        override val id: String = UUID.randomUUID().toString(),
        val pathology: String,
        val lead: String?,
        val caption: String
    ) : HtmlBlock

    data class RawHtml(
        override val id: String = UUID.randomUUID().toString(),
        val html: String
    ) : HtmlBlock
}
