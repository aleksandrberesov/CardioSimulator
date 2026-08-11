package com.example.cardiosimulator.data

import com.example.cardiosimulator.data.crypto.ChunkedPackChannel
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.InputStream

/**
 * Wrapper over commons-compress ZipFile using ChunkedPackChannel for decryption.
 * Mirrors Windows EncryptedArchive.
 */
class EncryptedArchive(private val channel: ChunkedPackChannel) : AutoCloseable {
    
    private val zipFile = ZipFile.builder()
        .setSeekableByteChannel(channel)
        .get()
    
    /**
     * Returns the identity hash of the underlying pack (hex-encoded SHA-256 of the salt).
     */
    fun getIdentityHash(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(channel.getSalt())
        return hash.joinToString("") { "%02x".format(it) }
    }

    // Case-insensitive lookup index (Phase 5 requirement)
    private val entriesByName = zipFile.entries.asSequence()
        .associateBy { it.name.lowercase() }

    /**
     * Reads an entry by name (case-insensitive). Returns null if not found.
     */
    fun readByName(name: String): InputStream? {
        val entry = entriesByName[name.lowercase()] ?: return null
        return zipFile.getInputStream(entry)
    }

    /**
     * Alias for readByName.
     */
    fun readPath(path: String): InputStream? = readByName(path)

    /**
     * Lists all entry paths in the archive.
     */
    fun entryPaths(): List<String> = entriesByName.values.map { it.name }

    /**
     * Returns names of files with the specified extension (e.g. ".dat").
     */
    fun fileNamesWithExtension(extension: String): List<String> {
        val ext = extension.lowercase().let { if (it.startsWith(".")) it else ".$it" }
        return entriesByName.values
            .map { it.name }
            .filter { it.lowercase().endsWith(ext) }
    }

    override fun close() {
        zipFile.close()
        channel.close()
    }
}
