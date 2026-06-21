package com.example.cardiosimulator.data.wfdb

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MatlabLevel4 {
    /**
     * Reads a Level 4 .mat matrix (int16 only for WFDB).
     * Returns a 2D array [rows][cols] (in WFDB case [channels][samples]).
     */
    fun readMatrix(bytes: ByteArray): Pair<String, Array<IntArray>> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val type = buffer.int    // MOPT (usually 0030 for LE, int16, full)
        val rows = buffer.int
        val cols = buffer.int
        val imagf = buffer.int   // 0 for real, 1 for complex
        val namelen = buffer.int

        val nameBytes = ByteArray(namelen)
        buffer.get(nameBytes)
        val name = String(nameBytes).trimEnd('\u0000')

        val result = Array(rows) { IntArray(cols) }
        // Data is column-major
        for (c in 0 until cols) {
            for (r in 0 until rows) {
                result[r][c] = buffer.short.toInt()
            }
        }
        return name to result
    }

    fun writeInt16Matrix(name: String, rows: Int, cols: Int, dataColumnMajor: IntArray): ByteArray {
        val nameBytes = (name + "\u0000").toByteArray()
        val namelen = nameBytes.size
        val size = 20 + namelen + (rows * cols * 2)
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(30) // M=0 (LE), O=0, P=3 (int16), T=0 (full) -> 30
        buffer.putInt(rows)
        buffer.putInt(cols)
        buffer.putInt(0)
        buffer.putInt(namelen)
        buffer.put(nameBytes)

        for (v in dataColumnMajor) {
            buffer.putShort(v.toShort())
        }

        return buffer.array()
    }
}
