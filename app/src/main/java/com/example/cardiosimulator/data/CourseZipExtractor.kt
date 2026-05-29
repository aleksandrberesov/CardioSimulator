package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * ZIP extractor for the `Courses.zip` bundle.
 *
 * Unlike [PathologyZipExtractor] (which flattens to a single directory),
 * the course bundle's directory structure is **load-bearing**:
 * `<course-id>/course.txt`, `<course-id>/lectures/...`,
 * `<course-id>/assets/...`. Nested paths are preserved verbatim.
 *
 * UTF-8 only; no charset detection (see `docs/course-format.md` §8).
 */
object CourseZipExtractor {
    /**
     * Extracts [zipUri] into [targetDir], preserving the nested
     * directory layout. Wipes [targetDir] first so a re-import doesn't
     * leave stale files from a previous bundle. Returns true on success.
     */
    fun extract(context: Context, zipUri: Uri, targetDir: File): Boolean = runCatching {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        // Pre-compute the canonical root so the zip-slip guard doesn't
        // hit File.getCanonicalPath() for the targetDir on every entry.
        val rootCanonical = targetDir.canonicalPath
        val rootPrefix = rootCanonical + File.separator

        context.contentResolver.openInputStream(zipUri)?.use { input ->
            ZipInputStream(input, Charsets.UTF_8).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    val outFile = File(targetDir, entry.name)
                    val outCanonical = outFile.canonicalPath
                    // Guard against zip-slip: reject entries that resolve
                    // outside targetDir (e.g. `../../etc/passwd`).
                    if (outCanonical != rootCanonical && !outCanonical.startsWith(rootPrefix)) {
                        zin.closeEntry()
                        entry = zin.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zin.copyTo(out) }
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
        } ?: return@runCatching false
        true
    }.getOrDefault(false)
}
