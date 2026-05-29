package com.example.cardiosimulator.domain

/**
 * Grammar primitives shared by [PathologyParser] and [CourseParser].
 *
 * Both formats are UTF-8 text with `key:value` lines, blank-line-separated
 * blocks, and semicolon-delimited index rows. Extracting the shared
 * routines into one place keeps the two pipelines from drifting under
 * future grammar tweaks (e.g. allowing leading whitespace, adding
 * escapes) — any change made here lands in both formats at once.
 */

/**
 * Splits a `key:value` line on the first `:`. Returns null when the line
 * has no `:` or starts with `:`.
 */
internal fun splitKeyValue(line: String): Pair<String, String>? {
    val i = line.indexOf(':')
    if (i <= 0) return null
    return line.substring(0, i).trim() to line.substring(i + 1)
}

/**
 * Splits a `key:value;key:value;…` line into a key→value map. Leading
 * and trailing whitespace on both keys and values is trimmed.
 */
internal fun parseSemicolonFields(line: String): Map<String, String> {
    val map = linkedMapOf<String, String>()
    for (field in line.split(';')) {
        val (k, v) = splitKeyValue(field) ?: continue
        map[k.trim()] = v.trim()
    }
    return map
}

/**
 * Splits a `key:value`-per-line text block into a map. Blank lines are
 * skipped; whitespace around keys and values is trimmed.
 */
internal fun parseKeyValueLines(text: String): Map<String, String> {
    val map = linkedMapOf<String, String>()
    for (raw in text.split('\n')) {
        val line = raw.trimEnd('\r')
        if (line.isBlank()) continue
        val (k, v) = splitKeyValue(line) ?: continue
        map[k.trim()] = v.trim()
    }
    return map
}

/**
 * Splits a file's `key:value` header block from its body lines. The
 * header runs until the first blank line; the body is everything after,
 * with blank lines filtered out.
 */
internal fun splitHeader(text: String): Pair<Map<String, String>, List<String>> {
    val lines = text.split('\n').map { it.trimEnd('\r') }
    val header = linkedMapOf<String, String>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) { i++; break }
        val (k, v) = splitKeyValue(line) ?: run { i++; continue }
        header[k] = v
        i++
    }
    val body = lines.drop(i).filter { it.isNotBlank() }
    return header to body
}
