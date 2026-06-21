package com.example.cardiosimulator.data.wfdb

import org.junit.Assert.assertEquals
import org.junit.Test

class WfdbHeaderParserTest {
    @Test
    fun testParseSimple() {
        val headerText = """
            JS00001 12 500 5000
            JS00001.mat 16+24 1000/mV 16 0 -254 21756 0 I
            # Some comment
        """.trimIndent()

        val header = WfdbHeaderParser.parse(headerText)
        assertEquals("JS00001", header.recordName)
        assertEquals(12, header.numberOfSignals)
        assertEquals(500f, header.samplingFrequency)
        assertEquals(5000, header.numberOfSamplesPerSignal)
        assertEquals(1, header.signals.size)
        assertEquals("JS00001.mat", header.signals[0].fileName)
        assertEquals(16, header.signals[0].format)
        assertEquals(24L, header.signals[0].byteOffset)
        assertEquals(1000f, header.signals[0].gain)
        assertEquals("mV", header.signals[0].units)
        assertEquals("I", header.signals[0].description)
        assertEquals(listOf("Some comment"), header.comments)
    }

    @Test
    fun testRoundTrip() {
        val headerText = """
            test 2 250.000 1000
            test.dat 16 200.0000(0)/mV 12 0 100 0 0 Lead1
            test.dat 16 200.0000(0)/mV 12 0 -50 0 0 Lead2
            # Comment 1
            # Comment 2
        """.trimIndent()

        val header = WfdbHeaderParser.parse(headerText)
        val serialized = WfdbHeaderParser.serialize(header)
        
        val header2 = WfdbHeaderParser.parse(serialized)
        assertEquals(header.recordName, header2.recordName)
        assertEquals(header.numberOfSignals, header2.numberOfSignals)
        assertEquals(header.samplingFrequency, header2.samplingFrequency, 0.001f)
        assertEquals(header.numberOfSamplesPerSignal, header2.numberOfSamplesPerSignal)
        assertEquals(header.signals, header2.signals)
        assertEquals(header.comments, header2.comments)
    }
}
