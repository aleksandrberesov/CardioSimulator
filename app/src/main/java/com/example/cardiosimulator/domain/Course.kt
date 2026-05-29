package com.example.cardiosimulator.domain

/**
 * Course bundle domain types. Mirrors the on-disk format documented in
 * `docs/course-format.md`:
 *
 * - One [Course] per `<course-id>/` directory.
 * - One [Lecture] per `<lecture-id>.<lang>.md` file inside `lectures/`.
 * - Rich-content lectures decompose into a flat sequence of [CourseBlock]s
 *   so the renderer can interleave Markdown text with native Compose
 *   overlays for ECG embeds and editable tables.
 *
 * Parsing lives in [CourseParser]; the parser keeps the raw Markdown body
 * around on [Lecture.rawMarkdown] so the constructor can write back the
 * author-pristine source rather than a round-tripped one.
 */

/** Top-level `manifest.txt` model. */
data class CourseManifest(
    val version: String,
    val entries: List<CourseEntry>,
) {
    companion object {
        const val SUPPORTED_VERSION: String = "1.0"
    }
}

/** One row of [CourseManifest.entries]. */
data class CourseEntry(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    val lecturesCount: Int,
    /**
     * `PathologyEntry.id`s this course covers. Mirrors [Course.pathologies]
     * up into the manifest row so consumers (e.g. the rhythm selector) can
     * scope the pathology list to a course without reading every
     * `course.txt`.
     */
    val pathologies: List<String> = emptyList(),
)

/** Parsed `<course-id>/course.txt`. */
data class Course(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    val authors: String?,
    val languages: List<String>,
    val lectures: List<LectureEntry>,
    /**
     * Ordered, de-duplicated list of `PathologyEntry.id`s this course
     * covers. Authoritative source for the course → pathologies mapping;
     * typically the union of the pathologies embedded across the course's
     * lectures (see `docs/course-format.md` §3).
     */
    val pathologies: List<String> = emptyList(),
)

/** One row of [Course.lectures] — the lecture index inside a course. */
data class LectureEntry(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
)

/** Parsed `<lecture-id>.<lang>.md`. */
data class Lecture(
    val id: String,
    val courseId: String,
    val language: String,
    val frontMatter: LectureFrontMatter,
    val blocks: List<CourseBlock>,
    /**
     * The original Markdown body (everything after the closing `---` of
     * the front matter). Retained verbatim so the constructor can write
     * back source exactly as the author typed it.
     */
    val rawMarkdown: String,
)

/**
 * Front-matter `key: value` pairs. Known keys are surfaced as fields;
 * unknown keys live in [extras] and are re-emitted on save so foreign
 * metadata round-trips losslessly.
 */
data class LectureFrontMatter(
    val id: String,
    val order: Int = 0,
    val title: String = "",
    val schemaVersion: Int = 1,
    val extras: Map<String, String> = emptyMap(),
)

/**
 * One renderable segment of a lecture body.
 *
 * The parser only carves out the segments that need structured
 * handling at the data layer: the custom fenced blocks (`ecg`, `table`)
 * and everything else as opaque [Markdown] runs. The renderer turns
 * Markdown runs into Compose content via a Markdown library (Phase 2);
 * KaTeX `$...$` math, GFM tables, images, and links stay inside the
 * Markdown runs and are not extracted here.
 */
sealed class CourseBlock {
    data class Markdown(val text: String) : CourseBlock()

    data class EcgEmbed(
        val pathologyId: String,
        val lead: Lead?,
        val caption: String?,
    ) : CourseBlock()

    data class EditableTable(
        val id: String,
        val editable: Boolean,
        /** GFM table source — cell parsing is deferred to the renderer. */
        val raw: String,
    ) : CourseBlock()
}
