package com.example.cardiosimulator.data

import com.example.cardiosimulator.data.crypto.WritableEncryptedOverlay
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest
import com.example.cardiosimulator.domain.PathologyParser

/**
 * Decorator that merges a read-only [EncryptedPathologySource] (base) with a
 * [WritableEncryptedOverlay] (writable). Overlay entries win, and tombstones
 * mark base entries as missing.
 */
class OverlayPathologySource(
    private val base: EncryptedPathologySource,
    private val overlay: WritableEncryptedOverlay
) : PathologySource, AutoCloseable {

    override fun readManifest(): PathologyManifest? {
        val baseManifest = base.readManifest() ?: return null
        val overlayManifestBytes = overlay.readEntry("manifest.txt")
        val overlayManifest = overlayManifestBytes?.let { 
            PathologyParser.parseManifest(String(it, Charsets.UTF_8)) 
        }

        // Merge: overlay entries win, tombstones are removed
        val mergedEntries = mutableMapOf<String, PathologyEntry>()
        baseManifest.entries.associateByTo(mergedEntries) { it.id }
        
        // Remove tombstones
        mergedEntries.keys.removeIf { overlay.isTombstoned("$it.dat") }
        
        // Add/Override from overlay manifest
        overlayManifest?.entries?.associateByTo(mergedEntries) { it.id }
        
        return baseManifest.copy(entries = mergedEntries.values.toList().sortedBy { it.number ?: Int.MAX_VALUE })
    }

    override fun readPathology(id: String): PathologyFile? {
        val bytes = overlay.readEntry("$id.dat")
        if (bytes != null) return PathologyParser.parsePathology(bytes)
        if (overlay.isTombstoned("$id.dat")) return null
        return base.readPathology(id)
    }

    override fun listPathologies(): List<String> {
        val all = mutableSetOf<String>()
        all.addAll(base.listPathologies())
        
        // Remove tombstones
        all.removeIf { overlay.isTombstoned("$it.dat") }
        
        // Add overlay entries
        overlay.listEntries().forEach { name ->
            if (name.endsWith(".dat")) {
                all.add(name.removeSuffix(".dat"))
            }
        }
        return all.toList()
    }

    override fun readGroupsText(): String? {
        val overlayGroups = overlay.readEntry("groups.txt")
        if (overlayGroups != null) return String(overlayGroups, Charsets.UTF_8)
        if (overlay.isTombstoned("groups.txt")) return null
        return base.readGroupsText()
    }

    /**
     * Writes [file] as binary CSD1 into the overlay. Updates overlay manifest.
     */
    fun writePathology(file: PathologyFile, leadOrder: List<Lead>? = null): Boolean {
        val manifest = readManifest()
        val order = leadOrder ?: manifest?.leadOrder ?: Lead.entries
        val binary = PathologyParser.serializePathologyBinary(file, order)
        overlay.writeEntry("${file.id}.dat", binary)

        // Update manifest in overlay
        val overlayManifestBytes = overlay.readEntry("manifest.txt")
        val overlayManifest = overlayManifestBytes?.let { 
            PathologyParser.parseManifest(String(it, Charsets.UTF_8)) 
        } ?: PathologyManifest(PathologyManifest.SUPPORTED_VERSION, manifest?.baseline ?: 0, order, emptyList())

        val existingIndex = overlayManifest.entries.indexOfFirst { it.id == file.id }
        val updatedEntries = if (existingIndex != -1) {
             overlayManifest.entries.map {
                if (it.id == file.id) {
                    it.copy(
                        titleEn = file.titleEn,
                        nameRu = file.nameRu,
                        leadsCount = file.leads.size,
                        group = file.group,
                        clinicalCase = file.clinicalCase,
                        number = file.number
                    )
                } else it
            }
        } else {
            overlayManifest.entries + PathologyEntry(
                id = file.id,
                titleEn = file.titleEn,
                nameRu = file.nameRu,
                leadsCount = file.leads.size,
                fileName = "${file.id}.dat",
                group = file.group,
                clinicalCase = file.clinicalCase,
                number = file.number
            )
        }
        
        val newManifest = overlayManifest.copy(entries = updatedEntries)
        overlay.writeEntry("manifest.txt", PathologyParser.serializeManifest(newManifest).toByteArray(Charsets.UTF_8))
        return true
    }

    fun deletePathology(id: String): Boolean {
        overlay.deleteEntry("$id.dat")
        
        // Also remove from overlay manifest if it was there
        val overlayManifestBytes = overlay.readEntry("manifest.txt")
        val overlayManifest = overlayManifestBytes?.let { 
            runCatching { PathologyParser.parseManifest(String(it, Charsets.UTF_8)) }.getOrNull()
        }
        if (overlayManifest != null) {
            val updatedEntries = overlayManifest.entries.filter { it.id != id }
            if (updatedEntries.size != overlayManifest.entries.size) {
                overlay.writeEntry("manifest.txt", PathologyParser.serializeManifest(overlayManifest.copy(entries = updatedEntries)).toByteArray(Charsets.UTF_8))
            }
        }
        return true
    }

    override fun close() {
        base.close()
        overlay.close()
    }
}
