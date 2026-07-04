package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ZipProgress(val done: Int, val total: Int, val currentEntry: String?)

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
     * SAF entry point. Counts entries (first pass) then extracts with progress (second pass).
     * Extracts [zipUri] into [targetDir], flattening any nested directory
     * structure so every entry lands directly under [targetDir]. Returns
     * true on success.
     */
    suspend fun extract(
        context: Context,
        zipUri: Uri,
        targetDir: File,
        onProgress: ((ZipProgress) -> Unit)? = null,
    ): Boolean {
        val total = countEntries(context, zipUri)          // reopen stream, count, close
        val input = context.contentResolver.openInputStream(zipUri) ?: return false
        return input.use { extractStream(it, targetDir, total, onProgress) }
    }

    private fun countEntries(context: Context, zipUri: Uri): Int =
        context.contentResolver.openInputStream(zipUri)?.use { inp ->
            ZipInputStream(inp, Charsets.UTF_8).use { z ->
                var n = 0; while (z.nextEntry != null) { n++; z.closeEntry() }; n
            }
        } ?: 0

    /** Testable core: extracts [input] into [targetDir], flattening, reporting throttled progress. */
    suspend fun extractStream(
        input: InputStream,
        targetDir: File,
        total: Int,
        onProgress: ((ZipProgress) -> Unit)? = null,
    ): Boolean {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        val step = maxOf(1, total / 100)                    // ≤ ~100 callbacks
        var done = 0
        try {
            ZipInputStream(input, Charsets.UTF_8).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()     // cooperative cancel
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast('/')
                        FileOutputStream(File(targetDir, name)).use { out -> zin.copyTo(out) }
                    }
                    done++
                    if (onProgress != null && (done == total || done % step == 0))
                        onProgress(ZipProgress(done, total, entry.name.substringAfterLast('/')))
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
            return true
        } catch (ce: CancellationException) {
            runCatching { if (targetDir.exists()) targetDir.deleteRecursively() }  // no half dataset
            throw ce
        } catch (t: Throwable) {
            return false
        }
    }
}
