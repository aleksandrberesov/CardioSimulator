package com.example.cardiosimulator.domain

/**
 * Pathology dataset domain types. Mirrors the flat `.dat` format documented
 * in `docs/data-structure.md`:
 *
 * - One `.dat` file per pathology.
 * - All 12 standard leads inside (one exception: `emd` ships only 6 limb leads).
 * - Raw ADC samples, baseline-centered on 1024.
 * - No anchors, no part/series indirection, no per-record calibration.
 */

/** 12-lead vocabulary used across the dataset, manifest, and renderer. */
enum class Lead {
    I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6;

    companion object {
        fun fromToken(raw: String): Lead? =
            Lead.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

/** Manifest header keys; [version] must be validated before consuming the rest. */
data class PathologyManifest(
    val version: String,
    val baseline: Int,
    val leadOrder: List<Lead>,
    val entries: List<PathologyEntry>,
) {
    companion object {
        const val SUPPORTED_VERSION: String = "1.0"
    }
}

/** One row of [PathologyManifest.entries]. */
data class PathologyEntry(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    val leadsCount: Int,
    val fileName: String,
    val group: String? = null
)

/** One lead block inside a `<pathology>.dat` file. */
data class LeadStream(
    val lead: Lead,
    val samples: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LeadStream) return false
        return lead == other.lead &&
                samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = lead.hashCode()
        result = 31 * result + samples.contentHashCode()
        return result
    }
}

/** Parsed `<pathology>.dat`. */
data class PathologyFile(
    val id: String,
    val titleEn: String,
    val nameRu: String?,
    val leads: Map<Lead, LeadStream>,
    val significantPoints: List<SignificantPoint> = emptyList(),
    val group: String? = null
)
