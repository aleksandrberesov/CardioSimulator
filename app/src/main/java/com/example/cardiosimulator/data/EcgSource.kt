package com.example.cardiosimulator.data

import android.content.res.AssetManager
import java.io.File
import java.nio.charset.Charset

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
        assets.open(path).use { it.readBytes().decodeEcgText() }
    }.getOrNull()

    companion object {
        const val SERIES_DIR = "Series"
        const val PARTS_DIR = "Parts"
    }
}

/**
 * [EcgSource] backed by a directory in the local file system.
 * Automatically searches for "Series" and "Parts" subdirectories
 * within the given root to account for nested structures in archives.
 */
class FileEcgSource(
    rootDir: File,
    seriesDirName: String = AssetEcgSource.SERIES_DIR,
    partsDirName: String = AssetEcgSource.PARTS_DIR,
) : EcgSource {

    private val seriesDir: File? = findDir(rootDir, seriesDirName)
    private val partsDir: File? = findDir(rootDir, partsDirName)

    override fun listSeries(): List<String> = listDir(seriesDir)
    override fun listParts(): List<String> = listDir(partsDir)

    override fun readSeries(name: String): String? = read(seriesDir?.let { File(it, name) })
    override fun readPart(name: String): String? = read(partsDir?.let { File(it, name) })

    private fun listDir(dir: File?): List<String> =
        dir?.listFiles { f -> f.isFile }?.map { it.name }.orEmpty()

    private fun read(file: File?): String? = runCatching {
        if (file == null || !file.exists() || !file.canRead()) null
        else file.readBytes().decodeEcgText()
    }.getOrNull()

    fun isValid(): Boolean =
        seriesDir != null && seriesDir.isDirectory &&
        partsDir != null && partsDir.isDirectory

    private fun findDir(root: File, name: String): File? {
        if (root.name.equals(name, ignoreCase = true) && root.isDirectory) return root
        val files = root.listFiles() ?: return null
        // First check immediate children for efficiency
        for (f in files) {
            if (f.name.equals(name, ignoreCase = true) && f.isDirectory) return f
        }
        // Then search deeper
        for (f in files) {
            if (f.isDirectory) {
                val found = findDir(f, name)
                if (found != null) return found
            }
        }
        return null
    }
}

/**
 * Decodes a byte array into a string, attempting UTF-8 first and falling back
 * to windows-1251 if it doesn't look like valid UTF-8. This handles legacy
 * ECG datasets while supporting modern UTF-8 files.
 */
private fun ByteArray.decodeEcgText(): String {
    val utf8 = Charsets.UTF_8
    val decoded = toString(utf8)
    return if (decoded.contains('\uFFFD')) {
        toString(Charset.forName("windows-1251"))
    } else {
        decoded
    }
}
