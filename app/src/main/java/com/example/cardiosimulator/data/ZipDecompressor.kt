package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

object ZipDecompressor {
    /**
     * Unzips the file at [zipUri] into [targetDir].
     * Returns true on success.
     */
    fun unzip(context: Context, zipUri: Uri, targetDir: File): Boolean = runCatching {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        // Heuristic for counting Russian letters
        fun countRussian(s: String): Int = s.count { it in '\u0410'..'\u044F' || it == 'ё' || it == 'Ё' }

        // 1. Determine the charset by scanning the ZIP with candidates
        var charset = Charsets.UTF_8
        var maxRussian = 0

        val candidates = listOf(Charsets.UTF_8, Charset.forName("IBM866"), Charset.forName("windows-1251"))
        
        for (candidate in candidates) {
            var currentRussian = 0
            try {
                context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                    ZipInputStream(inputStream, candidate).use { zipStream ->
                        var entry = zipStream.nextEntry
                        while (entry != null) {
                            currentRussian += countRussian(entry.name)
                            entry = zipStream.nextEntry
                        }
                    }
                }
                if (currentRussian > maxRussian) {
                    maxRussian = currentRussian
                    charset = candidate
                }
            } catch (e: Exception) { /* skip candidate if it fails */ }
        }

        // 2. Perform the actual extraction with the best charset
        context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
            ZipInputStream(inputStream, charset).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val file = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { outputStream ->
                            zipStream.copyTo(outputStream)
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
        }
        true
    }.getOrDefault(false)
}
