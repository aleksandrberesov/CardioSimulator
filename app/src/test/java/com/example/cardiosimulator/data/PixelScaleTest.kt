package com.example.cardiosimulator.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelScaleTest {

    @Test
    fun testDisplayScaleFactor() {
        assertEquals(6.0f, displayScaleFactor(0))
        assertEquals(6.0f, displayScaleFactor(1))
        assertEquals(4.4f, displayScaleFactor(2))
        assertEquals(3.2f, displayScaleFactor(3))
        assertEquals(3.2f, displayScaleFactor(4))
        assertEquals(2.4f, displayScaleFactor(5))
        assertEquals(2.0f, displayScaleFactor(6))
        assertEquals(2.0f, displayScaleFactor(7))
        assertEquals(2.0f, displayScaleFactor(12))
        assertEquals(6.0f, displayScaleFactor(-1))
    }
}
