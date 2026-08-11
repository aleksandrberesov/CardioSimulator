package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest
import com.example.cardiosimulator.domain.PathologyParser

/**
 * [PathologySource] implementation over an [EncryptedArchive].
 * Mirrors Windows EncryptedPathologySource.
 */
class EncryptedPathologySource(
    private val archive: EncryptedArchive
) : PathologySource, AutoCloseable {

    override fun readManifest(): PathologyManifest? = runCatching {
        archive.readByName("manifest.txt")?.bufferedReader()?.use { it.readText() }
            ?.let { PathologyParser.parseManifest(it) }
    }.getOrNull()

    override fun readPathology(id: String): PathologyFile? = runCatching {
        archive.readByName("$id.dat")?.use { it.readBytes() }
            ?.let { PathologyParser.parsePathology(it) }
    }.getOrNull()

    override fun listPathologies(): List<String> =
        archive.fileNamesWithExtension(".dat")
            .map { it.removeSuffix(".dat") }

    override fun readGroupsText(): String? = runCatching {
        archive.readByName("groups.txt")?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    override fun close() {
        archive.close()
    }
}
