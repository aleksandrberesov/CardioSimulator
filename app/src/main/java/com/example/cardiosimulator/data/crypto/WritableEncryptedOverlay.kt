package com.example.cardiosimulator.data.crypto

import com.example.cardiosimulator.data.EncryptedArchive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Manages a small encrypted pack in `filesDir/overlays/`.
 * Supports writing entries, tombstones (deletes), and per-pack storage.
 */
class WritableEncryptedOverlay(
    private val overlayFile: File
) : AutoCloseable {
    private var archive: EncryptedArchive? = null
    private val memoryEntries = mutableMapOf<String, ByteArray>()
    private val tombstones = mutableSetOf<String>()

    init {
        load()
    }

    private fun load() {
        if (!overlayFile.exists() || overlayFile.length() == 0L) return
        
        runCatching {
            val fis = java.io.FileInputStream(overlayFile)
            val channel = ChunkedPackChannel(fis.channel)
            val arch = EncryptedArchive(channel)
            archive = arch
            
            // Load tombstones
            arch.readByName("tombstones.txt")?.bufferedReader()?.useLines { lines ->
                lines.forEach { if (it.isNotBlank()) tombstones.add(it) }
            }
        }.onFailure {
            it.printStackTrace()
            // If corrupt, start fresh
            archive?.close()
            archive = null
            tombstones.clear()
        }
    }

    fun readEntry(name: String): ByteArray? {
        if (tombstones.contains(name)) return null
        memoryEntries[name]?.let { return it }
        return archive?.readByName(name)?.use { it.readBytes() }
    }

    fun writeEntry(name: String, content: ByteArray) {
        tombstones.remove(name)
        memoryEntries[name] = content
        save()
    }

    fun deleteEntry(name: String) {
        memoryEntries.remove(name)
        tombstones.add(name)
        save()
    }

    fun isTombstoned(name: String): Boolean = tombstones.contains(name)

    fun listEntries(): List<String> {
        val all = mutableSetOf<String>()
        archive?.entryPaths()?.let { all.addAll(it) }
        all.addAll(memoryEntries.keys)
        all.remove("tombstones.txt")
        all.removeAll(tombstones)
        return all.toList()
    }

    private fun save() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            // Write all entries (merging memory and archive)
            val allNames = mutableSetOf<String>()
            allNames.addAll(memoryEntries.keys)
            archive?.entryPaths()?.let { allNames.addAll(it) }
            allNames.remove("tombstones.txt")
            
            for (name in allNames) {
                if (tombstones.contains(name)) continue
                
                val content = memoryEntries[name] ?: archive?.readByName(name)?.use { it.readBytes() } ?: continue
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
            
            // Write tombstones
            if (tombstones.isNotEmpty()) {
                zos.putNextEntry(ZipEntry("tombstones.txt"))
                zos.write(tombstones.joinToString("\n").toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        
        val zipBytes = bos.toByteArray()
        val parent = overlayFile.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        
        val tmpFile = File(parent, overlayFile.name + ".tmp")
        FileOutputStream(tmpFile).use { fos ->
            ChunkedPack.createPack(zipBytes, fos)
        }
        
        // Atomic swap
        archive?.close()
        archive = null
        if (overlayFile.exists()) overlayFile.delete()
        tmpFile.renameTo(overlayFile)
        
        load() // Re-open the new pack
    }

    override fun close() {
        archive?.close()
        archive = null
    }

    companion object {
        fun getOverlayFile(baseDir: File, packIdentityHash: String): File {
            val overlaysDir = File(baseDir, "overlays")
            return File(overlaysDir, "$packIdentityHash.pak")
        }
    }
}
