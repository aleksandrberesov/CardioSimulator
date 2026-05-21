package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest
import com.example.cardiosimulator.domain.PathologyParser
import java.io.File

/**
 * [PathologySource] backed by a directory on disk (typically
 * `filesDir/pathologies/`, populated by [PathologyZipExtractor]).
 *
 * Adds [writePathology] for the editor save flow — writes are atomic via
 * a `.tmp` + rename to avoid partial files on interrupt. The asset source
 * cannot be written; only the file-backed dataset is editable.
 */
class FilePathologySource(
    val root: File,
) : PathologySource {

    override fun readManifest(): PathologyManifest? = runCatching {
        val text = File(root, "manifest.txt").takeIf { it.canRead() }?.readText(Charsets.UTF_8)
            ?: return null
        PathologyParser.parseManifest(text)
    }.getOrNull()

    override fun readPathology(id: String): PathologyFile? = runCatching {
        val file = File(root, "$id.dat").takeIf { it.canRead() } ?: return null
        PathologyParser.parsePathology(file.readText(Charsets.UTF_8))
    }.getOrNull()

    override fun listPathologies(): List<String> =
        root.listFiles { f -> f.isFile && f.name.endsWith(".dat") }
            ?.map { it.name.removeSuffix(".dat") }
            ?: emptyList()

    /**
     * Atomically writes [file] as `<id>.dat`. Uses the manifest's canonical
     * lead order when present; otherwise falls back to [Lead.entries].
     */
    fun writePathology(file: PathologyFile, leadOrder: List<Lead>? = null): Boolean = runCatching {
        val target = File(root, "${file.id}.dat")
        target.parentFile?.mkdirs()
        val order = leadOrder ?: readManifest()?.leadOrder ?: Lead.entries
        val text = PathologyParser.serializePathology(file, order)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }.getOrDefault(false)

    fun isValid(): Boolean =
        root.isDirectory && File(root, "manifest.txt").canRead()
}
