package com.example.cardiosimulator.signals.biosppy

import kotlin.math.*

data class EcgLandmarks(
    val rPeak: Int,
    val qPeak: Int,
    val qrsStart: Int,
    val sPeak: Int,
    val qrsEnd: Int,
    val pPeak: Int,
    val pStart: Int,
    val pEnd: Int,
    val tPeak: Int,
    val tStart: Int,
    val tEnd: Int
)

object Landmarks {
    fun argRelExtrema(array: DoubleArray, findMin: Boolean): IntArray {
        if (array.size < 3) return IntArray(0)
        val list = mutableListOf<Int>()
        for (i in 1 until array.size - 1) {
            if (findMin) {
                if (array[i] < array[i - 1] && array[i] < array[i + 1])
                    list.add(i)
            } else {
                if (array[i] > array[i - 1] && array[i] > array[i + 1])
                    list.add(i)
            }
        }
        return list.toIntArray()
    }

    private fun argMin(array: DoubleArray): Int {
        if (array.isEmpty()) return 0
        var minVal = array[0]
        var minIdx = 0
        for (i in 1 until array.size) {
            if (array[i] < minVal) {
                minVal = array[i]
                minIdx = i
            }
        }
        return minIdx
    }

    private fun argMax(array: DoubleArray): Int {
        if (array.isEmpty()) return 0
        var maxVal = array[0]
        var maxIdx = 0
        for (i in 1 until array.size) {
            if (array[i] > maxVal) {
                maxVal = array[i]
                maxIdx = i
            }
        }
        return maxIdx
    }

    fun getQPositions(templates: List<DoubleArray>, rpeaks: IntArray, fs: Double, before: Double): Pair<IntArray, IntArray> {
        val nTemplates = templates.size
        val qPositions = IntArray(nTemplates)
        val qStartPositions = IntArray(nTemplates)
        val templateRPosition = (before * fs).toInt()

        for (n in 0 until nTemplates) {
            val each = templates[n]
            val templateLeft = each.sliceArray(0..templateRPosition)
            val minIndices = argRelExtrema(templateLeft, true)
            val qIdx = if (minIndices.isNotEmpty()) minIndices.last() else argMin(templateLeft)
            qPositions[n] = rpeaks[n] - (templateRPosition - qIdx)

            val templateQLeft = each.sliceArray(0..qIdx)
            val maxIndices = argRelExtrema(templateQLeft, false)
            val qStartIdx = if (maxIndices.isNotEmpty()) maxIndices.last() else argMax(templateQLeft)
            qStartPositions[n] = rpeaks[n] - templateRPosition + qStartIdx
        }
        return Pair(qPositions, qStartPositions)
    }

    fun getSPositions(templates: List<DoubleArray>, rpeaks: IntArray, fs: Double, before: Double): Pair<IntArray, IntArray> {
        val nTemplates = templates.size
        val sPositions = IntArray(nTemplates)
        val sEndPositions = IntArray(nTemplates)
        val templateRPosition = (before * fs).toInt()

        for (n in 0 until nTemplates) {
            val each = templates[n]
            val templateRight = each.sliceArray(templateRPosition until each.size)
            val minIndices = argRelExtrema(templateRight, true)
            val sIdxRel = if (minIndices.isNotEmpty()) minIndices.first() else argMin(templateRight)
            sPositions[n] = rpeaks[n] + sIdxRel

            val maxIndices = argRelExtrema(templateRight, false)
            val sEndIdxRel = if (maxIndices.isNotEmpty()) maxIndices.first() else argMax(templateRight)
            sEndPositions[n] = rpeaks[n] + sEndIdxRel
        }
        return Pair(sPositions, sEndPositions)
    }

