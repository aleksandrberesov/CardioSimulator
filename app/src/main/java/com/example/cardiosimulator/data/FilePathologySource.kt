package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest
import com.example.cardiosimulator.domain.PathologyParser
import java.io.File

/**
 * [PathologySource] backed by a directory on disk (typically
 * `filesDir/pathologies/`, populated by [PathologyZipExtractor]).
 *
 * Adds [writePathology] for the editor save flow — writes are atomic via
 * a `.tmp` + rename to avoid partial files on interrupt. The asset source
 * cannot be written; only the file-backed dataset is editable.
 */
class FilePathologySource(
    val root: File,
) : PathologySource {

    override fun readManifest(): PathologyManifest? = runCatching {
        val text = File(root, "manifest.txt").takeIf { it.canRead() }?.readText(Charsets.UTF_8)
            ?: return null
        PathologyParser.parseManifest(text)
    }.getOrNull()

    override fun readPathology(id: String): PathologyFile? = runCatching {
        val file = File(root, "$id.dat").takeIf { it.canRead() } ?: return null
        PathologyParser.parsePathology(file.readText(Charsets.UTF_8))
    }.getOrNull()

    override fun listPathologies(): List<String> =
        root.listFiles { f -> f.isFile && f.name.endsWith(".dat") }
            ?.map { it.name.removeSuffix(".dat") }
            ?: emptyList()

    /**
     * Atomically writes [file] as `<id>.dat`. Also updates the `manifest.txt`
     * if the pathology's metadata (title/name) has changed or if it's a new entry.
     */
    fun writePathology(file: PathologyFile, leadOrder: List<Lead>? = null): Boolean = runCatching {
        // 1. Write the .dat file
        val target = File(root, "${file.id}.dat")
        target.parentFile?.mkdirs()
        val manifest = readManifest()
        val order = leadOrder ?: manifest?.leadOrder ?: Lead.entries
        val text = PathologyParser.serializePathology(file, order)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) return false

        // 2. Update manifest.txt
        if (manifest != null) {
            val existingIndex = manifest.entries.indexOfFirst { it.id == file.id }
            val updatedEntries = if (existingIndex != -1) {
                val existing = manifest.entries[existingIndex]
                if (existing.titleEn != file.titleEn || existing.nameRu != file.nameRu) {
                    manifest.entries.map {
                        if (it.id == file.id) {
                            it.copy(titleEn = file.titleEn, nameRu = file.nameRu)
                        } else it
                    }
                } else null
            } else {
                // New entry
                manifest.entries + com.example.cardiosimulator.domain.PathologyEntry(
                    id = file.id,
                    titleEn = file.titleEn,
                    nameRu = file.nameRu,
                    leadsCount = file.leads.size,
                    fileName = "${file.id}.dat"
                )
            }

            if (updatedEntries != null) {
                val updatedManifest = manifest.copy(entries = updatedEntries)
                val manifestFile = File(root, "manifest.txt")
                val manifestText = PathologyParser.serializeManifest(updatedManifest)
                val mTmp = File(root, "manifest.txt.tmp")
                mTmp.writeText(manifestText, Charsets.UTF_8)
                if (manifestFile.exists()) manifestFile.delete()
                mTmp.renameTo(manifestFile)
            }
        }
        true
    }.getOrDefault(false)

    fun deletePathology(id: String): Boolean = runCatching {
        val target = File(root, "$id.dat")
        if (target.exists()) {
            target.delete()
        }

        val manifest = readManifest()
        if (manifest != null) {
            val updatedEntries = manifest.entries.filter { it.id != id }
            if (updatedEntries.size != manifest.entries.size) {
                val updatedManifest = manifest.copy(entries = updatedEntries)
                val manifestFile = File(root, "manifest.txt")
                val manifestText = PathologyParser.serializeManifest(updatedManifest)
                val mTmp = File(root, "manifest.txt.tmp")
                mTmp.writeText(manifestText, Charsets.UTF_8)
                if (manifestFile.exists()) manifestFile.delete()
                mTmp.renameTo(manifestFile)
            }
        }
        true
    }.getOrDefault(false)

    fun isValid(): Boolean =
        root.isDirectory && File(root, "manifest.txt").canRead()
}
