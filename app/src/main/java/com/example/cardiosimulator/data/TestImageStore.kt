package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object TestImageStore {
    fun copyImageToBank(context: Context, uri: Uri, bankImagesDir: File, questionId: String): String? {
        bankImagesDir.mkdirs()
        
        // Delete old images for this question
        deleteImageFromBank(bankImagesDir, questionId)

        val extension = context.contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
        val fileName = "${questionId}_${UUID.randomUUID().toString().take(8)}.$extension"
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

    fun deleteImageFromBank(bankImagesDir: File, questionId: String) {
        if (!bankImagesDir.exists()) return
        bankImagesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("${questionId}_")) {
                file.delete()
            }
        }
    }
}
