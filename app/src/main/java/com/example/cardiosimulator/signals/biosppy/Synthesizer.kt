package com.example.cardiosimulator.signals.biosppy

import java.util.Random
import kotlin.math.*

object Synthesizer {
    private val random = Random()

    private fun nextNormal(mean: Double, std: Double): Double {
        return mean + std * random.nextGaussian()
    }

    private fun clip(value: Double, min: Double, max: Double): Double {
        return if (value < min) min else if (value > max) max else value
    }

    private fun rangeList(start: Double, end: Double, step: Double): DoubleArray {
        val list = mutableListOf<Double>()
        var current = start
        while (current < end - 1e-9) {
            list.add(current)
            current += step
        }
        return list.toDoubleArray()
    }

    fun b(l: Double, kb: Int): DoubleArray {
        val size = (kb * l).toInt()
        return DoubleArray(size)
    }

    fun p(i: Double, ap: Double, kp: Int): DoubleArray {
        val k = rangeList(0.0, kp.toDouble(), i)
        return DoubleArray(k.size) { idx ->
            -(ap / 2.0) * cos((2.0 * PI * k[idx] + 15.0) / kp) + ap / 2.0
        }
    }

    fun pq(l: Double, kpq: Int): DoubleArray {
        val size = (kpq * l).toInt()
        return DoubleArray(size)
    }

    fun q1(i: Double, aq: Double, kq1: Int): DoubleArray {
        val k = rangeList(0.0, kq1.toDouble(), i)
        return DoubleArray(k.size) { idx -> -aq * (k[idx] / kq1) }
    }

    fun q2(i: Double, aq: Double, kq2: Int): DoubleArray {
        val k = rangeList(0.0, kq2.toDouble(), i)
        return DoubleArray(k.size) { idx -> aq * (k[idx] / kq2) - aq }
    }

    fun r(i: Double, ar: Double, kr: Int): DoubleArray {
        val k = rangeList(0.0, kr.toDouble(), i)
        return DoubleArray(k.size) { idx -> ar * sin((PI * k[idx]) / kr) }
    }

    fun sScalar(i: Double, `as`: Double, ks: Int, kcs: Int, k: Double): Double {
        return -`as` * i * k * (19.78 * PI) / ks * exp(-2.0 * ((6.0 * PI) / ks * i * k).pow(2.0))
    }

    fun s(i: Double, `as`: Double, ks: Int, kcs: Int): DoubleArray {
        val kRange = rangeList(0.0, (ks - kcs).toDouble(), i)
        return DoubleArray(kRange.size) { idx -> sScalar(i, `as`, ks, kcs, kRange[idx]) }
    }

    fun stScalar(i: Double, `as`: Double, ks: Int, kcs: Int, sm: Int, kst: Int, k: Double): Double {
        val sAtEnd = sScalar(i, `as`, ks, kcs, (ks - kcs).toDouble())
        return -sAtEnd * (k / sm) + sAtEnd
    }

    fun st(i: Double, `as`: Double, ks: Int, kcs: Int, sm: Int, kst: Int): DoubleArray {
        val kRange = rangeList(0.0, kst.toDouble(), i)
        return DoubleArray(kRange.size) { idx -> stScalar(i, `as`, ks, kcs, sm, kst, kRange[idx]) }
    }

    fun tScalar(i: Double, `as`: Double, ks: Int, kcs: Int, sm: Int, kst: Int, at: Double, kt: Int, k: Double): Double {
        val stAtEnd = stScalar(i, `as`, ks, kcs, sm, kst, kst.toDouble())
        return -at * cos((1.48 * PI * k + 15.0) / kt) + at + stAtEnd
    }

    fun t(i: Double, `as`: Double, ks: Int, kcs: Int, sm: Int, kst: Int, at: Double, kt: Int): DoubleArray {
        val kRange = rangeList(0.0, kt.toDouble(), i)
        return DoubleArray(kRange.size) { idx -> tScalar(i, `as`, ks, kcs, sm, kst, at, kt, kRange[idx]) }
    }

    fun iSeg(i: Double, `as`: Double, ks: Int, kcs: Int, sm: Int, kst: Int, at: Double, kt: Int, si: Int, ki: Int): DoubleArray {
        val tAtEnd = tScalar(i, `as`, ks, kcs, sm, kst, at, kt, kt.toDouble())
        val kRange = rangeList(0.0, ki.toDouble(), i)
        return DoubleArray(kRange.size) { idx -> tAtEnd * (si / (kRange[idx] + 10.0)) }
    }

