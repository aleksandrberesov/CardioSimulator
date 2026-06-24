package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.DerivedLeads
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current [PathologySource], caches the manifest, lazily reads
 * pathology files on demand, and exposes baseline-zeroed [Points] per
 * `(pathology id, Lead)`.
 *
 * Replaces the legacy `EcgRepository` (parts + series) in Phase 5.
 * Per the architecture migration plan, this class is renamed
 * `EcgRepository` once the legacy implementation is deleted.
 */
class PathologyRepository(private var source: PathologySource) {

    private val _manifest = MutableStateFlow<PathologyManifest?>(null)
    val manifestFlow: StateFlow<PathologyManifest?> = _manifest.asStateFlow()

    val groups = com.example.cardiosimulator.domain.PathologyGroups()

    fun setSource(newSource: PathologySource) {
        source = newSource
        _manifest.value = null
    }

    fun loadManifest(): Boolean {
        val m = source.readManifest()
        _manifest.value = m
        groups.load(source.readGroupsText())
        return m != null
    }

    fun manifest(): PathologyManifest? = _manifest.value

    fun pathologies(): List<PathologyEntry> =
        _manifest.value?.entries?.sortedBy { it.titleEn.lowercase() } ?: emptyList()

    fun readPathology(id: String): PathologyFile? = source.readPathology(id)

    fun createGroup(key: String, name: String): Boolean {
        val s = source
        if (s is FilePathologySource) {
            val success = s.appendGroup(key, name)
            if (success) {
                // Reload groups catalog
                groups.load(s.readGroupsText())
            }
            return success
        }
        return false
    }

    /**
     * Persists [file] back to the source. Only supported if the current source
     * is a [FilePathologySource]. Returns true on success.
     */
    fun writePathology(file: PathologyFile): Boolean {
        val s = source
        if (s is FilePathologySource) {
            val success = s.writePathology(file, manifest()?.leadOrder)
            if (success) {
                // Reload manifest to pick up title changes
                loadManifest()
            }
            return success
        }
        return false
    }

    fun deletePathology(id: String): Boolean {
        val s = source
        if (s is FilePathologySource) {
            val success = s.deletePathology(id)
            if (success) {
                loadManifest()
            }
            return success
        }
        return false
    }

    fun duplicatePathology(id: String): String? {
        val original = readPathology(id) ?: return null
        val newId = id + "_copy_" + System.currentTimeMillis().toString().takeLast(4)
        val newFile = original.copy(
            id = newId,
            titleEn = original.titleEn + " (Copy)",
            nameRu = original.nameRu?.let { it + " (Копия)" }
        )
        return if (writePathology(newFile)) newId else null
    }

    fun createPathology(id: String, titleEn: String, nameRu: String?): String? {
        val baseline = manifest()?.baseline ?: DEFAULT_BASELINE
        val leadOrder = manifest()?.leadOrder ?: Lead.entries
        val sampleCount = 501 // 1 second at 500Hz (0..500 indices = 1000ms)
        val blankSamples = IntArray(sampleCount) { baseline }
        
        val leads = leadOrder.associateWith { lead ->
            LeadStream(lead, blankSamples.copyOf())
        }
        
        val newFile = PathologyFile(
            id = id,
            titleEn = titleEn,
            nameRu = nameRu,
            leads = leads
        )
        return if (writePathology(newFile)) id else null
    }

    fun importPathology(file: PathologyFile): String? {
        val s = source
        if (s is FilePathologySource) {
            var uniqueId = file.id.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
            val existingIds = pathologies().map { it.id }.toSet()
            if (existingIds.contains(uniqueId)) {
                uniqueId += "_" + System.currentTimeMillis().toString().takeLast(4)
            }
            val importedFile = file.copy(id = uniqueId)
            return if (writePathology(importedFile)) uniqueId else null
        }
        return null
    }

    /**
     * Returns the baseline-zeroed [Points] for one lead of one pathology,
     * synthesizing the lead via [DerivedLeads] if the file does not ship
     * it. Returns null when neither a direct lead nor a derivable basis
     * pair is available.
     */
    fun leadWaveform(id: String, lead: Lead): Points? {
        val file = readPathology(id) ?: return null
        val baseline = manifest()?.baseline ?: DEFAULT_BASELINE

        file.leads[lead]?.let { stream ->
            return Points(stream.samples.map { (it - baseline).toFloat() })
        }
        return synthesize(lead, file.leads, baseline)?.let { Points(it) }
    }

    private fun synthesize(
        target: Lead,
        leads: Map<Lead, LeadStream>,
        baseline: Int,
    ): List<Float>? {
        fun zeroed(l: Lead) = leads[l]?.samples?.map { (it - baseline).toFloat() }
        return when (target) {
            Lead.III, Lead.aVR, Lead.aVL, Lead.aVF -> {
                val i = zeroed(Lead.I) ?: return null
                val ii = zeroed(Lead.II) ?: return null
                DerivedLeads.combineIII_aVR_aVL_aVF(i, ii, target).takeIf { it.isNotEmpty() }
            }
            Lead.V1, Lead.V3, Lead.V4, Lead.V5 -> {
                val v2 = zeroed(Lead.V2) ?: return null
                val v6 = zeroed(Lead.V6) ?: return null
                DerivedLeads.combineV1_V3_V4_V5(v2, v6, target).takeIf { it.isNotEmpty() }
            }
            else -> null
        }
    }

    companion object {
        private const val DEFAULT_BASELINE = 1024
    }
}
