package com.example.cardiosimulator.data.wfdb

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WfdbSignalCodecTest {
    @Test
    fun testFormat16() {
        val samples = arrayOf(intArrayOf(1, -1, 1000), intArrayOf(0, 500, -500))
        val bytes = WfdbSignalCodec.encode(16, 2, 3, samples)
        val decoded = WfdbSignalCodec.decode(bytes, 16, 2, 3)
        assertArrayEquals(samples[0], decoded[0])
        assertArrayEquals(samples[1], decoded[1])
    }

    @Test
    fun testFormat212() {
        // A=0x123, B=0x456
        // b0 = 0x23, b1 = 0x41, b2 = 0x56
        val bytes = byteArrayOf(0x23.toByte(), 0x41.toByte(), 0x56.toByte())
        val decoded = WfdbSignalCodec.decodeFlat(bytes, 212, 2)
        assertArrayEquals(intArrayOf(0x123, 0x456), decoded)
    }

    @Test
    fun testFormat212Negative() {
        // s1=-1 (0xFFF), s2=-2 (0xFFE)
        // b0=0xFF, b1=0xFF, b2=0xFE
        val bytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte())
        val decoded = WfdbSignalCodec.decodeFlat(bytes, 212, 2)
        assertArrayEquals(intArrayOf(-1, -2), decoded)
    }

    @Test
    fun testFormat80() {
        // Offset binary 8-bit, 128 offset
        // 0 -> -128, 128 -> 0, 255 -> 127
        val bytes = byteArrayOf(0.toByte(), 128.toByte(), 255.toByte())
        val decoded = WfdbSignalCodec.decodeFlat(bytes, 80, 3)
        assertArrayEquals(intArrayOf(-128, 0, 127), decoded)
    }
}
