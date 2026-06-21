package com.example.cardiosimulator.data.wfdb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MatlabLevel4Test {
    @Test
    fun testRoundTrip() {
        val name = "val"
        val rows = 2
        val cols = 3
        val data = intArrayOf(1, 2, 3, 4, 5, 6)
        // [ 1, 3, 5 ]
        // [ 2, 4, 6 ]

        val bytes = MatlabLevel4.writeInt16Matrix(name, rows, cols, data)
        val (readName, readData) = MatlabLevel4.readMatrix(bytes)

        assertEquals(name, readName)
        assertEquals(rows, readData.size)
        assertEquals(cols, readData[0].size)
        assertArrayEquals(intArrayOf(1, 3, 5), readData[0])
        assertArrayEquals(intArrayOf(2, 4, 6), readData[1])
    }
}
