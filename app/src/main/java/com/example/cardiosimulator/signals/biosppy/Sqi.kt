package com.example.cardiosimulator.signals.biosppy

import kotlin.math.*

object Sqi {
    fun bsqi(detector1: IntArray, detector2: IntArray, fs: Double = 1000.0, mode: String = "simple", searchWindowMs: Double = 150.0): Double {
        if (detector1.isEmpty() || detector2.isEmpty()) return 0.0

        val searchWindow = (searchWindowMs / 1000.0 * fs).toInt()
        var both = 0
        val det2Set = detector2.toSet()

        for (i in detector1) {
            val start = max(0, i - searchWindow)
            val end = i + searchWindow
            for (j in start until end) {
                if (det2Set.contains(j)) {
                    both++
                    break
                }
            }
        }

        return when (mode.lowercase()) {
            "simple" -> (both.toDouble() / detector1.size) * 100.0
            "matching" -> (2.0 * both) / (detector1.size + detector2.size)
            "n_double" -> both.toDouble() / (detector1.size + detector2.size - both)
            else -> 0.0
        }
    }

    fun ssqi(signal: DoubleArray): Double {
        if (signal.isEmpty()) return 0.0
        val mean = signal.average()
        var m2 = 0.0
        var m3 = 0.0
        for (x in signal) {
            val diff = x - mean
            m2 += diff * diff
            m3 += diff * diff * diff
        }
        m2 /= signal.size
        m3 /= signal.size
        if (m2 < 1e-15) return 0.0
        return m3 / m2.pow(1.5)
    }

    fun ksqi(signal: DoubleArray, fisher: Boolean = true): Double {
        if (signal.isEmpty()) return 0.0
        val mean = signal.average()
        var m2 = 0.0
        var m4 = 0.0
        for (x in signal) {
            val diff = x - mean
            val diffSq = diff * diff
            m2 += diffSq
            m4 += diffSq * diffSq
        }
        m2 /= signal.size
        m4 /= signal.size
        if (m2 < 1e-15) return 0.0
        val kurt = m4 / (m2 * m2)
        return if (fisher) kurt - 3.0 else kurt
    }

    fun psqi(signal: DoubleArray, fThr: Double = 0.01): Double {
        if (signal.size < 2) return 0.0
        var flatlineCount = 0
        val diffLength = signal.size - 1
        for (i in 0 until diffLength) {
            if (abs(signal[i + 1] - signal[i]) < fThr) {
                flatlineCount++
            }
        }
        return (flatlineCount.toDouble() / diffLength) * 100.0
    }

    fun fsqi(ecgSignal: DoubleArray, fs: Double = 1000.0, nseg: Int = 1024, numSpectrum: DoubleArray? = null, demSpectrum: DoubleArray? = null, mode: String = "simple"): Double {
        val mNumSpectrum = numSpectrum ?: doubleArrayOf(5.0, 20.0)
        val (f, pxxDen) = Dsp.welch(ecgSignal, fs, nseg)
        if (f.isEmpty()) return 0.0

        fun powerInRange(range: DoubleArray): Double {
            val ySub = mutableListOf<Double>()
            val xSub = mutableListOf<Double>()
            for (i in f.indices) {
                if (f[i] in range[0]..range[1]) {
                    ySub.add(pxxDen[i])
                    xSub.add(f[i])
                }
            }
            return Dsp.integrateTrapz(ySub.toDoubleArray(), xSub.toDoubleArray())
        }

        val numPower = powerInRange(mNumSpectrum)
        val demPower = if (demSpectrum == null) powerInRange(doubleArrayOf(0.0, fs / 2.0)) else powerInRange(demSpectrum)

        if (abs(demPower) < 1e-15) return 0.0
        val ratio = numPower / demPower
        return if (mode.equals("bas", ignoreCase = true)) 1.0 - ratio else ratio
    }

    fun zz2018(signal: DoubleArray, detector1: IntArray, detector2: IntArray, fs: Double = 1000.0, searchWindowMs: Double = 100.0, nseg: Int = 1024, mode: String = "simple"): String {
        if (detector1.isEmpty() || detector2.isEmpty()) return "Unacceptable"

        val qsqi = bsqi(detector1, detector2, fs, "matching", searchWindowMs)
        val psqi = fsqi(signal, fs, nseg, doubleArrayOf(5.0, 15.0), doubleArrayOf(5.0, 40.0))
        val ksqiVal = ksqi(signal)
        val bassqi = fsqi(signal, fs, nseg, doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0, 40.0), "bas")