    fun getPPositions(templates: List<DoubleArray>, rpeaks: IntArray, fs: Double, before: Double): Triple<IntArray, IntArray, IntArray> {
        val nTemplates = templates.size
        val pPositions = IntArray(nTemplates)
        val pStartPositions = IntArray(nTemplates)
        val pEndPositions = IntArray(nTemplates)
        val templateRPosition = (before * fs).toInt()
        val templatePPositionMax = ( (before - 0.02) * fs).toInt()

        for (n in 0 until nTemplates) {
            val each = templates[n]
            val templateLeft = each.sliceArray(0..templatePPositionMax)
            val pIdx = argMax(templateLeft)
            pPositions[n] = rpeaks[n] - templateRPosition + pIdx

            val templatePLeft = each.sliceArray(0..pIdx)
            val minIndicesLeft = argRelExtrema(templatePLeft, true)
            val pStartIdx = if (minIndicesLeft.isNotEmpty()) minIndicesLeft.last() else argMin(templatePLeft)
            pStartPositions[n] = rpeaks[n] - templateRPosition + pStartIdx

            val templatePRight = each.sliceArray(pIdx..templatePPositionMax)
            val minIndicesRight = argRelExtrema(templatePRight, true)
            val pEndIdxRel = if (minIndicesRight.isNotEmpty()) minIndicesRight.first() else argMin(templatePRight)
            pEndPositions[n] = rpeaks[n] - templateRPosition + pIdx + pEndIdxRel
        }
        return Triple(pPositions, pStartPositions, pEndPositions)
    }

    fun getTPositions(templates: List<DoubleArray>, rpeaks: IntArray, fs: Double, before: Double): Triple<IntArray, IntArray, IntArray> {
        val nTemplates = templates.size
        val tPositions = IntArray(nTemplates)
        val tStartPositions = IntArray(nTemplates)
        val tEndPositions = IntArray(nTemplates)
        val templateRPosition = (before * fs).toInt()
        val templateTPositionMin = ( (before + 0.07) * fs).toInt()

        for (n in 0 until nTemplates) {
            val each = templates[n]
            val templateRight = each.sliceArray(templateTPositionMin until each.size)
            val tIdxRel = argMax(templateRight)
            tPositions[n] = rpeaks[n] - templateRPosition + templateTPositionMin + tIdxRel

            val templateTLeft = each.sliceArray(templateRPosition until templateTPositionMin + tIdxRel)
            val minIndicesLeft = argRelExtrema(templateTLeft, true)
            val tStartIdxRel = if (minIndicesLeft.isNotEmpty()) minIndicesLeft.last() else argMin(templateTLeft)
            tStartPositions[n] = rpeaks[n] + tStartIdxRel

            val templateTRight = each.sliceArray(templateTPositionMin + tIdxRel until each.size)
            val minIndicesRight = argRelExtrema(templateTRight, true)
            val tEndIdxRel = if (minIndicesRight.isNotEmpty()) minIndicesRight.first() else argMin(templateTRight)
            tEndPositions[n] = rpeaks[n] - templateRPosition + templateTPositionMin + tIdxRel + tEndIdxRel
        }
        return Triple(tPositions, tStartPositions, tEndPositions)
    }

    fun extractHeartbeats(signal: DoubleArray, rpeaks: IntArray, fs: Double, before: Double = 0.2, after: Double = 0.4): Pair<List<DoubleArray>, IntArray> {
        val beforeSamples = (before * fs).toInt()
        val afterSamples = (after * fs).toInt()
        val winSize = beforeSamples + afterSamples
        val templates = mutableListOf<DoubleArray>()
        val validR = mutableListOf<Int>()
        val sortedR = rpeaks.sortedArray()

        for (r in sortedR) {
            val a = r - beforeSamples
            val b = r + afterSamples
            if (a < 0) continue
            if (b > signal.size) break
            templates.add(signal.sliceArray(a until b))
            validR.add(r)
        }
        return Pair(templates, validR.toIntArray())
    }

    fun getLandmarks(signal: DoubleArray, rpeaks: IntArray, fs: Double): List<EcgLandmarks> {
        // Plan: 200 ms before and 400 ms after R-peaks.
        val before = 0.2
        val after = 0.4
        val (templates, validR) = extractHeartbeats(signal, rpeaks, fs, before, after)
        
        val list = mutableListOf<EcgLandmarks>()
        if (validR.isEmpty()) return list
        
        val qPos = getQPositions(templates, validR, fs, before)
        val sPos = getSPositions(templates, validR, fs, before)
        val pPos = getPPositions(templates, validR, fs, before)
        val tPos = getTPositions(templates, validR, fs, before)
        
        for (i in validR.indices) {
            list.add(EcgLandmarks(
                rPeak = validR[i],
                qPeak = qPos.first[i],
                qrsStart = qPos.second[i],
                sPeak = sPos.first[i],
                qrsEnd = sPos.second[i],
                pPeak = pPos.first[i],
                pStart = pPos.second[i],
                pEnd = pPos.third[i],
                tPeak = tPos.first[i],
                tStart = tPos.second[i],
                tEnd = tPos.third[i]
            ))
        }
        return list
    }
}
