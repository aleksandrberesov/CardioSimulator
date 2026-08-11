package com.example.cardiosimulator.data.crypto

import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Ported from ContentCrypto.cs. Handles secret assembly and key derivation.
 */
object ContentCrypto {
    private const val ITERATIONS = 100_000
    private const val KEY_LEN = 32
    private const val MASK = 0x5A.toByte()

    // These arrays must be ported verbatim from ContentCrypto.cs:118-143.
    // They are XOR-masked to prevent the secret from appearing as a single constant in the binary.
    private val ARRAY_A = byteArrayOf(
        0x3E.toByte(), 0x1A.toByte(), 0x7D.toByte(), 0x2C.toByte(), 0x5F.toByte(), 0x08.toByte(), 0x4D.toByte(), 0x6B.toByte(),
        0x3E.toByte(), 0x1A.toByte(), 0x7D.toByte(), 0x2C.toByte(), 0x5F.toByte(), 0x08.toByte(), 0x4D.toByte(), 0x6B.toByte(),
        0x3E.toByte(), 0x1A.toByte(), 0x7D.toByte(), 0x2C.toByte(), 0x5F.toByte(), 0x08.toByte(), 0x4D.toByte(), 0x6B.toByte(),
        0x3E.toByte(), 0x1A.toByte(), 0x7D.toByte(), 0x2C.toByte(), 0x5F.toByte(), 0x08.toByte(), 0x4D.toByte(), 0x6B.toByte()
    )
    private val ARRAY_B = byteArrayOf(
        0x6C.toByte(), 0x4F.toByte(), 0x2A.toByte(), 0x09.toByte(), 0x7E.toByte(), 0x5B.toByte(), 0x36.toByte(), 0x17.toByte(),
        0x6C.toByte(), 0x4F.toByte(), 0x2A.toByte(), 0x09.toByte(), 0x7E.toByte(), 0x5B.toByte(), 0x36.toByte(), 0x17.toByte(),
        0x6C.toByte(), 0x4F.toByte(), 0x2A.toByte(), 0x09.toByte(), 0x7E.toByte(), 0x5B.toByte(), 0x36.toByte(), 0x17.toByte(),
        0x6C.toByte(), 0x4F.toByte(), 0x2A.toByte(), 0x09.toByte(), 0x7E.toByte(), 0x5B.toByte(), 0x36.toByte(), 0x17.toByte()
    )

    fun getSecret(): ByteArray {
        val secret = ByteArray(32)
        for (i in 0 until 32) {
            secret[i] = (ARRAY_A[i].toInt() xor ARRAY_B[i].toInt() xor MASK.toInt()).toByte()
        }
        return secret
    }

    fun deriveKey(salt: ByteArray): ByteArray {
        return pbkdf2HmacSha256(getSecret(), salt, ITERATIONS, KEY_LEN)
    }

    fun looksLikePack(bytes: ByteArray): Boolean {
        return (bytes.size >= 4 &&
                bytes[0] == 'C'.code.toByte() &&
                bytes[1] == 'S'.code.toByte() &&
                bytes[2] == 'P'.code.toByte() &&
                bytes[3] == '2'.code.toByte())
    }

    /**
     * Hand-rolled PBKDF2-HMAC-SHA256 that operates on raw bytes to match Windows.
     * Avoids Java's PBEKeySpec which UTF-8 encodes a char[] password.
     */
    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val hLen = 32 // SHA-256 length
        if (dkLen > (Math.pow(2.0, 32.0).toLong() - 1) * hLen) throw GeneralSecurityException("Derived key too long")
        
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))

        val result = ByteArray(dkLen)
        val numBlocks = (dkLen + hLen - 1) / hLen
        val u = ByteArray(hLen)
        val t = ByteArray(hLen)
        val blockIndex = ByteArray(4)

        for (i in 1..numBlocks) {
            // Block index to big-endian
            blockIndex[0] = (i shr 24).toByte()
            blockIndex[1] = (i shr 16).toByte()
            blockIndex[2] = (i shr 8).toByte()
            blockIndex[3] = i.toByte()

            // First iteration: U_1 = PRF(P, S || INT(i))
            mac.update(salt)
            mac.update(blockIndex)
            val u1 = mac.doFinal()
            System.arraycopy(u1, 0, u, 0, hLen)
            System.arraycopy(u, 0, t, 0, hLen)

            // Subsequent iterations
            for (j in 2..iterations) {
                mac.update(u)
                val uj = mac.doFinal()
                System.arraycopy(uj, 0, u, 0, hLen)
                for (k in 0 until hLen) {
                    t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
                }
            }

            val offset = (i - 1) * hLen
            val len = Math.min(hLen, dkLen - offset)
            System.arraycopy(t, 0, result, offset, len)
        }
        return result
    }
}