        if (mode.equals("simple", ignoreCase = true)) {
            val qsqiClass = when {
                qsqi > 0.90 -> 2
                qsqi < 0.60 -> 0
                else -> 1
            }

            var rrMax = 1.0
            if (detector1.size > 1) {
                var minDiff = Double.MAX_VALUE
                for (i in 0 until detector1.size - 1) {
                    val diff = (detector1[i + 1] - detector1[i]).toDouble()
                    if (diff < minDiff) minDiff = diff
                }
                rrMax = 60000.0 / ( (1000.0 / fs) * minDiff)
            }

            val l1: Double
            val l2: Double
            val l3: Double
            if (rrMax < 130.0) {
                l1 = 0.5; l2 = 0.8; l3 = 0.4
            } else {
                l1 = 0.4; l2 = 0.7; l3 = 0.3
            }

            val pSqiClass = when {
                psqi in l1..l2 -> 2
                psqi in l3..l1 -> 1
                else -> 0
            }

            val kSqiClass = if (ksqiVal > 5.0) 2 else 0

            val basSqiClass = when {
                bassqi >= 0.95 -> 2
                bassqi < 0.9 -> 0
                else -> 1
            }

            val classMatrix = intArrayOf(qsqiClass, pSqiClass, kSqiClass, basSqiClass)
            val nOptimal = classMatrix.count { it == 2 }
            val nSuspics = classMatrix.count { it == 1 }
            val nUnqualy = classMatrix.count { it == 0 }

            return when {
                nUnqualy >= 3 || (nUnqualy == 2 && nSuspics >= 1) || (nUnqualy == 1 && nSuspics == 3) -> "Unacceptable"
                nOptimal >= 3 && nUnqualy == 0 -> "Excellent"
                else -> "Barely acceptable"
            }
        } else if (mode.equals("fuzzy", ignoreCase = true)) {
            val qsqiScaled = qsqi * 100.0
            val uqH = when {
                qsqiScaled <= 80.0 -> 0.0
                qsqiScaled >= 90.0 -> qsqiScaled / 100.0
                else -> 1.0 / (1.0 + (1.0 / (0.3 * (qsqiScaled - 80.0)).pow(2.0)))
            }
            val uqI = 1.0 / (1.0 + ((qsqiScaled - 75.0) / 7.5).pow(2.0))
            val uqJ = if (qsqiScaled <= 55.0) 1.0 else 1.0 / (1.0 + ((qsqiScaled - 55.0) / 5.0).pow(2.0))
            val r1 = doubleArrayOf(uqH, uqI, uqJ)

            val upH = when {
                psqi <= 0.25 -> 0.0
                psqi >= 0.35 -> 1.0
                else -> 0.1 * (psqi - 0.25)
            }
            val upI = when {
                psqi < 0.18 || psqi >= 0.32 -> 0.0
                psqi < 0.22 -> 25.0 * (psqi - 0.18)
                psqi < 0.28 -> 1.0
                else -> 25.0 * (0.32 - psqi)
            }
            val upJ = when {
                psqi < 0.15 -> 1.0
                psqi > 0.25 -> 0.0
                else -> 0.1 * (0.25 - psqi)
            }
            val r2 = doubleArrayOf(upH, upI, upJ)

            val r3 = if (ksqiVal > 5.0) doubleArrayOf(1.0, 0.0, 0.0) else doubleArrayOf(0.0, 0.0, 1.0)

            val ubH = when {
                bassqi <= 90.0 -> 0.0
                bassqi >= 95.0 -> bassqi / 100.0
                else -> 1.0 / (1.0 + (1.0 / (0.8718 * (bassqi - 90.0)).pow(2.0)))
            }
            val ubI = if (bassqi <= 85.0) 1.0 else 1.0 / (1.0 + ((bassqi - 85.0) / 5.0).pow(2.0))
            val ubJ = 1.0 / (1.0 + ((bassqi - 95.0) / 2.5).pow(2.0))
            val r4 = doubleArrayOf(ubH, ubI, ubJ)

            val w = doubleArrayOf(0.4, 0.4, 0.1, 0.1)
            val s = DoubleArray(3) { col ->
                r1[col] * w[0] + r2[col] * w[1] + r3[col] * w[2] + r4[col] * w[3]
            }

            val sumSq = s[0] * s[0] + s[1] * s[1] + s[2] * s[2]
            var v = 1.0
            if (sumSq > 1e-15) {
                v = (s[0] * s[0] * 1.0 + s[1] * s[1] * 2.0 + s[2] * s[2] * 3.0) / sumSq
            }

            return when {
                v < 1.5 -> "Excellent"
                v >= 2.40 -> "Unacceptable"
                else -> "Barely acceptable"
            }
        }
        return "Unacceptable"
    }
}
