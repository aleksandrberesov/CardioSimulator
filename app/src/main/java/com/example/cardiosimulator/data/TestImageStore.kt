package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object TestImageStore {
    fun copyImageToBank(context: Context, uri: Uri, bankImagesDir: File): String? {
        bankImagesDir.mkdirs()
        val extension = context.contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
        val fileName = "${UUID.randomUUID()}.$extension"
        val targetFile = File(bankImagesDir, fileName)
        
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }
}
