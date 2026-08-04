package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.domain.PathologyManifest
import com.example.cardiosimulator.domain.PathologyParser
import com.example.cardiosimulator.domain.LeadStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PathologyParserTest {

    private val sampleManifest = """
        version:1.0
        baseline:1024
        lead_order:I,II,III,aVR,aVL,aVF,V1,V2,V3,V4,V5,V6
        pathologies:2

        pathology:tachpm;leads:12;samples:31568;title:Atrial tachycardia
        pathology:emd;leads:6;samples:2412;title:Electromechanical dissociation
    """.trimIndent()

    private val samplePathology = """
        pathology:tachpm
        title:Atrial tachycardia
        name:Предсердная тахикардия
        leads:2

        lead:I
        count:4
        points:1024,1025,1023,1024

        lead:II
        count:3
        points:1024,1100,1050
    """.trimIndent()

    @Test
    fun `manifest parses version baseline lead order entries`() {
        val m = PathologyParser.parseManifest(sampleManifest)
        assertEquals("1.0", m.version)
        assertEquals(1024, m.baseline)
        assertEquals(12, m.leadOrder.size)
        assertEquals(Lead.I, m.leadOrder.first())
        assertEquals(Lead.V6, m.leadOrder.last())
        assertEquals(2, m.entries.size)
        assertEquals("tachpm", m.entries[0].id)
        assertEquals("Atrial tachycardia", m.entries[0].titleEn)
        assertEquals(12, m.entries[0].leadsCount)
        assertEquals("tachpm.dat", m.entries[0].fileName)
    }

    @Test(expected = PathologyParser.FormatException::class)
    fun `manifest with unsupported version rejected`() {
        val bad = sampleManifest.replace("version:1.0", "version:2.0")
        PathologyParser.parseManifest(bad)
    }

    @Test(expected = PathologyParser.FormatException::class)
    fun `manifest missing baseline rejected`() {
        val bad = sampleManifest.lines().filterNot { it.startsWith("baseline:") }.joinToString("\n")
        PathologyParser.parseManifest(bad)
    }

    @Test
    fun `pathology parses id title name and lead blocks`() {
        val file = PathologyParser.parsePathology(samplePathology)
        assertEquals("tachpm", file.id)
        assertEquals("Atrial tachycardia", file.titleEn)
        assertEquals("Предсердная тахикардия", file.nameRu)
        assertEquals(2, file.leads.size)

        val leadI = file.leads[Lead.I]!!
        assertEquals(Lead.I, leadI.lead)
        assertEquals(4, leadI.samples.size)
        assertEquals(1025, leadI.samples[1])

        val leadII = file.leads[Lead.II]!!
        assertEquals(3, leadII.samples.size)
        assertEquals(1100, leadII.samples[1])
    }

    @Test
    fun `pathology with mismatched count rejected`() {
        val bad = samplePathology.replace("count:4", "count:5")
        try {
            PathologyParser.parsePathology(bad)
            fail("expected FormatException")
        } catch (e: PathologyParser.FormatException) {
            assertTrue(e.message!!.contains("count"))
        }
    }

    @Test
    fun `pathology missing leads still parses present ones`() {
        // emd-style: only limb leads, no V1-V6.
        val emd = """
            pathology:emd
            title:EMD
            name:
            leads:6

            lead:I
            count:2
            points:1000,1024

            lead:II
            count:2
            points:1024,1100
        """.trimIndent()
        val file = PathologyParser.parsePathology(emd)
        assertEquals(2, file.leads.size)
        assertNull(file.leads[Lead.V1])
        assertNotNull(file.leads[Lead.I])
    }

    @Test
    fun `pathology serialization round-trips`() {
        val original = PathologyParser.parsePathology(samplePathology)
        val text = PathologyParser.serializePathology(original, Lead.entries)
        val parsed = PathologyParser.parsePathology(text)
        assertEquals(original, parsed)
    }

    @Test
    fun `manifest serialization round-trips`() {
        val original = PathologyParser.parseManifest(sampleManifest)
        val text = PathologyParser.serializeManifest(original)
        val parsed = PathologyParser.parseManifest(text)
        assertEquals(original.version, parsed.version)
        assertEquals(original.baseline, parsed.baseline)
        assertEquals(original.leadOrder, parsed.leadOrder)
        assertEquals(original.entries.map { it.id }, parsed.entries.map { it.id })
    }

    @Test
    fun `lead order is preserved in serialized output`() {
        val file = PathologyFile(
            id = "x",
            titleEn = "T",
            nameRu = null,
            leads = linkedMapOf(
                Lead.II to LeadStream(Lead.II, intArrayOf(1, 2)),
                Lead.I to LeadStream(Lead.I, intArrayOf(3, 4)),
            ),
        )
        val text = PathologyParser.serializePathology(file, listOf(Lead.I, Lead.II))
        // I appears first per the supplied order, even though leads map listed II first.
        val iIdx = text.indexOf("lead:I\n")
        val iiIdx = text.indexOf("lead:II\n")
        assertTrue("Lead.I block should appear before Lead.II", iIdx in 0 until iiIdx)
    }

    @Test
    fun `group field round-trips in manifest and pathology`() {
        val manifestWithGroup = """
            version:1.0
            baseline:1024
            lead_order:I
            pathologies:1

            pathology:test;leads:1;title:Test;group:sinus
        """.trimIndent()
        val m = PathologyParser.parseManifest(manifestWithGroup)
        assertEquals("sinus", m.entries[0].group)

        val serializedM = PathologyParser.serializeManifest(m)
        assertTrue(serializedM.contains(";group:sinus"))

        val pathologyWithGroup = """
            pathology:test
            title:Test
            group:sinus
            leads:1

            lead:I
            count:1
            points:1024
        """.trimIndent()
        val p = PathologyParser.parsePathology(pathologyWithGroup)
        assertEquals("sinus", p.group)

        val serializedP = PathologyParser.serializePathology(p, listOf(Lead.I))
        assertTrue(serializedP.contains("group:sinus\n"))
    }

    @Test
    fun `pathology parser handles multiline descriptions correctly`() {
        val datWithDescription = """
            pathology:desc_test
            title:Test Description
            description:This is a test\ndescription spread\nover multiple lines.
            leads:1

            lead:I
            count:2
            points:1024,1024
        """.trimIndent()

        val file = PathologyParser.parsePathology(datWithDescription)
        assertEquals("This is a test\ndescription spread\nover multiple lines.", file.description)

        val serialized = PathologyParser.serializePathology(file, listOf(Lead.I))
        assertTrue(serialized.contains("description:This is a test\\ndescription spread\\nover multiple lines."))
    }

    @Test
    fun `clinical_case field round-trips in manifest and pathology`() {
        val clinicalStr = "title=Severe Infarct,name=John Doe,age=45,gender=Male,hr=72,bp=120/80"
        val manifestWithClinical = """
            version:1.0
            baseline:1024
            lead_order:I
            pathologies:1

            pathology:test;leads:1;title:Test;clinical_case:$clinicalStr
        """.trimIndent()
        val m = PathologyParser.parseManifest(manifestWithClinical)
        assertEquals(clinicalStr, m.entries[0].clinicalCase)

        val serializedM = PathologyParser.serializeManifest(m)
        assertTrue(serializedM.contains(";clinical_case:$clinicalStr"))

        val pathologyWithClinical = """
            pathology:test
            title:Test
            clinical_case:$clinicalStr
            leads:1

            lead:I
            count:1
            points:1024
        """.trimIndent()
        val p = PathologyParser.parsePathology(pathologyWithClinical)
        assertEquals(clinicalStr, p.clinicalCase)

        val serializedP = PathologyParser.serializePathology(p, listOf(Lead.I))
        assertTrue(serializedP.contains("clinical_case:$clinicalStr\n"))
    }

    @Test
    fun `number field round-trips in manifest and pathology`() {
        val manifestWithNumber = """
            version:1.0
            baseline:1024
            lead_order:I
            pathologies:1

            pathology:test;leads:1;title:Test;number:7
        """.trimIndent()
        val m = PathologyParser.parseManifest(manifestWithNumber)
        assertEquals(7, m.entries[0].number)

        val serializedM = PathologyParser.serializeManifest(m)
        assertTrue(serializedM.contains(";number:7"))

        val pathologyWithNumber = """
            pathology:test
            title:Test
            number:42
            leads:1

            lead:I
            count:1
            points:1024
        """.trimIndent()
        val p = PathologyParser.parsePathology(pathologyWithNumber)
        assertEquals(42, p.number)

        val serializedP = PathologyParser.serializePathology(p, listOf(Lead.I))
        assertTrue(serializedP.contains("number:42\n"))
    }

    @Test
    fun `tips and tip_notes round-trip`() {
        val pathologyWithTips = """
            pathology:tips_test
            title:Tips Test
            tips:Arrow|Plain|I|Check this|100.000:10.500;200.000:20.000~Label|Plain|II|Caption with %7C %7E|500.500:-5.000
            tip_notes:Note 1%7Cwith pipe~Note 2%0Awith newline
            leads:1

            lead:I
            count:1
            points:1024
        """.trimIndent()

        val file = PathologyParser.parsePathology(pathologyWithTips)
        assertEquals(2, file.tips.size)
        assertEquals("Check this", file.tips[0].text)
        assertEquals(Lead.I, file.tips[0].lead)
        assertEquals(2, file.tips[0].points.size)
        assertEquals(10.5f, file.tips[0].points[0].adc)

        assertEquals("Caption with | ~", file.tips[1].text)
        assertEquals(Lead.II, file.tips[1].lead)

        assertEquals(2, file.tipComments.size)
        assertEquals("Note 1|with pipe", file.tipComments[0])
        assertEquals("Note 2\nwith newline", file.tipComments[1])

        val serialized = PathologyParser.serializePathology(file, listOf(Lead.I))
        val parsedAgain = PathologyParser.parsePathology(serialized)

        assertEquals(file.tips, parsedAgain.tips)
        assertEquals(file.tipComments, parsedAgain.tipComments)
    }

    @Test
    fun `binary CSD1 parses correctly`() {
        val header = "pathology:bin_test\ntitle:Binary Test\n"
        val leads = listOf(
            0 to intArrayOf(1024, 1025, 1023), // Lead I
            6 to intArrayOf(1000, 1100)        // Lead V1
        )
        val bytes = buildCSD1(header, leads)
        
        val file = PathologyParser.parsePathology(bytes)
        assertEquals("bin_test", file.id)
        assertEquals("Binary Test", file.titleEn)
        assertEquals(2, file.leads.size)
        
        assertEquals(1025, file.leads[Lead.I]!!.samples[1])
        assertEquals(1100, file.leads[Lead.V1]!!.samples[1])
    }

    @Test
    fun `binary CSD1 handles int16 overflow wrap-around`() {
        // {30000, -30000, 30000, 0, 32767, -32768}
        val header = "pathology:wrap\ntitle:Wrap\n"
        val samples = intArrayOf(30000, -30000, 30000, 0, 32767, -32768)
        val bytes = buildCSD1(header, listOf(0 to samples))
        
        val file = PathologyParser.parsePathology(bytes)
        val parsedSamples = file.leads[Lead.I]!!.samples
        for (i in samples.indices) {
            assertEquals("Sample at $i", samples[i], parsedSamples[i])
        }
    }

    @Test
    fun `text pathology with UTF-8 BOM parses`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val text = "pathology:bom\ntitle:BOM\nleads:0\n".toByteArray(Charsets.UTF_8)
        val bytes = bom + text
        
        val file = PathologyParser.parsePathology(bytes)
        assertEquals("bom", file.id)
    }

    private fun buildCSD1(header: String, leads: List<Pair<Int, IntArray>>): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        fun writeInt(i: Int) {
            bos.write(i and 0xFF); bos.write((i shr 8) and 0xFF)
            bos.write((i shr 16) and 0xFF); bos.write((i shr 24) and 0xFF)
        }
        fun writeShort(s: Short) {
            bos.write(s.toInt() and 0xFF); bos.write((s.toInt() shr 8) and 0xFF)
        }
        fun writeString(s: String?) {
            if (s == null) { writeInt(-1) } else {
                val b = s.toByteArray(Charsets.UTF_8); writeInt(b.size); bos.write(b)
            }
        }
        bos.write("CSD1".toByteArray())
        writeString(header)
        writeInt(leads.size)
        for ((idx, samples) in leads) {
            bos.write(idx)
            writeString(null) // elements
            writeInt(samples.size)
            var prev = 0
            for (v in samples) {
                writeShort((v - prev).toShort())
                prev = v
            }
        }
        return bos.toByteArray()
    }
}
