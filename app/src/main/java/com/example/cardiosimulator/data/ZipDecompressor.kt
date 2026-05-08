package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ZipDecompressor {
    /**
     * Unzips the file at [zipUri] into [targetDir].
     * Returns true on success.
     */
    fun unzip(context: Context, zipUri: Uri, targetDir: File): Boolean = runCatching {
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipStream ->
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
