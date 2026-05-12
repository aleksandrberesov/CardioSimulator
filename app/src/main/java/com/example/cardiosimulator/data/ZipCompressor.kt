package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a ZIP archive from a directory and writes it to a SAF-provided
 * [Uri]. Mirrors [ZipDecompressor.unzip] but in the opposite direction —
 * used by the "Export ZIP" action so the user can grab an updated copy of
 * the edited dataset.
 */
object ZipCompressor {

    /**
     * Re-packs [sourceDir] into the file backing [destUri].
     * Returns true on success. Walks the directory tree depth-first and
     * preserves relative paths from [sourceDir].
     */
    fun zip(context: Context, sourceDir: File, destUri: Uri): Boolean = runCatching {
        if (!sourceDir.exists() || !sourceDir.isDirectory) return@runCatching false
        context.contentResolver.openOutputStream(destUri, "w")?.use { out ->
            ZipOutputStream(out).use { zos ->
                val rootPath = sourceDir.absolutePath
                sourceDir.walkTopDown().forEach { f ->
                    if (f.absolutePath == rootPath) return@forEach
                    val rel = f.absolutePath.removePrefix(rootPath).trimStart(File.separatorChar)
                    if (f.isDirectory) {
                        zos.putNextEntry(ZipEntry("$rel/"))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(rel))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        } ?: return@runCatching false
        true
    }.getOrDefault(false)

    /**
     * Re-packs [sourceDir] into a temp file inside [context]'s cache and
     * returns the resulting file. Used to obtain a fresh snapshot for TCP
     * upload after edits.
     */
    fun zipToCache(context: Context, sourceDir: File, fileName: String = "edited_ecg.zip"): File? = runCatching {
        if (!sourceDir.exists() || !sourceDir.isDirectory) return@runCatching null
        val out = File(context.cacheDir, fileName)
        if (out.exists()) out.delete()
        ZipOutputStream(out.outputStream()).use { zos ->
            val rootPath = sourceDir.absolutePath
            sourceDir.walkTopDown().forEach { f ->
                if (f.absolutePath == rootPath) return@forEach
                val rel = f.absolutePath.removePrefix(rootPath).trimStart(File.separatorChar)
                if (f.isDirectory) {
                    zos.putNextEntry(ZipEntry("$rel/"))
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        out
    }.getOrNull()
}
