package com.example.cardiosimulator.domain

/**
 * Course bundle domain types. Mirrors the on-disk format documented in
 * `docs/course-format.md`:
 *
 * - One [Course] per `<course-id>/` directory.
 * - One [Lecture] per `<lecture-id>.<lang>.html` file inside `lectures/`.
 * - A lecture body is HTML rendered in a single `WebView`; it is not
 *   decomposed into structured blocks. ECG embeds (`<ecg>`) and editable
 *   quiz tables are handled by the renderer, not the data model.
 *
 * Parsing lives in [CourseParser]; the parser keeps the raw HTML body
 * around on [Lecture.rawHtml] so the constructor can write back the
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
    /**
     * "Тема" (topic) entries. Order matches the display order.
     */
    val topics: List<TopicEntry> = emptyList(),
)

/** A "Тема" (topic): a named grouping of lectures. Its [id] is referenced by member
 *  lectures' [LectureEntry.topic]; can exist with no lectures yet. */
data class TopicEntry(val id: String, val titleEn: String, val nameRu: String?)

/** One row of [Course.lectures] — the lecture index inside a course. */
data class LectureEntry(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    /** Owning topic id (null = ungrouped). */
    val topic: String? = null,
)

/** Parsed `<lecture-id>.<lang>.html`. */
data class Lecture(
    val id: String,
    val courseId: String,
    val language: String,
    val frontMatter: LectureFrontMatter,
    /**
     * The original HTML body (everything after the closing `---` of the
     * front matter). Retained verbatim so the constructor can write back
     * source exactly as the author typed it, and so the renderer can hand
     * it to the `WebView` unchanged (apart from the `<ecg>` → SVG rewrite).
     */
    val rawHtml: String,
) {
    /**
     * Whether this lecture is a standalone HTML document (layout: standalone).
     * If true, [rawHtml] contains the full document (<!DOCTYPE html> etc.),
     * and [LectureWebView] serves it as-is (with KaTeX/SVG injection).
     */
    val isStandalone: Boolean get() = frontMatter.extras["layout"] == "standalone"
}

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
