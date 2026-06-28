package com.example.cardiosimulator.signals.biosppy

import kotlin.math.*

object QrsSegmenters {

    fun findExtrema(signal: DoubleArray, mode: String = "both"): Pair<IntArray, DoubleArray> {
        if (signal.size < 3) return Pair(IntArray(0), DoubleArray(0))

        val n = signal.size
        val extremaList = mutableListOf<Int>()
        var lastSign = (signal[1] - signal[0]).sign.toInt()

        for (i in 1 until n - 1) {
            val currentSign = (signal[i + 1] - signal[i]).sign.toInt()
            val diffSign = currentSign - lastSign
            lastSign = currentSign

            when (mode) {
                "both" -> if (abs(diffSign) > 0) extremaList.add(i)
                "max" -> if (diffSign < 0) extremaList.add(i)
                "min" -> if (diffSign > 0) extremaList.add(i)
            }
        }

        val extrema = extremaList.toIntArray()
        val values = DoubleArray(extrema.size) { i -> signal[extrema[i]] }
        return Pair(extrema, values)
    }

    fun correctRPeaks(signal: DoubleArray, rpeaks: IntArray, fs: Double, tolSec: Double = 0.05): IntArray {
        val toleranceSamples = (tolSec * fs).toInt()
        val length = signal.size
        val newR = mutableListOf<Int>()

        for (r in rpeaks) {
            val a = max(0, r - toleranceSamples)
            val b = min(length, r + toleranceSamples)

            var maxVal = Double.NEGATIVE_INFINITY
            var argmax = a
            for (i in a until b) {
                if (signal[i] > maxVal) {
                    maxVal = signal[i]
                    argmax = i
                }
            }
            newR.add(argmax)
        }

        return newR.distinct().sorted().toIntArray()
    }

