package com.example.cardiosimulator.data.wfdb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WfdbReadWriteTest {
    @Test
    fun testRoundTripDat() {
        val samples = arrayOf(intArrayOf(10, 20, 30), intArrayOf(-10, -20, -30))
        val header = WfdbHeader(
            recordName = "test",
            numberOfSignals = 2,
            numberOfSamplesPerSignal = 3,
            signals = listOf(
                WfdbSignalSpec(fileName = "test.dat", format = 16, description = "I"),
                WfdbSignalSpec(fileName = "test.dat", format = 16, description = "II")
            )
        )
        val record = WfdbRecord(header, samples)
        
        val (newHeader, bytes) = WfdbWriter.build(record, WfdbStorage.DAT)
        val decodedRecord = WfdbReader.readRecord(newHeader) { bytes }
        
        assertEquals(record.header.recordName, decodedRecord.header.recordName)
        assertArrayEquals(record.samples[0], decodedRecord.samples[0])
        assertArrayEquals(record.samples[1], decodedRecord.samples[1])
    }

    @Test
    fun testRoundTripMat() {
        val samples = arrayOf(intArrayOf(10, 20, 30), intArrayOf(-10, -20, -30))
        val header = WfdbHeader(
            recordName = "test",
            numberOfSignals = 2,
            numberOfSamplesPerSignal = 3,
            signals = listOf(
                WfdbSignalSpec(fileName = "test.mat", format = 16, description = "I"),
                WfdbSignalSpec(fileName = "test.mat", format = 16, description = "II")
            )
        )
        val record = WfdbRecord(header, samples)
        
        val (newHeader, bytes) = WfdbWriter.build(record, WfdbStorage.MAT)
        val decodedRecord = WfdbReader.readRecord(newHeader) { bytes }
        
        assertEquals(record.header.recordName, decodedRecord.header.recordName)
        assertArrayEquals(record.samples[0], decodedRecord.samples[0])
        assertArrayEquals(record.samples[1], decodedRecord.samples[1])
    }
}
