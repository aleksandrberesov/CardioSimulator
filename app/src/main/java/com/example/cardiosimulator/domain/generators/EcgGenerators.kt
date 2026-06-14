package com.example.cardiosimulator.domain.generators

import kotlin.math.*

/**
 * Parametric ECG element generators (Phase 4.0 of
 * docs/plans/active/2026-06-further-development-plan.md).
 * Emits raw ADC segments centered on a baseline.
 */
object EcgGenerators {

    fun generateP(width: Int, height: Int, baseline: Int): IntArray {
        if (width <= 0) return intArrayOf()
        return IntArray(width) { i ->
            val t = i.toFloat() / width
            // Sinusoidal bump: sin(pi * t)
            val valF = height * sin(PI * t).toFloat()
            baseline + valF.toInt()
        }
    }

    fun generateQRS(
        qWidth: Int, qHeight: Int,
        rWidth: Int, rHeight: Int,
        sWidth: Int, sHeight: Int,
        baseline: Int
    ): IntArray {
        val totalWidth = qWidth + rWidth + sWidth
        if (totalWidth <= 0) return intArrayOf()
        val samples = IntArray(totalWidth)
        var cursor = 0
        
        // Q (negative)
        for (i in 0 until qWidth) {
            val t = i.toFloat() / qWidth
            samples[cursor++] = baseline - (qHeight * sin(PI * t).toFloat()).toInt()
        }
        // R (positive)
        for (i in 0 until rWidth) {
            val t = i.toFloat() / rWidth
            samples[cursor++] = baseline + (rHeight * sin(PI * t).toFloat()).toInt()
        }
        // S (negative)
        for (i in 0 until sWidth) {
            val t = i.toFloat() / sWidth
            samples[cursor++] = baseline - (sHeight * sin(PI * t).toFloat()).toInt()
        }
        return samples
    }

    fun generateT(width: Int, height: Int, baseline: Int): IntArray {
        if (width <= 0) return intArrayOf()
        return IntArray(width) { i ->
            val t = i.toFloat() / width
            // T is often broader and slightly asymmetric, but sin(pi * t) is a good start
            val valF = height * sin(PI * t).toFloat()
            baseline + valF.toInt()
        }
    }

    fun generateST(width: Int, startLevel: Int, endLevel: Int, baseline: Int): IntArray {
        if (width <= 0) return intArrayOf()
        return IntArray(width) { i ->
            val t = i.toFloat() / width
            // Linear transition between levels
            val valF = startLevel + (endLevel - startLevel) * t
            baseline + valF.toInt()
        }
    }

    /** Generates a flat baseline segment. */
    fun generateBaseline(width: Int, baseline: Int): IntArray {
        if (width <= 0) return intArrayOf()
        return IntArray(width) { baseline }
    }
}