    fun hamiltonSegmenter(signal: DoubleArray, samplingRate: Double = 1000.0): IntArray {
        val length = signal.size
        val duration = length / samplingRate

        val v1s = samplingRate.toInt()
        val thElapsed = ceil(0.36 * samplingRate).toInt()
        val smSize = (0.08 * samplingRate).toInt()
        var initEcg = 8
        if (duration < initEcg) initEcg = duration.toInt()
        if (initEcg < 1) initEcg = 1

        // Bandpass filter (25Hz lowpass, 3Hz highpass)
        val lpCoeff = Filter.butterworth(4, doubleArrayOf(2.0 * 25.0 / samplingRate), "lowpass")
        val filteredLp = Filter.filtfilt(lpCoeff.first, lpCoeff.second, signal)

        val hpCoeff = Filter.butterworth(4, doubleArrayOf(2.0 * 3.0 / samplingRate), "highpass")
        val filtered = Filter.filtfilt(hpCoeff.first, hpCoeff.second, filteredLp)

        // Absolute derivative scaled by sampling rate
        val dx = DoubleArray(length - 1) { i ->
            abs((filtered[i + 1] - filtered[i]) * samplingRate)
        }

        // Smooth
        val smoothedDx = Dsp.smoother(dx, "hamming", smSize, true)

        // Initialize buffers
        val qrsPeakBuffer = DoubleArray(initEcg)
        val noisePeakBuffer = DoubleArray(initEcg)
        val rrInterval = DoubleArray(initEcg) { samplingRate }

        var a = 0
        for (i in 0 until initEcg) {
            if (a >= smoothedDx.size) break
            val end = min(a + v1s, smoothedDx.size)
            val sub = smoothedDx.sliceArray(a until end)

            val (_, values) = findExtrema(sub, "max")
            if (values.isNotEmpty()) {
                qrsPeakBuffer[i] = values.maxOrNull() ?: 0.0
            }
            a += v1s
        }

        var anp = median(noisePeakBuffer)
        var aqrsp = median(qrsPeakBuffer)
        var dt = anp + 0.475 * (aqrsp - anp)

        val lim = ceil(0.2 * samplingRate).toInt()
        val diffNr = ceil(0.045 * samplingRate).toInt()

        val (allPeaks, _) = findExtrema(smoothedDx, "max")
        val beats = mutableListOf<Int>()
        var indexQrs = 0
        var indexNoise = 0
        var indexRr = 0

        for (f in allPeaks) {
            var skipPeak = false
            for (op in allPeaks) {
                if (op != f && op > f - lim && op < f + lim) {
                    if (smoothedDx[op] > smoothedDx[f]) {
                        skipPeak = true
                        break
                    }
                }
            }
            if (skipPeak) continue

            val elapsed = if (beats.isNotEmpty()) (f - beats.last()).toDouble() else 0.0

            if (smoothedDx[f] > dt) {
                val start = max(0, f - diffNr)
                val end = min(length, f + diffNr)
                val diffNow = DoubleArray(max(0, end - start - 1)) { i -> signal[start + i + 1] - signal[start + i] }
                val posCount = diffNow.count { it > 0 }

                if (posCount == 0 || posCount == diffNow.size) continue

                if (beats.isNotEmpty()) {
                    if (elapsed < thElapsed) {
                        val pStart = max(0, beats.last() - diffNr)
                        val pEnd = min(length, beats.last() + diffNr)
                        val diffPrev = DoubleArray(max(0, pEnd - pStart - 1)) { i -> signal[pStart + i + 1] - signal[pStart + i] }

                        val slopeNow = if (diffNow.isNotEmpty()) diffNow.maxOrNull() ?: 0.0 else 0.0
                        val slopePrev = if (diffPrev.isNotEmpty()) diffPrev.maxOrNull() ?: 0.0 else 0.0

                        if (slopeNow < 0.5 * slopePrev) continue // T-wave
                    }

                    if (smoothedDx[f] < 3.0 * median(qrsPeakBuffer)) {
                        beats.add(f)
                        rrInterval[indexRr] = (beats.last() - beats[beats.size - 2]).toDouble()
                        indexRr = (indexRr + 1) % initEcg
                    } else continue
                } else {
                    if (smoothedDx[f] < 3.0 * median(qrsPeakBuffer)) {
                        beats.add(f)
                    } else continue
                }

                qrsPeakBuffer[indexQrs] = smoothedDx[f]
                indexQrs = (indexQrs + 1) % initEcg
            } else {
                val rrm = median(rrInterval)
                if (beats.size >= 2 && elapsed >= 1.5 * rrm && elapsed > thElapsed) {
                    if (smoothedDx[f] > 0.5 * dt) {
                        beats.add(f)
                        rrInterval[indexRr] = (beats.last() - beats[beats.size - 2]).toDouble()
                        indexRr = (indexRr + 1) % initEcg

                        qrsPeakBuffer[indexQrs] = smoothedDx[f]
                        indexQrs = (indexQrs + 1) % initEcg
                    }
                } else {
                    noisePeakBuffer[indexNoise] = smoothedDx[f]
                    indexNoise = (indexNoise + 1) % initEcg
                }
            }

            anp = median(noisePeakBuffer)
            aqrsp = median(qrsPeakBuffer)
            dt = anp + 0.475 * (aqrsp - anp)
        }

        val rBeats = mutableListOf<Int>()
        val adjacency = 0.05 * samplingRate
        val thresCh = 0.85

        for (i in beats) {
            val start = max(0, i - lim)
            val end = min(length, i + lim)
            val window = signal.sliceArray(start until end)

            val (wPeaks, _) = findExtrema(window, "max")
            val (wNegPeaks, _) = findExtrema(window, "min")

            val peakIndices = wPeaks.toMutableList()
            val negPeakIndices = wNegPeaks.toMutableList()

            for (k in 0 until window.size - 1) {
                if (window[k + 1] - window[k] == 0.0) {
                    peakIndices.add(k)
                    negPeakIndices.add(k)
                }
            }

            val posPeaks = peakIndices.map { idx -> Pair(window[idx], idx) }.sortedByDescending { it.first }
            val negPeaks = negPeakIndices.map { idx -> Pair(window[idx], idx) }.sortedBy { it.first }

            var errPos = posPeaks.isEmpty()
            var errNeg = negPeaks.isEmpty()

            val twoPeaks = mutableListOf<Pair<Double, Int>>()
            if (!errPos) {
                twoPeaks.add(posPeaks[0])
                for (k in 1 until posPeaks.size) {
                    if (abs(posPeaks[0].second - posPeaks[k].second) > adjacency) {
                        twoPeaks.add(posPeaks[k])
                        break
                    }
                }
            }

            val twoNegPeaks = mutableListOf<Pair<Double, Int>>()
            if (!errNeg) {
                twoNegPeaks.add(negPeaks[0])
                for (k in 1 until negPeaks.size) {
                    if (abs(negPeaks[0].second - negPeaks[k].second) > adjacency) {
                        twoNegPeaks.add(negPeaks[k])
                        break
                    }
                }
            }

            val posDiv = if (twoPeaks.size >= 2) abs(twoPeaks[0].first - twoPeaks[1].first) else { errPos = true; 0.0 }
            val negDiv = if (twoNegPeaks.size >= 2) abs(twoNegPeaks[0].first - twoNegPeaks[1].first) else { errNeg = true; 0.0 }

            var finalOffset = 0
            if (!errPos && !errNeg) {
                finalOffset = if (posDiv > thresCh * negDiv) twoPeaks[0].second else twoNegPeaks[0].second
            } else if (errPos && errNeg) {
                if (twoPeaks.isNotEmpty() && twoNegPeaks.isNotEmpty()) {
                    finalOffset = if (abs(twoPeaks[0].first) > abs(twoNegPeaks[0].first)) twoPeaks[0].second else twoNegPeaks[0].second
                } else if (twoPeaks.isNotEmpty()) finalOffset = twoPeaks[0].second
                else if (twoNegPeaks.isNotEmpty()) finalOffset = twoNegPeaks[0].second
            } else if (errPos) {
                if (twoPeaks.isNotEmpty()) finalOffset = twoPeaks[0].second
            } else {
                if (twoNegPeaks.isNotEmpty()) finalOffset = twoNegPeaks[0].second
            }

            rBeats.add(finalOffset + start)
        }

        return rBeats.distinct().sorted().toIntArray()
    }

