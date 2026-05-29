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
 * 3. `<lecture-id>.<lang>.md` — YAML-lite front matter delimited by
 *    `---` lines, then a Markdown body that the parser splits into a
 *    list of [CourseBlock]s by extracting the custom fenced blocks
 *    (`ecg`, `table`); everything else stays in [CourseBlock.Markdown].
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
        append('\n')
        for (l in course.lectures) {
            append("lecture:").append(l.id)
                .append(";title:").append(l.titleEn)
            if (!l.nameRu.isNullOrBlank()) append(";name:").append(l.nameRu)
            append('\n')
        }
    }

    // ─── <lecture-id>.<lang>.md ─────────────────────────────────────────

    /**
     * Parses a lecture file. [courseId] and [language] are injected by
     * the source layer (they're encoded in the file's directory and
     * filename, not the content).
     */
    fun parseLecture(text: String, courseId: String, language: String): Lecture {
        val (fmText, body) = splitFrontMatter(text)
        val fm = parseFrontMatter(fmText)
        val blocks = extractBlocks(body)
        return Lecture(
            id = fm.id,
            courseId = courseId,
            language = language,
            frontMatter = fm,
            blocks = blocks,
            rawMarkdown = body,
        )
    }

    /**
     * Serializes a lecture back to the on-disk format. Re-emits the
     * front matter (preserving unknown [LectureFrontMatter.extras] keys)
     * followed by [Lecture.rawMarkdown] verbatim.
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
        append(lecture.rawMarkdown)
    }

    // ─── front matter ───────────────────────────────────────────────────

    /**
     * Splits a lecture file into its `---`-delimited front matter and
     * the Markdown body. If no front matter is present, returns an
     * empty header and the whole text as the body.
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

    // ─── fenced-block extraction ────────────────────────────────────────

    private val FENCE_LINE = Regex("""^```\s*(\w+)\s*$""")

    /**
     * Walks [body] line by line, accumulating Markdown until a custom
     * fenced block (` ```ecg ` or ` ```table `) is encountered. Standard
     * code fences with any other info-string (`js`, `kotlin`, …) stay
     * inside Markdown runs untouched.
     */
    private fun extractBlocks(body: String): List<CourseBlock> {
        if (body.isEmpty()) return emptyList()
        val out = mutableListOf<CourseBlock>()
        val current = StringBuilder()
        val lines = body.split('\n')

        fun flushMarkdown() {
            val text = current.toString()
            if (text.isNotEmpty()) out += CourseBlock.Markdown(text)
            current.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val fence = FENCE_LINE.matchEntire(line.trimEnd('\r'))
            val tag = fence?.groupValues?.get(1)
            if (tag == "ecg" || tag == "table") {
                flushMarkdown()
                val end = (i + 1 until lines.size).firstOrNull { idx ->
                    lines[idx].trimEnd('\r').trim() == "```"
                } ?: lines.size
                val inner = lines.subList(i + 1, end).joinToString("\n")
                out += when (tag) {
                    "ecg" -> parseEcgFence(inner)
                    "table" -> parseTableFence(inner)
                    else -> error("unreachable")
                }
                i = end + 1
                continue
            }
            current.append(line)
            if (i != lines.lastIndex) current.append('\n')
            i++
        }
        flushMarkdown()
        return out
    }

    private fun parseEcgFence(inner: String): CourseBlock.EcgEmbed {
        val fields = parseKeyValueLines(inner)
        val pathology = fields["pathology"].orEmpty()
        val leadToken = fields["lead"]
        return CourseBlock.EcgEmbed(
            pathologyId = pathology,
            lead = leadToken?.takeIf { it.isNotBlank() }?.let { Lead.fromToken(it) },
            caption = fields["caption"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseTableFence(inner: String): CourseBlock.EditableTable {
        // Split header (key:value lines) from GFM body on the first
        // bare `---` line.
        val lines = inner.split('\n')
        val separator = lines.indexOfFirst { it.trimEnd('\r').trim() == "---" }
        val headerLines = if (separator >= 0) lines.subList(0, separator) else emptyList()
        val rawLines = if (separator >= 0) lines.subList(separator + 1, lines.size) else lines
        val header = parseKeyValueLines(headerLines.joinToString("\n"))
        return CourseBlock.EditableTable(
            id = header["id"].orEmpty(),
            editable = header["editable"]?.trim()?.lowercase() == "true",
            raw = rawLines.joinToString("\n").trim('\n'),
        )
    }

    // Shared grammar primitives (splitHeader / splitKeyValue /
    // parseSemicolonFields / parseKeyValueLines) live in
    // domain/ParserHelpers.kt and are used inline above.
}
