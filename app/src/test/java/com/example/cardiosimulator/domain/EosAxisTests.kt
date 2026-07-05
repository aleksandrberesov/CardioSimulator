package com.example.cardiosimulator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EosAxisTests {

    @Test
    fun testNetMm() {
        val measure = EosLeadMeasure(qMm = 1.0, rMm = 10.0, sMm = 2.0)
        assertEquals(7.0, measure.netMm, 1e-6)
    }

    @Test
    fun testAngleDegrees() {
        assertEquals(0.0, EosAxis.angleDegrees(1.0, 0.0), 1e-6)
        assertEquals(90.0, EosAxis.angleDegrees(0.0, 1.0), 1e-6)
        assertEquals(45.0, EosAxis.angleDegrees(1.0, 1.0), 1e-6)
        assertEquals(180.0, EosAxis.angleDegrees(-1.0, 0.0), 1e-6)
        assertEquals(-90.0, EosAxis.angleDegrees(0.0, -1.0), 1e-6)
    }

    @Test
    fun testClassify() {
        assertEquals(EosAxisClass.Horizontal, EosAxis.classify(0.0))
        assertEquals(EosAxisClass.Horizontal, EosAxis.classify(15.0))
        assertEquals(EosAxisClass.Horizontal, EosAxis.classify(29.0))
        
        assertEquals(EosAxisClass.Normal, EosAxis.classify(30.0))
        assertEquals(EosAxisClass.Normal, EosAxis.classify(45.0))
        assertEquals(EosAxisClass.Normal, EosAxis.classify(69.0))
        
        assertEquals(EosAxisClass.Vertical, EosAxis.classify(70.0))
        assertEquals(EosAxisClass.Vertical, EosAxis.classify(80.0))
        assertEquals(EosAxisClass.Vertical, EosAxis.classify(90.0))
        
        assertEquals(EosAxisClass.RightDeviation, EosAxis.classify(91.0))
        assertEquals(EosAxisClass.RightDeviation, EosAxis.classify(180.0))
        assertEquals(EosAxisClass.RightDeviation, EosAxis.classify(-180.0))
        
        assertEquals(EosAxisClass.LeftDeviation, EosAxis.classify(-1.0))
        assertEquals(EosAxisClass.LeftDeviation, EosAxis.classify(-45.0))
        assertEquals(EosAxisClass.LeftDeviation, EosAxis.classify(-89.0))
        
        assertEquals(EosAxisClass.ExtremeDeviation, EosAxis.classify(-90.0))
        assertEquals(EosAxisClass.ExtremeDeviation, EosAxis.classify(-135.0))
        assertEquals(EosAxisClass.ExtremeDeviation, EosAxis.classify(181.0))
    }

    @Test
    fun testWorkedExample() {
        // I net=2, aVF net=6 -> alpha ~= 71.6, Vertical
        val leadI = EosLeadMeasure(0.0, 2.0, 0.0)
        val leadAvf = EosLeadMeasure(0.0, 6.0, 0.0)
        val result = EosAxis.from(leadI, leadAvf)
        
        assertEquals(71.565, result.angleDeg, 0.001)
        assertEquals(EosAxisClass.Vertical, result.axisClass)
    }
}