    fun generate(
        kb: Int = 130,
        ap: Double = 0.2,
        kp: Int = 100,
        kpq: Int = 40,
        aq: Double = 0.1,
        kq1: Int = 25,
        kq2: Int = 5,
        ar: Double = 0.7,
        kr: Int = 40,
        `as`: Double = 0.2,
        ks: Int = 30,
        kcs: Int = 5,
        sm: Int = 96,
        kst: Int = 100,
        at: Double = 0.15,
        kt: Int = 220,
        si: Int = 2,
        ki: Int = 200,
        variance: Double = 0.01,
        samplingRate: Double = 10000.0
    ): Pair<DoubleArray, DoubleArray> {
        var mKb = kb
        var mAp = ap
        var mKp = kp
        var mKpq = kpq
        var mAq = aq
        var mKq1 = kq1
        var mKq2 = kq2
        var mAr = ar
        var mKr = kr
        var mAs = `as`
        var mKs = ks
        var mKcs = kcs
        var mSm = sm
        var mKst = kst
        var mAt = at
        var mKt = kt
        var mSi = si

        if (variance > 0.0) {
            mKb = nextNormal(kb.toDouble(), kb * variance).roundToInt().let { clip(it.toDouble(), 0.0, 130.0).toInt() }
            mAp = clip(nextNormal(ap, ap * variance), -0.2, 0.5)
            mKp = nextNormal(kp.toDouble(), kp * variance).roundToInt().let { clip(it.toDouble(), 10.0, 100.0).toInt() }
            mKpq = nextNormal(kpq.toDouble(), kpq * variance).roundToInt().let { clip(it.toDouble(), 0.0, 60.0).toInt() }
            mAq = clip(nextNormal(aq, aq * variance), 0.0, 0.5)
            mKq1 = nextNormal(kq1.toDouble(), kq1 * variance).roundToInt().let { clip(it.toDouble(), 0.0, 70.0).toInt() }
            mKq2 = nextNormal(kq2.toDouble(), kq2 * variance).roundToInt().let { clip(it.toDouble(), 0.0, 50.0).toInt() }
            mAr = clip(nextNormal(ar, ar * variance), 0.5, 2.0)
            mKr = nextNormal(kr.toDouble(), kr * variance).roundToInt().let { clip(it.toDouble(), 10.0, 150.0).toInt() }
            mAs = clip(nextNormal(`as`, `as` * variance), 0.0, 1.0)
            mKs = nextNormal(ks.toDouble(), ks * variance).roundToInt().let { clip(it.toDouble(), 10.0, 200.0).toInt() }
            mKcs = nextNormal(kcs.toDouble(), kcs * variance).roundToInt().let { clip(it.toDouble(), -5.0, 150.0).toInt() }
            mSm = nextNormal(sm.toDouble(), sm * variance).roundToInt().let { clip(it.toDouble(), 1.0, 150.0).toInt() }
            mKst = nextNormal(kst.toDouble(), kst * variance).roundToInt().let { clip(it.toDouble(), 0.0, 110.0).toInt() }
            mAt = clip(nextNormal(at, at * variance), -0.5, 1.0)
            mKt = nextNormal(kt.toDouble(), kt * variance).roundToInt().let { clip(it.toDouble(), 50.0, 300.0).toInt() }
            mSi = nextNormal(si.toDouble(), si * variance).roundToInt().let { clip(it.toDouble(), 0.0, 50.0).toInt() }
        }

        val i = 1000.0 / samplingRate
        val l = 1.0 / i

        val bToSList = mutableListOf<Double>()
        bToSList.addAll(b(l, mKb).toList())
        bToSList.addAll(p(i, mAp, mKp).toList())
        bToSList.addAll(pq(l, mKpq).toList())
        bToSList.addAll(q1(i, mAq, mKq1).toList())
        bToSList.addAll(q2(i, mAq, mKq2).toList())
        bToSList.addAll(r(i, mAr, mKr).toList())
        bToSList.addAll(s(i, mAs, mKs, mKcs).toList())

        val stToIList = mutableListOf<Double>()
        stToIList.addAll(st(i, mAs, mKs, mKcs, mSm, mKst).toList())
        stToIList.addAll(t(i, mAs, mKs, mKcs, mSm, mKst, mAt, mKt).toList())
        stToIList.addAll(iSeg(i, mAs, mKs, mKcs, mSm, mKst, mAt, mKt, mSi, ki).toList())

        val ecg1Filtered = Dsp.smoother(bToSList.toDoubleArray(), "boxzen", 50, true)
        val ecg2Filtered = Dsp.smoother(stToIList.toDoubleArray(), "boxzen", 500, true)

        val ecgWave = DoubleArray(ecg1Filtered.size + ecg2Filtered.size)
        ecg1Filtered.copyInto(ecgWave, 0)
        ecg2Filtered.copyInto(ecgWave, ecg1Filtered.size)

        val t = DoubleArray(ecgWave.size) { idx -> idx / samplingRate }

        return Pair(ecgWave, t)
    }
}
