package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.DerivedLeads
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.domain.PathologyEntry
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest

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

    @Volatile private var manifest: PathologyManifest? = null

    fun setSource(newSource: PathologySource) {
        source = newSource
        manifest = null
    }

    fun loadManifest(): Boolean {
        val m = source.readManifest()
        manifest = m
        return m != null
    }

    fun manifest(): PathologyManifest? = manifest

    fun pathologies(): List<PathologyEntry> =
        manifest?.entries?.sortedBy { it.titleEn.lowercase() } ?: emptyList()

    fun readPathology(id: String): PathologyFile? = source.readPathology(id)

    /**
     * Returns the baseline-zeroed [Points] for one lead of one pathology,
     * synthesizing the lead via [DerivedLeads] if the file does not ship
     * it. Returns null when neither a direct lead nor a derivable basis
     * pair is available.
     */
    fun leadWaveform(id: String, lead: Lead): Points? {
        val file = readPathology(id) ?: return null
        val baseline = manifest?.baseline ?: DEFAULT_BASELINE

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
