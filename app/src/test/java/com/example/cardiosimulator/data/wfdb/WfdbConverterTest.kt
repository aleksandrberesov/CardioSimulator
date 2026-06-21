package com.example.cardiosimulator.data.wfdb

import com.example.cardiosimulator.domain.Lead
import org.junit.Assert.assertEquals
import org.junit.Test

class WfdbConverterTest {
    @Test
    fun testToPathologyFile() {
        val samples = arrayOf(intArrayOf(200, 400)) // Gain 200, Baseline 0 -> 1mV, 2mV
        val header = WfdbHeader(
            recordName = "test",
            numberOfSignals = 1,
            numberOfSamplesPerSignal = 2,
            signals = listOf(
                WfdbSignalSpec(fileName = "test.dat", format = 16, gain = 200f, baseline = 0, description = "I")
            )
        )
        val record = WfdbRecord(header, samples)
        
        val pathology = WfdbConverter.toPathologyFile(record, "id1", "Title", "Название")
        
        assertEquals("id1", pathology.id)
        val leadI = pathology.leads[Lead.I]!!
        // 1mV -> 1024 + 1*256 = 1280
        // 2mV -> 1024 + 2*256 = 1536
        assertEquals(1280, leadI.samples[0])
        assertEquals(1536, leadI.samples[1])
    }
}