    fun ssfSegmenter(signal: DoubleArray, samplingRate: Double = 1000.0, threshold: Double = 20.0, before: Double = 0.03, after: Double = 0.01): IntArray {
        val length = signal.size
        val winB = (before * samplingRate).toInt()
        val winA = (after * samplingRate).toInt()

        val dx = DoubleArray(length - 1) { i ->
            val diff = signal[i + 1] - signal[i]
            if (diff >= 0) 0.0 else diff * diff
        }

        val idxList = mutableListOf<Int>()
        for (i in dx.indices) {
            if (dx[i] > threshold) idxList.add(i)
        }

        val rSet = mutableSetOf<Int>()
        for (k in idxList.indices) {
            val prev = if (k == 0) 0 else idxList[k - 1]
            if (k == 0 && idxList[0] <= 1) continue
            if (k > 0 && idxList[k] - prev <= 1) continue

            val item = idxList[k]
            val a = max(0, item - winB)
            val b = min(length, item + winA)

            var maxVal = Double.NEGATIVE_INFINITY
            var r = a
            for (i in a until b) {
                if (signal[i] > maxVal) {
                    maxVal = signal[i]
                    r = i
                }
            }
            rSet.add(r)
        }

        return rSet.sorted().toIntArray()
    }

    private fun median(arr: DoubleArray): Double {
        if (arr.isEmpty()) return 0.0
        val sorted = arr.sortedArray()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }
}
