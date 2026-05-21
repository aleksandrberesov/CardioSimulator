package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Read-only ZIP extractor for the new flat `Pathologies.zip` bundle.
 *
 * The new format is UTF-8 throughout (see `docs/data-structure.md` §4),
 * so there is no charset detection — this is intentionally simpler than
 * the legacy [ZipDecompressor] which tried windows-1251 / CP866 / UTF-8
 * heuristically against the old CP1251 series files.
 */
object PathologyZipExtractor {
    /**
     * Extracts [zipUri] into [targetDir], flattening any nested directory
     * structure so every entry lands directly under [targetDir]. Returns
     * true on success.
     */
    fun extract(context: Context, zipUri: Uri, targetDir: File): Boolean = runCatching {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        context.contentResolver.openInputStream(zipUri)?.use { input ->
            ZipInputStream(input, Charsets.UTF_8).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast('/')
                        val outFile = File(targetDir, name)
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
