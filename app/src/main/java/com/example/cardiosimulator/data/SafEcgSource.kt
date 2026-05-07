package com.example.cardiosimulator.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * [EcgSource] backed by a Storage Access Framework tree URI. The user
 * picks a folder via `ACTION_OPEN_DOCUMENT_TREE`; that folder must contain
 * `Series/` and `Parts/` subdirectories. Read access is granted
 * persistently via `takePersistableUriPermission`, so the same URI keeps
 * working across reboots.
 *
 * Listings/reads return empty/null on any IO failure (e.g. permission
 * revoked, folder deleted) — callers should treat empty data as a signal
 * to send the user back to the data-source picker.
 */
class SafEcgSource(
    private val context: Context,
    private val treeUri: Uri,
    private val seriesDirName: String = AssetEcgSource.SERIES_DIR,
    private val partsDirName: String = AssetEcgSource.PARTS_DIR,
) : EcgSource {

    private val root: DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    private val seriesDir: DocumentFile? by lazy { findDir(seriesDirName) }
    private val partsDir: DocumentFile? by lazy { findDir(partsDirName) }

    override fun listSeries(): List<String> = listFiles(seriesDir)
    override fun listParts(): List<String> = listFiles(partsDir)

    override fun readSeries(name: String): String? = readChild(seriesDir, name)
    override fun readPart(name: String): String? = readChild(partsDir, name)

    /** True iff the URI is still readable and both subdirectories exist. */
    fun isValid(): Boolean = seriesDir != null && partsDir != null

    private fun findDir(name: String): DocumentFile? = runCatching {
        root?.takeIf { it.isDirectory && it.canRead() }
            ?.findFile(name)
            ?.takeIf { it.isDirectory && it.canRead() }
    }.getOrNull()

    private fun listFiles(dir: DocumentFile?): List<String> = runCatching {
        dir?.listFiles()?.mapNotNull { f -> f.name?.takeIf { f.isFile && f.canRead() } }.orEmpty()
    }.getOrDefault(emptyList())

    private fun readChild(dir: DocumentFile?, name: String): String? = runCatching {
        val file = dir?.findFile(name) ?: return@runCatching null
        if (!file.isFile || !file.canRead()) return@runCatching null
        context.contentResolver.openInputStream(file.uri)?.use { stream ->
            stream.readBytes().toString(java.nio.charset.Charset.forName("windows-1251"))
        }
    }.getOrNull()
}
