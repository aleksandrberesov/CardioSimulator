package com.example.cardiosimulator.data

import java.io.File

/**
 * Atomically writes [text] to [target] via a `.tmp` + rename so an
 * interrupted write can't leave a half-written file in place.
 *
 * Steps:
 *   1. Ensure [target]'s parent directory exists.
 *   2. Write to `<target>.tmp` in the same directory.
 *   3. Delete the existing [target] if any (Android's `File.renameTo`
 *      won't overwrite).
 *   4. Rename the tmp file to [target].
 *
 * Returns true on success, false on any I/O failure. Both
 * [FilePathologySource] and [FileCourseSource] use this helper to keep
 * the rename idiom in one place.
 */
internal fun atomicWriteText(target: File, text: String): Boolean = runCatching {
    val parent = target.parentFile ?: return false
    parent.mkdirs()
    val tmp = File(parent, target.name + ".tmp")
    tmp.writeText(text, Charsets.UTF_8)
    if (target.exists()) target.delete()
    tmp.renameTo(target)
}.getOrDefault(false)
