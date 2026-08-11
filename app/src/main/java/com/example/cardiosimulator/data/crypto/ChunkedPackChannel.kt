package com.example.cardiosimulator.data.crypto

import android.util.LruCache
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A SeekableByteChannel that decrypts CSP2 chunked packs on demand.
 */
class ChunkedPackChannel(
    private val delegate: FileChannel
) : SeekableByteChannel {
    
    private val header = ByteBuffer.allocate(ChunkedPack.HEADER_LEN)
    private var plainLength: Long = 0
    private var chunkCount: Int = 0
    private val nonceBase = ByteArray(8)
    private val salt = ByteArray(16)
    
    private var position: Long = 0
    private var isOpen = true

    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val keySpec: SecretKeySpec
    
    // LRU cache for decrypted chunks: chunkIndex -> decryptedBytes
    private val chunkCache = object : LruCache<Int, ByteArray>(8) {}
    
    private val lock = Any()

    /**
     * Returns a copy of the pack's salt. Used for identity hashing.
     */
    fun getSalt(): ByteArray = salt.copyOf()

    init {
        delegate.position(0)
        var totalRead = 0
        while (totalRead < ChunkedPack.HEADER_LEN) {
            val r = delegate.read(header)
            if (r == -1) throw java.io.IOException("Truncated header")
            totalRead += r
        }
        header.flip()
        
        // Validate magic
        val magic = ByteArray(4)
        header.get(magic)
        if (!magic.contentEquals(ChunkedPack.MAGIC)) {
            throw java.io.IOException("Not a CSP2 pack")
        }
        
        val version = header.get()
        if (version != ChunkedPack.VERSION) {
            throw java.io.IOException("Unsupported version: $version")
        }
        
        header.position(8) // Skip reserved
        header.get(salt)
        header.get(nonceBase)
        
        val chunkSize = header.int
        if (chunkSize != ChunkedPack.CHUNK_SIZE) {
            throw java.io.IOException("Unsupported chunk size: $chunkSize")
        }
        
        plainLength = header.long
        chunkCount = ChunkedPack.chunkCount(plainLength)
        
        // Validate file length
        val actualLen = delegate.size()
        val expectedLen = ChunkedPack.expectedFileLength(plainLength)
        if (actualLen != expectedLen) {
            throw java.io.IOException("Pack length mismatch: expected $expectedLen, got $actualLen")
        }
        
        // Derive key
        val key = ContentCrypto.deriveKey(salt)
        keySpec = SecretKeySpec(key, "AES")
        
        // Validate header tag
        val headerTag = ByteArray(16)
        header.position(48)
        header.get(headerTag)
        
        validateHeaderTag(headerTag)
    }

    private fun validateHeaderTag(tag: ByteArray) {
        val nonce = ByteArray(12)
        ChunkedPack.writeNonce(nonceBase, -1, nonce) // Index 0xFFFFFFFF
        
        val aad = ByteArray(48)
        val pos = header.position()
        header.position(0)
        header.get(aad)
        header.position(pos)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        
        // GCM decrypt empty ciphertext with tag
        try {
            cipher.doFinal(tag)
        } catch (e: Exception) {
            throw java.security.GeneralSecurityException("Pack header authentication failed (wrong key or corrupt header)", e)
        }
    }

    override fun read(dst: ByteBuffer): Int {
        synchronized(lock) {
            if (!isOpen) throw java.nio.channels.ClosedChannelException()
            if (position >= plainLength) return -1
            
            val toRead = Math.min(dst.remaining().toLong(), plainLength - position).toInt()
            var bytesReadTotal = 0
            
            while (bytesReadTotal < toRead) {
                val chunkIndex = (position / ChunkedPack.CHUNK_SIZE).toInt()
                val chunkOffset = (position % ChunkedPack.CHUNK_SIZE).toInt()
                
                val chunk = getOrDecryptChunk(chunkIndex)
                val available = chunk.size - chunkOffset
                val take = Math.min(toRead - bytesReadTotal, available)
                
                dst.put(chunk, chunkOffset, take)
                bytesReadTotal += take
                position += take
            }
            
            return bytesReadTotal
        }
    }

    private fun getOrDecryptChunk(index: Int): ByteArray {
        chunkCache.get(index)?.let { return it }
        
        val isFinal = index == chunkCount - 1
        val plainLen = ChunkedPack.chunkLength(index, chunkCount, plainLength)
        val cipherLen = plainLen + ChunkedPack.TAG_LEN
        
        val frameOffset = ChunkedPack.frameOffset(index)
        val encrypted = ByteBuffer.allocate(cipherLen)
        
        delegate.position(frameOffset)
        var totalRead = 0
        while (totalRead < cipherLen) {
            val r = delegate.read(encrypted)
            if (r == -1) throw java.io.IOException("Truncated chunk $index")
            totalRead += r
        }
        encrypted.flip()
        
        val nonce = ByteArray(12)
        ChunkedPack.writeNonce(nonceBase, index, nonce)
        
        val aad = ByteArray(ChunkedPack.AAD_LEN)
        ChunkedPack.writeAad(index, isFinal, aad)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        
        val decrypted = try {
            cipher.doFinal(encrypted.array(), 0, cipherLen)
        } catch (e: Exception) {
            throw java.security.GeneralSecurityException("Chunk $index authentication failed", e)
        }
        
        chunkCache.put(index, decrypted)
        return decrypted
    }

    override fun write(src: ByteBuffer?): Int = throw java.nio.channels.NonWritableChannelException()
    override fun position(): Long = position
    override fun position(newPosition: Long): SeekableByteChannel {
        synchronized(lock) {
            position = newPosition
            return this
        }
    }
    override fun size(): Long = plainLength
    override fun truncate(size: Long): SeekableByteChannel = throw java.nio.channels.NonWritableChannelException()
    override fun isOpen(): Boolean = isOpen
    override fun close() {
        synchronized(lock) {
            if (isOpen) {
                delegate.close()
                chunkCache.evictAll()
                isOpen = false
            }
        }
    }
}
