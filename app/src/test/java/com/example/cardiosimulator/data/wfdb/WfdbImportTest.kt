package com.example.cardiosimulator.data.wfdb

import com.example.cardiosimulator.data.FilePathologySource
import com.example.cardiosimulator.data.PathologyRepository
import com.example.cardiosimulator.domain.Lead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WfdbImportTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testImportEndToEnd() {
        val root = tempFolder.newFolder("pathologies")
        val manifestFile = java.io.File(root, "manifest.txt")
        manifestFile.writeText("version:1.0\nbaseline:1024\nlead_order:I,II,III,aVR,aVL,aVF,V1,V2,V3,V4,V5,V6\n")
        
        val source = FilePathologySource(root)
        val repository = PathologyRepository(source)
        repository.loadManifest()
        
        // 1. Create a synthetic WFDB record
        val samples = arrayOf(intArrayOf(256, 0, -256)) // 1mV, 0mV, -1mV if gain=256, baseline=0
        val header = WfdbHeader(
            recordName = "JS00001",
            numberOfSignals = 1,
            numberOfSamplesPerSignal = 3,
            signals = listOf(
                WfdbSignalSpec(fileName = "JS00001.dat", format = 16, gain = 256f, baseline = 0, description = "I")
            )
        )
        val record = WfdbRecord(header, samples)
        
        // 2. Convert
        val pathology = WfdbConverter.toPathologyFile(record, "JS00001", "Chapman", "Чепмен")
        
        // 3. Import
        val importedId = repository.importPathology(pathology)
        assertNotNull(importedId)
        
        // 4. Verify persistence
        repository.loadManifest()
        val entry = repository.pathologies().find { it.id == importedId }
        assertNotNull(entry)
        assertEquals("Chapman", entry?.titleEn)
        
        val readBack = repository.readPathology(importedId!!)
        assertNotNull(readBack)
        val leadI = readBack?.leads?.get(Lead.I)
        assertNotNull(leadI)
        // 1mV -> 1024 + 1*256 = 1280
        // 0mV -> 1024 + 0*256 = 1024
        // -1mV -> 1024 - 1*256 = 768
        assertEquals(1280, leadI?.samples?.get(0))
        assertEquals(1024, leadI?.samples?.get(1))
        assertEquals(768, leadI?.samples?.get(2))
    }
}
