package com.example.cardiosimulator.data

import android.content.res.AssetManager

/**
 * Storage-agnostic source of ECG data files. Implementations expose the
 * `Series/` and `Parts/` directories as flat lists of named entries whose
 * contents can be read as text. This decouples [EcgRepository] from
 * [AssetManager] so data can come from APK assets, a user-picked folder
 * (Storage Access Framework), or a directory in the app's internal
 * storage (e.g. an unzipped dataset).
 */
interface EcgSource {
    fun listSeries(): List<String>
    fun listParts(): List<String>
    fun readSeries(name: String): String?
    fun readPart(name: String): String?
}

/**
 * Default implementation backed by Android assets. Preserves the legacy
 * behavior in which `Series/` and `Parts/` live inside the APK.
 */
class AssetEcgSource(
    private val assets: AssetManager,
    private val seriesDir: String = SERIES_DIR,
    private val partsDir: String = PARTS_DIR,
) : EcgSource {

    override fun listSeries(): List<String> = listDir(seriesDir)
    override fun listParts(): List<String> = listDir(partsDir)

    override fun readSeries(name: String): String? = read("$seriesDir/$name")
    override fun readPart(name: String): String? = read("$partsDir/$name")

    private fun listDir(dir: String): List<String> =
        runCatching { assets.list(dir)?.toList().orEmpty() }
            .getOrDefault(emptyList())

    private fun read(path: String): String? = runCatching {
        assets.open(path).use { it.readBytes().toString(Charsets.ISO_8859_1) }
    }.getOrNull()

    companion object {
        const val SERIES_DIR = "Series"
        const val PARTS_DIR = "Parts"
    }
}
