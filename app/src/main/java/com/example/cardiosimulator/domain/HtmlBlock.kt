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
        val text: String,
    ) : HtmlBlock

    data class Paragraph(
        override val id: String = UUID.randomUUID().toString(),
        val html: String,
    ) : HtmlBlock

    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        val src: String,
        val alt: String,
    ) : HtmlBlock

    data class KaTeX(
        override val id: String = UUID.randomUUID().toString(),
        val expression: String,
        val displayMode: Boolean,
    ) : HtmlBlock

    data class Ecg(
        override val id: String = UUID.randomUUID().toString(),
        val pathology: String,
        val lead: String? = null,
        val leads: List<String> = emptyList(),
        val gridScheme: String = "Pink",
        val count: Int = 1,
        val seriesScheme: String = "OneColumn",
        val caption: String,
    ) : HtmlBlock

    data class Table(
        override val id: String = UUID.randomUUID().toString(),
        val rows: List<List<String>> = listOf(listOf("")),
    ) : HtmlBlock

    data class HtmlList(
        override val id: String = UUID.randomUUID().toString(),
        val items: String,
        val numbered: Boolean,
    ) : HtmlBlock

    data class Quote(
        override val id: String = UUID.randomUUID().toString(),
        val html: String,
    ) : HtmlBlock

    data class Note(
        override val id: String = UUID.randomUUID().toString(),
        val variant: String,
        val html: String,
    ) : HtmlBlock

    data class Card(
        override val id: String = UUID.randomUUID().toString(),
        val title: String,
        val html: String,
    ) : HtmlBlock

    data class Section(
        override val id: String = UUID.randomUUID().toString(),
        val title: String,
        val html: String,
    ) : HtmlBlock

    data class Figure(
        override val id: String = UUID.randomUUID().toString(),
        val html: String,
        val caption: String,
    ) : HtmlBlock

    data class Divider(
        override val id: String = UUID.randomUUID().toString(),
    ) : HtmlBlock

    data class Raw(
        override val id: String = java.util.UUID.randomUUID().toString().replace("-", ""),
        val html: String,
    ) : HtmlBlock

    data class Container(
        override val id: String = java.util.UUID.randomUUID().toString().replace("-", ""),
        val html: String,
    ) : HtmlBlock

    data class EcgSegment(
        override val id: String = UUID.randomUUID().toString().replace("-", ""),
        val pathology: String,
        val lead: String,
        val startSec: Float = 0f,
        val durationSec: Float = 2.5f,
        val caption: String? = null,
        val tips: List<TipOverlay> = emptyList(),
    ) : HtmlBlock
}
