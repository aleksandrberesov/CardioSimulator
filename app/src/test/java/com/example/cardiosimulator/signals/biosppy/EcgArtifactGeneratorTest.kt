package com.example.cardiosimulator.signals.biosppy

import com.example.cardiosimulator.domain.EcgArtifact
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class EcgArtifactGeneratorTest {

    private fun cleanSine(n: Int, fs: Double): DoubleArray {
        val s = DoubleArray(n)
        for (i in 0 until n) {
            s[i] = sin(2.0 * PI * 1.0 * i / fs)
        }
        return s
    }

    @Test
    fun testArtifactPerturbsSignalAndIsDeterministic() {
        val kinds = listOf(
            EcgArtifact.Muscle,
            EcgArtifact.Mains,
            EcgArtifact.Baseline,
            EcgArtifact.Contact,
            EcgArtifact.Motion
        )

        for (kind in kinds) {
            val n = 2000
            val fs = 1000.0
            val clean = cleanSine(n, fs)

            val a = EcgArtifactGenerator.apply(clean, kind, fs, intensity = 1.0, seed = 42)
            val b = EcgArtifactGenerator.apply(clean, kind, fs, intensity = 1.0, seed = 42)

            // Length is preserved
            assertEquals(n, a.size)

            // Same seed -> identical output
            for (i in 0 until n) {
                assertEquals("Deterministic check failed for $kind", a[i], b[i], 1e-10)
            }

            // The artifact actually changed the trace
            var diff = 0.0
            for (i in 0 until n) {
                diff += abs(a[i] - clean[i])
            }
            assertTrue("Artifact $kind should perturb the signal", diff > 1e-6)
        }
    }

    @Test
    fun testMuscleArtifactVariesWithSeed() {
        val n = 2000
        val fs = 1000.0
        val clean = cleanSine(n, fs)

        val a = EcgArtifactGenerator.apply(clean, EcgArtifact.Muscle, fs, intensity = 1.0, seed = 1)
        val b = EcgArtifactGenerator.apply(clean, EcgArtifact.Muscle, fs, intensity = 1.0, seed = 2)

        var anyDifferent = false
        for (i in 0 until n) {
            if (abs(a[i] - b[i]) > 1e-9) {
                anyDifferent = true
                break
            }
        }
        assertTrue("Different seeds should yield different muscle noise", anyDifferent)
    }

    @Test
    fun testArtifactIntensityScalesAmplitude() {
        val n = 2000
        val fs = 1000.0
        val clean = cleanSine(n, fs)

        val low = EcgArtifactGenerator.apply(clean, EcgArtifact.Mains, fs, intensity = 0.5, seed = 7)
        val high = EcgArtifactGenerator.apply(clean, EcgArtifact.Mains, fs, intensity = 2.0, seed = 7)

        var lowEnergy = 0.0
        var highEnergy = 0.0
        for (i in 0 until n) {
            lowEnergy += abs(low[i] - clean[i])
            highEnergy += abs(high[i] - clean[i])
        }
        assertTrue("Higher intensity should add more noise energy", highEnergy > lowEnergy)
    }
}
