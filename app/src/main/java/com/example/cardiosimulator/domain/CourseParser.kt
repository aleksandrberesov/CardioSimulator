package com.example.cardiosimulator.domain

/**
 * Pure-Kotlin parser + serializer for the course bundle format.
 * Grammar is documented in `docs/course-format.md`. All files are UTF-8
 * with LF endings.
 *
 * Three formats live here:
 *
 * 1. `manifest.txt` — key:value header + semicolon-delimited course rows
 *    (identical shape to [PathologyParser.parseManifest]).
 * 2. `<course-id>/course.txt` — key:value header + semicolon-delimited
 *    lecture rows.
 * 3. `<lecture-id>.<lang>.html` — YAML-lite front matter delimited by
 *    `---` lines, then an HTML body kept verbatim as [Lecture.rawHtml].
 *    The body is rendered in a single `WebView`; it is not decomposed
 *    into structured blocks here.
 */
object CourseParser {

    class FormatException(message: String) : RuntimeException(message)

    // ─── manifest.txt ───────────────────────────────────────────────────

    fun parseManifest(text: String): CourseManifest {
        val (header, body) = splitHeader(text)
        val version = header["version"]
            ?: throw FormatException("manifest: missing 'version'")
        if (version != CourseManifest.SUPPORTED_VERSION) {
            throw FormatException(
                "manifest: unsupported version '$version' (this build needs " +
                    "'${CourseManifest.SUPPORTED_VERSION}')"
            )
        }
        val entries = body.mapNotNull { line ->
            val fields = parseSemicolonFields(line)
            val id = fields["course"] ?: return@mapNotNull null
            CourseEntry(
                id = id,
                titleEn = fields["title"].orEmpty(),
                nameRu = fields["name"],
                lecturesCount = fields["lectures"]?.toIntOrNull() ?: 0,
                pathologies = parseCsv(fields["pathologies"]),
            )
        }
        return CourseManifest(version = version, entries = entries)
    }

    fun serializeManifest(manifest: CourseManifest): String = buildString {
        append("version:").append(manifest.version).append('\n')
        append("courses:").append(manifest.entries.size).append('\n')
        append('\n')
        for (e in manifest.entries) {
            append("course:").append(e.id)
                .append(";lectures:").append(e.lecturesCount)
                .append(";title:").append(e.titleEn)
            if (!e.nameRu.isNullOrBlank()) append(";name:").append(e.nameRu)
            if (e.pathologies.isNotEmpty()) {
                append(";pathologies:").append(e.pathologies.joinToString(","))
            }
            append('\n')
        }
    }

    // ─── <course-id>/course.txt ─────────────────────────────────────────

    fun parseCourse(text: String): Course {
        val (header, body) = splitHeader(text)
        val id = header["course"]
            ?: throw FormatException("course: missing 'course'")
        val languages = header["language"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val pathologies = parseCsv(header["pathologies"])
        val lectures = body.mapNotNull { line ->
            val fields = parseSemicolonFields(line)
            val lectureId = fields["lecture"] ?: return@mapNotNull null
            LectureEntry(
                id = lectureId,
                titleEn = fields["title"].orEmpty(),
                nameRu = fields["name"],
            )
        }
        return Course(
            id = id,
            titleEn = header["title"].orEmpty(),
            nameRu = header["name"],
            authors = header["authors"],
            languages = languages,
            lectures = lectures,
            pathologies = pathologies,
        )
    }

    fun serializeCourse(course: Course): String = buildString {
        append("course:").append(course.id).append('\n')
        append("title:").append(course.titleEn).append('\n')
        if (!course.nameRu.isNullOrBlank()) append("name:").append(course.nameRu).append('\n')
        if (!course.authors.isNullOrBlank()) append("authors:").append(course.authors).append('\n')
        if (course.languages.isNotEmpty()) {
            append("language:").append(course.languages.joinToString(",")).append('\n')
        }
        if (course.pathologies.isNotEmpty()) {
            append("pathologies:").append(course.pathologies.joinToString(",")).append('\n')
        }
        append('\n')
        for (l in course.lectures) {
            append("lecture:").append(l.id)
                .append(";title:").append(l.titleEn)
            if (!l.nameRu.isNullOrBlank()) append(";name:").append(l.nameRu)
            append('\n')
        }
    }

    // ─── <lecture-id>.<lang>.html ───────────────────────────────────────

    /**
     * Parses a lecture file. [courseId] and [language] are injected by
     * the source layer (they're encoded in the file's directory and
     * filename, not the content). The HTML body after the front matter is
     * kept verbatim on [Lecture.rawHtml]; it is not decomposed here.
     */
    fun parseLecture(text: String, courseId: String, language: String): Lecture {
        val (fmText, body) = splitFrontMatter(text)
        val fm = parseFrontMatter(fmText)
        return Lecture(
            id = fm.id,
            courseId = courseId,
            language = language,
            frontMatter = fm,
            rawHtml = body,
        )
    }

    /**
     * Serializes a lecture back to the on-disk format. Re-emits the
     * front matter (preserving unknown [LectureFrontMatter.extras] keys)
     * followed by [Lecture.rawHtml] verbatim.
     */
    fun serializeLecture(lecture: Lecture): String = buildString {
        append("---\n")
        val fm = lecture.frontMatter
        append("id: ").append(fm.id).append('\n')
        if (fm.order != 0) append("order: ").append(fm.order).append('\n')
        if (fm.title.isNotEmpty()) append("title: ").append(fm.title).append('\n')
        append("schemaVersion: ").append(fm.schemaVersion).append('\n')
        for ((k, v) in fm.extras) {
            append(k).append(": ").append(v).append('\n')
        }
        append("---\n")
        append(lecture.rawHtml)
    }

    // ─── front matter ───────────────────────────────────────────────────

    /**
     * Splits a lecture file into its `---`-delimited front matter and
     * the HTML body. If no front matter is present, returns an empty
     * header and the whole text as the body.
     */
    private fun splitFrontMatter(text: String): Pair<String, String> {
        val lines = text.split('\n')
        if (lines.firstOrNull()?.trim() != "---") return "" to text
        val closeIdx = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
            ?: return "" to text
        val header = lines.subList(1, closeIdx).joinToString("\n")
        val body = lines.subList(closeIdx + 1, lines.size).joinToString("\n")
            .trimStart('\n')
        return header to body
    }

    private fun parseFrontMatter(text: String): LectureFrontMatter {
        var id = ""
        var order = 0
        var title = ""
        var schemaVersion = 1
        val extras = linkedMapOf<String, String>()
        for (rawLine in text.split('\n')) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) continue
            val (k, v) = splitKeyValue(line) ?: continue
            val value = v.trim()
            when (k.trim()) {
                "id" -> id = value
                "order" -> order = value.toIntOrNull() ?: 0
                "title" -> title = value
                "schemaVersion" -> schemaVersion = value.toIntOrNull() ?: 1
                else -> extras[k.trim()] = value
            }
        }
        return LectureFrontMatter(
            id = id,
            order = order,
            title = title,
            schemaVersion = schemaVersion,
            extras = extras,
        )
    }

    /** Splits a comma-separated value list, trimming and dropping blanks. */
    private fun parseCsv(raw: String?): List<String> =
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    // Shared grammar primitives (splitHeader / splitKeyValue /
    // parseSemicolonFields) live in domain/ParserHelpers.kt and are used
    // inline above.
}
