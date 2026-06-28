package com.example.cardiosimulator.signals.biosppy

import com.example.cardiosimulator.domain.EcgArtifact
import kotlin.math.*
import kotlin.random.Random

object EcgArtifactGenerator {
    private const val TAU = 2.0 * PI

    fun apply(
        signal: DoubleArray,
        kind: EcgArtifact,
        samplingRate: Double,
        intensity: Double = 1.0,
        seed: Int = 0
    ): DoubleArray {
        val n = signal.size
        if (n == 0) return DoubleArray(0)

        val reference = peakToPeak(signal)
        val noise = generate(n, kind, samplingRate, reference, intensity, seed)

        val result = DoubleArray(n)
        for (i in 0 until n) result[i] = signal[i] + noise[i]
        return result
    }

    fun generate(
        n: Int,
        kind: EcgArtifact,
        samplingRate: Double,
        referenceAmplitude: Double,
        intensity: Double,
        seed: Int
    ): DoubleArray {
        if (n <= 0) return DoubleArray(0)
        val fs = if (samplingRate <= 0) 1000.0 else samplingRate
        val refAmp = if (referenceAmplitude > 1e-9) referenceAmplitude else 1.0
        val rng = Random(seed)

        return when (kind) {
            EcgArtifact.Mains -> mains(n, fs, refAmp, intensity)
            EcgArtifact.Muscle -> muscle(n, fs, refAmp, intensity, rng)
            EcgArtifact.Baseline -> baseline(n, fs, refAmp, intensity, rng)
            EcgArtifact.Motion -> motion(n, fs, refAmp, intensity, rng)
            EcgArtifact.Contact -> contact(n, fs, refAmp, intensity, rng)
            else -> DoubleArray(n)
        }
    }

    private fun mains(n: Int, fs: Double, refAmp: Double, intensity: Double): DoubleArray {
        val f0 = 50.0
        val amp = 0.06 * refAmp * intensity
        val nyq = fs / 2.0
        val noise = DoubleArray(n)
        for (i in 0 until n) {
            val t = i / fs
            var v = sin(2.0 * PI * f0 * t)
            if (3.0 * f0 < nyq) v += 0.2 * sin(2.0 * PI * 3.0 * f0 * t)
            noise[i] = amp * v
        }
        return noise
    }

    private fun muscle(n: Int, fs: Double, refAmp: Double, intensity: Double, rng: Random): DoubleArray {
        val white = whiteNoise(n, rng)
        val cut = 25.0
        val nyq = fs / 2.0
        var band = white
        if (n > 30 && cut < nyq) {
            try {
                val (b, a) = Filter.butterworth(order = 2, Wn = doubleArrayOf(cut / nyq), band = "highpass")
                band = Filter.filtfilt(b, a, white)
            } catch (e: Exception) {
                // signal too short for filtfilt padding
            }
        }
        val targetStd = 0.05 * refAmp * intensity
        return scaleToStd(band, targetStd)
    }

    private fun baseline(n: Int, fs: Double, refAmp: Double, intensity: Double, rng: Random): DoubleArray {
        val amp = 0.22 * refAmp * intensity
        val freqs = doubleArrayOf(0.15, 0.3, 0.5)
        val weights = doubleArrayOf(1.0, 0.5, 0.3)
        val phases = doubleArrayOf(rng.nextDouble() * TAU, rng.nextDouble() * TAU, rng.nextDouble() * TAU)
        val weightSum = weights.sum()

        val noise = DoubleArray(n)
        for (i in 0 until n) {
            val t = i / fs
            var v = 0.0
            for (k in freqs.indices) {
                v += weights[k] * sin(2.0 * PI * freqs[k] * t + phases[k])
            }
            noise[i] = amp * v / weightSum
        }
        return noise
    }

    private fun motion(n: Int, fs: Double, refAmp: Double, intensity: Double, rng: Random): DoubleArray {
        val amp = 0.5 * refAmp * intensity
        val durationSec = n / fs
        val bumps = max(1, round(durationSec * 0.35).toInt())

        val noise = DoubleArray(n)
        for (b in 0 until bumps) {
            val center = rng.nextInt(n)
            val widthSec = 0.2 + rng.nextDouble() * 0.4
            val widthSamples = widthSec * fs
            val sign = if (rng.nextDouble() < 0.5) -1.0 else 1.0
            val mag = amp * (0.5 + rng.nextDouble() * 0.5)
            val span = (widthSamples * 3.0).toInt()
            for (i in max(0, center - span) until min(n, center + span)) {
                val d = (i - center) / widthSamples
                noise[i] += sign * mag * exp(-0.5 * d * d)
            }
        }
        return noise
    }

    private fun contact(n: Int, fs: Double, refAmp: Double, intensity: Double, rng: Random): DoubleArray {
        val amp = 0.6 * refAmp * intensity
        val durationSec = n / fs
        val pops = max(1, round(durationSec * 0.8).toInt())
        val tau = 0.04 * fs

        val noise = DoubleArray(n)
        for (p in 0 until pops) {
            val pos = rng.nextInt(n)
            val sign = if (rng.nextDouble() < 0.5) -1.0 else 1.0
            val mag = amp * (0.5 + rng.nextDouble() * 0.5)
            val span = (tau * 6.0).toInt()
            for (i in pos until min(n, pos + span)) {
                noise[i] += sign * mag * exp(-(i - pos) / tau)
            }
        }
        return noise
    }

    private fun whiteNoise(n: Int, rng: Random): DoubleArray {
        val w = DoubleArray(n)
        for (i in 0 until n) {
            val u1 = 1.0 - rng.nextDouble()
            val u2 = 1.0 - rng.nextDouble()
            w[i] = sqrt(-2.0 * ln(u1)) * cos(TAU * u2)
        }
        return w
    }

    fun peakToPeak(signal: DoubleArray): Double {
        if (signal.isEmpty()) return 0.0
        var minVal = Double.MAX_VALUE
        var maxVal = Double.MIN_VALUE
        for (v in signal) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        return maxVal - minVal
    }

    private fun scaleToStd(x: DoubleArray, targetStd: Double): DoubleArray {
        val n = x.size
        if (n == 0 || targetStd <= 0) return DoubleArray(n)
        var mean = 0.0
        for (v in x) mean += v
        mean /= n
        var variance = 0.0
        for (v in x) {
            val d = v - mean
            variance += d * d
        }
        variance /= n
        val std = sqrt(variance)
        if (std < 1e-12) return DoubleArray(n)
        val scale = targetStd / std
        val result = DoubleArray(n)
        for (i in 0 until n) result[i] = (x[i] - mean) * scale
        return result
    }
}
