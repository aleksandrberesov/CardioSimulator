package com.example.cardiosimulator.data

import android.content.res.AssetManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

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
 * Decodes a byte array into a string, attempting several encodings common in
 * ECG datasets (UTF-8, windows-1251, CP866). Uses a heuristic to pick the
 * most likely correct one for Russian text.
 */
private fun ByteArray.decodeEcgText(): String {
    if (isEmpty()) return ""

    // 1. Check for UTF-16 BOMs
    if (size >= 2) {
        val b0 = get(0).toInt() and 0xFF
        val b1 = get(1).toInt() and 0xFF
        if (b0 == 0xFF && b1 == 0xFE) return String(this, 2, size - 2, Charset.forName("UTF-16LE"))
        if (b0 == 0xFE && b1 == 0xFF) return String(this, 2, size - 2, Charset.forName("UTF-16BE"))
    }

    fun countRussian(s: String): Int = s.count { it in '\u0410'..'\u044F' || it == 'ё' || it == 'Ё' }

    // 2. Try UTF-8 strictly
    val sUtf8 = try {
        val decoder = Charsets.UTF_8.newDecoder()
        decoder.onMalformedInput(CodingErrorAction.REPORT)
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(this)).toString()
    } catch (e: Exception) {
        null
    }

    val s1251 = String(this, Charset.forName("windows-1251"))
    val s866 = String(this, Charset.forName("IBM866"))

    val countUtf8 = sUtf8?.let { countRussian(it) } ?: -1
    val count1251 = countRussian(s1251)
    val count866 = countRussian(s866)

    // Heuristic: if UTF-8 succeeded but has almost no Russian, and fallbacks have many,
    // it's likely mojibake (e.g. 1251 bytes accidentally looking like valid UTF-8 symbols).
    if (sUtf8 != null && countUtf8 >= count1251 && countUtf8 >= count866) {
        // Also check if UTF-8 has suspicious amount of random non-Russian/non-Latin scripts
        val hasSuspiciousChars = sUtf8.any { it in '\u2000'..'\uDFFF' }
        if (!hasSuspiciousChars) return sUtf8
    }

    return when {
        count866 > count1251 && count866 > countUtf8 -> s866
        count1251 > countUtf8 -> s1251
        sUtf8 != null -> sUtf8
        else -> s1251 // Default fallback
    }
}
