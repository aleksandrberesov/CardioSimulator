package com.example.cardiosimulator.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class EcgAssemblyTests {

    @Test
    fun `split waveform into equal parts`() {
        val samples = IntArray(100) { it }
        val baseline = 10
        val partCount = 4
        
        val parts = EcgAssemblySlicer.split(samples, baseline, partCount)
        
        assertNotNull(parts)
        assertEquals(4, parts!!.size)
        assertEquals(25, parts[0].samples.size)
        assertEquals(0 - 10, parts[0].samples[0])
        assertEquals(25 - 10, parts[1].samples[0])
        assertEquals(50 - 10, parts[2].samples[0])
        assertEquals(75 - 10, parts[3].samples[0])
    }

    @Test
    fun `split drops remainder`() {
        val samples = IntArray(103) { it }
        val baseline = 0
        val partCount = 5
        
        val parts = EcgAssemblySlicer.split(samples, baseline, partCount)
        
        assertNotNull(parts)
        assertEquals(5, parts!!.size)
        assertEquals(20, parts[0].samples.size) // 103 / 5 = 20
        assertEquals(80, parts[4].samples[0])
    }

    @Test
    fun `AssemblyAttempt reorder grading`() {
        val part1 = EcgAssemblyPart(listOf(1, 2))
        val part2 = EcgAssemblyPart(listOf(3, 4))
        val spec = EcgAssembly(500, listOf(part1, part2))
        
        val attempt = AssemblyAttempt(spec, 42)
        assertFalse(attempt.isComplete)
        
        // Find keys for part 0 and part 1 in the shuffled palette
        val key0 = attempt.palette.find { it.correctIndex == 0 }!!.key
        val key1 = attempt.palette.find { it.correctIndex == 1 }!!.key
        
        // Place wrongly
        attempt.place(0, key1)
        attempt.place(1, key0)
        assertTrue(attempt.isComplete)
        assertFalse(attempt.allCorrect)
        
        // Place correctly
        attempt.place(0, key0)
        attempt.place(1, key1)
        assertTrue(attempt.allCorrect)
    }

    @Test
    fun `JSON round-trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val assembly = EcgAssembly(
            sampleRateHz = 500,
            parts = listOf(
                EcgAssemblyPart(listOf(1, 2, 3)),
                EcgAssemblyPart(listOf(4, 5, 6))
            ),
            sourcePathologyId = "patho1",
            sliceLead = Lead.II
        )
        val question = TestQuestion(
            id = "q1",
            number = 1,
            text = "Test",
            options = emptyList(),
            correctOptionId = "",
            comment = "Comment",
            assemble = assembly
        )

        val encoded = json.encodeToString(question)
        val decoded = json.decodeFromString<TestQuestion>(encoded)

        assertEquals(question.id, decoded.id)
        assertNotNull(decoded.assemble)
        assertEquals(2, decoded.assemble!!.parts.size)
        assertEquals("patho1", decoded.assemble!!.sourcePathologyId)
        assertEquals(Lead.II, decoded.assemble!!.sliceLead)
    }
}
