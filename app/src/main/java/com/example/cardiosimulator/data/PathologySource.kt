package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest

/**
 * Storage-agnostic source for the flat pathology dataset. Implementations
 * expose a `manifest.txt` and one `.dat` file per pathology, both UTF-8.
 *
 * This interface is **read-only** by contract. The editor writes back
 * through [FilePathologySource.writePathology] on the file-backed
 * subclass — asset-backed sources cannot be written.
 *
 * Phase 5 of the architecture migration renames this to `EcgSource` after
 * the legacy interface (with `listSeries / listParts / readSeries /
 * readPart`) is deleted.
 */
interface PathologySource {
    fun readManifest(): PathologyManifest?
    fun readPathology(id: String): PathologyFile?
    fun listPathologies(): List<String>
}
