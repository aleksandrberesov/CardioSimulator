package com.example.cardiosimulator.data

import android.content.res.AssetManager
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest
import com.example.cardiosimulator.domain.PathologyParser

/**
 * [PathologySource] backed by Android assets. Reads `assets/Pathologies/`
 * (flat layout, UTF-8, see `docs/data-structure.md` §4).
 *
 * Used as the boot-time default when the user hasn't yet picked a custom
 * ZIP via SAF.
 */
class AssetPathologySource(
    private val assets: AssetManager,
    private val baseDir: String = DEFAULT_DIR,
) : PathologySource {

    override fun readManifest(): PathologyManifest? = runCatching {
        val text = readText("$baseDir/manifest.txt") ?: return null
        PathologyParser.parseManifest(text)
    }.getOrNull()

    override fun readPathology(id: String): PathologyFile? = runCatching {
        val text = readText("$baseDir/$id.dat") ?: return null
        PathologyParser.parsePathology(text)
    }.getOrNull()

    override fun listPathologies(): List<String> = runCatching {
        assets.list(baseDir)?.toList().orEmpty()
            .filter { it.endsWith(".dat") }
            .map { it.removeSuffix(".dat") }
    }.getOrDefault(emptyList())

    override fun readGroupsText(): String? = readText("$baseDir/groups.txt")

    private fun readText(path: String): String? = runCatching {
        assets.open(path).use { String(it.readBytes(), Charsets.UTF_8) }
    }.getOrNull()

    companion object {
        const val DEFAULT_DIR: String = "Pathologies"
    }
}
