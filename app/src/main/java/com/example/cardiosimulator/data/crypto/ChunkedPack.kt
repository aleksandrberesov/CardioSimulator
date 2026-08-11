package com.example.cardiosimulator.data.crypto

object ChunkedPack {
    val MAGIC = byteArrayOf('C'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), '2'.code.toByte())
    const val VERSION = 1.toByte()
    const val CHUNK_SIZE = 65536
    const val HEADER_LEN = 64
    const val TAG_LEN = 16
    const val NONCE_LEN = 12
    const val AAD_LEN = 5

    fun frameOffset(chunkIndex: Int): Long {
        return HEADER_LEN.toLong() + chunkIndex.toLong() * (CHUNK_SIZE + TAG_LEN)
    }

    fun chunkCount(plainLength: Long): Int {
        if (plainLength == 0L) return 0
        return ((plainLength + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
    }

    fun chunkLength(chunkIndex: Int, chunkCount: Int, plainLength: Long): Int {
        return if (chunkIndex < chunkCount - 1) {
            CHUNK_SIZE
        } else {
            (plainLength - (chunkCount - 1).toLong() * CHUNK_SIZE).toInt()
        }
    }

    fun expectedFileLength(plainLength: Long): Long {
        if (plainLength == 0L) return HEADER_LEN.toLong()
        val count = chunkCount(plainLength)
        val lastLen = chunkLength(count - 1, count, plainLength)
        return HEADER_LEN.toLong() + (count - 1).toLong() * (CHUNK_SIZE + TAG_LEN) + lastLen + TAG_LEN
    }

    fun writeNonce(nonceBase: ByteArray, chunkIndex: Int, out: ByteArray) {
        System.arraycopy(nonceBase, 0, out, 0, 8)
        // Big-endian uint32(chunkIndex)
        out[8] = ((chunkIndex shr 24) and 0xFF).toByte()
        out[9] = ((chunkIndex shr 16) and 0xFF).toByte()
        out[10] = ((chunkIndex shr 8) and 0xFF).toByte()
        out[11] = (chunkIndex and 0xFF).toByte()
    }

    fun writeAad(chunkIndex: Int, isFinal: Boolean, out: ByteArray) {
        // Big-endian uint32(chunkIndex)
        out[0] = ((chunkIndex shr 24) and 0xFF).toByte()
        out[1] = ((chunkIndex shr 16) and 0xFF).toByte()
        out[2] = ((chunkIndex shr 8) and 0xFF).toByte()
        out[3] = (chunkIndex and 0xFF).toByte()
        out[4] = if (isFinal) 1.toByte() else 0.toByte()
    }

    /**
     * Encrypts [plaintext] into a CSP2 pack and writes it to [output].
     */
    fun createPack(plaintext: ByteArray, output: java.io.OutputStream) {
        val secureRandom = java.security.SecureRandom()
        val salt = ByteArray(16)
        val nonceBase = ByteArray(8)
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(nonceBase)

        val key = ContentCrypto.deriveKey(salt)
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")

        // 1. Write Header (mostly)
        val header = java.nio.ByteBuffer.allocate(HEADER_LEN).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put(MAGIC)
        header.put(VERSION)
        header.position(8)
        header.put(salt)
        header.put(nonceBase)
        header.putInt(CHUNK_SIZE)
        header.putLong(plaintext.size.toLong())
        header.position(48)
        // Tag will be written at the end

        // 2. Compute Header Tag
        val headerNonce = ByteArray(12)
        writeNonce(nonceBase, -1, headerNonce)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, javax.crypto.spec.GCMParameterSpec(128, headerNonce))
        val headerAad = ByteArray(48)
        header.position(0)
        header.get(headerAad)
        cipher.updateAAD(headerAad)
        val headerTag = cipher.doFinal() // Encrypting empty plaintext
        
        header.position(48)
        header.put(headerTag)
        output.write(header.array())

        // 3. Write Chunks
        val plainLength = plaintext.size.toLong()
        val count = chunkCount(plainLength)
        for (i in 0 until count) {
            val isFinal = i == count - 1
            val offset = i * CHUNK_SIZE
            val len = chunkLength(i, count, plainLength)
            
            val nonce = ByteArray(12)
            writeNonce(nonceBase, i, nonce)
            val aad = ByteArray(AAD_LEN)
            writeAad(i, isFinal, aad)
            
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, javax.crypto.spec.GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            
            val encrypted = cipher.doFinal(plaintext, offset, len)
            output.write(encrypted)
        }
    }
}
