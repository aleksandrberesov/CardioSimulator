package com.example.cardiosimulator.data.wfdb

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WfdbSignalCodec {
    fun decode(bytes: ByteArray, format: Int, numberOfSignals: Int, numberOfSamples: Int): Array<IntArray> {
        val flat = decodeFlat(bytes, format, numberOfSignals * numberOfSamples)
        return reshape(flat, numberOfSignals, numberOfSamples)
    }

    fun decodeFlat(bytes: ByteArray, format: Int, totalSamples: Int): IntArray {
        val result = IntArray(totalSamples)
        val buffer = ByteBuffer.wrap(bytes)

        when (format) {
            16 -> {
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until totalSamples) {
                    if (buffer.remaining() >= 2) result[i] = buffer.short.toInt()
                }
            }
            61 -> {
                buffer.order(ByteOrder.BIG_ENDIAN)
                for (i in 0 until totalSamples) {
                    if (buffer.remaining() >= 2) result[i] = buffer.short.toInt()
                }
            }
            80 -> {
                for (i in 0 until totalSamples) {
                    if (buffer.remaining() >= 1) {
                        val unsigned = buffer.get().toInt() and 0xFF
                        result[i] = unsigned - 128
                    }
                }
            }
            212 -> {
                var i = 0
                while (i < totalSamples && buffer.remaining() >= 3) {
                    val b0 = buffer.get().toInt() and 0xFF
                    val b1 = buffer.get().toInt() and 0xFF
                    val b2 = buffer.get().toInt() and 0xFF

                    // Sample 1: b0 + (b1 & 0x0F) << 8
                    var s1 = b0 or ((b1 and 0x0F) shl 8)
                    if (s1 > 2047) s1 -= 4096
                    result[i++] = s1

                    if (i < totalSamples) {
                        // Sample 2: b2 + (b1 & 0xF0) << 4
                        var s2 = b2 or ((b1 and 0xF0) shl 4)
                        if (s2 > 2047) s2 -= 4096
                        result[i++] = s2
                    }
                }
            }
            24 -> {
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until totalSamples) {
                    if (buffer.remaining() >= 3) {
                        val b0 = buffer.get().toInt() and 0xFF
                        val b1 = buffer.get().toInt() and 0xFF
                        val b2 = buffer.get().toInt() and 0xFF
                        var s = b0 or (b1 shl 8) or (b2 shl 16)
                        if (s > 0x7FFFFF) s -= 0x1000000
                        result[i] = s
                    }
                }
            }
            32 -> {
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until totalSamples) {
                    if (buffer.remaining() >= 4) result[i] = buffer.int
                }
            }
            else -> throw IllegalArgumentException("Unsupported WFDB format: $format")
        }
        return result
    }

    fun encode(format: Int, numberOfSignals: Int, numberOfSamples: Int, samples: Array<IntArray>): ByteArray {
        if (format != 16) throw IllegalArgumentException("Only format 16 is supported for encoding")
        val flat = flatten(samples, numberOfSignals, numberOfSamples)
        val bytes = ByteArray(flat.size * 2)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (v in flat) {
            buf.putShort(v.toShort())
        }
        return bytes
    }

    fun reshape(flat: IntArray, numberOfSignals: Int, numberOfSamplesPerSignal: Int): Array<IntArray> {
        val result = Array(numberOfSignals) { IntArray(numberOfSamplesPerSignal) }
        var k = 0
        for (i in 0 until numberOfSamplesPerSignal) {
            for (j in 0 until numberOfSignals) {
                if (k < flat.size) {
                    result[j][i] = flat[k++]
                }
            }
        }
        return result
    }

    fun flatten(samples: Array<IntArray>, numberOfSignals: Int, numberOfSamplesPerSignal: Int): IntArray {
        val result = IntArray(numberOfSignals * numberOfSamplesPerSignal)
        var k = 0
        for (i in 0 until numberOfSamplesPerSignal) {
            for (j in 0 until numberOfSignals) {
                if (i < samples[j].size) {
                    result[k++] = samples[j][i]
                } else {
                    k++
                }
            }
        }
        return result
    }
}
