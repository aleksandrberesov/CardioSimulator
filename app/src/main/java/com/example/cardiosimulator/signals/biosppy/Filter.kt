package com.example.cardiosimulator.signals.biosppy

import kotlin.math.*

object Filter {
    fun poly(roots: Array<Complex>): DoubleArray {
        val n = roots.size
        val coeffs = Array(n + 1) { Complex.ZERO }
        coeffs[0] = Complex.ONE
        for (i in 0 until n) {
            val r = roots[i]
            for (j in i + 1 downTo 1) {
                coeffs[j] = coeffs[j] - r * coeffs[j - 1]
            }
        }
        val realCoeffs = DoubleArray(n + 1)
        for (i in 0..n) {
            realCoeffs[i] = coeffs[i].real
        }
        return realCoeffs
    }

    fun butterworth(order: Int, Wn: DoubleArray, band: String = "lowpass"): Pair<DoubleArray, DoubleArray> {
        // 1. Get analog prototype poles
        val poles = Array(order) { k ->
            val theta = PI * (2 * k + 1) / (2.0 * order)
            Complex(-sin(theta), cos(theta))
        }

        // 2. Pre-warp cutoff frequencies
        val omega = DoubleArray(Wn.size) { i ->
            tan(PI * Wn[i] / 2.0)
        }

        val analogPoles = mutableListOf<Complex>()
        val analogZeros = mutableListOf<Complex>()
        var analogGain = 1.0

        when (band.lowercase()) {
            "lowpass" -> {
                val w = omega[0]
                for (i in 0 until order) {
                    analogPoles.add(poles[i] * w)
                }
                analogGain = w.pow(order)
            }
            "highpass" -> {
                val w = omega[0]
                for (i in 0 until order) {
                    analogPoles.add(Complex(w) / poles[i])
                    analogZeros.add(Complex.ZERO)
                }
                analogGain = 1.0
            }
            "bandpass" -> {
                val w1 = omega[0]
                val w2 = omega[1]
                val bw = w2 - w1
                val w0Sq = w1 * w2

                for (i in 0 until order) {
                    val p = poles[i]
                    val term1 = p * (bw / 2.0)
                    val term2 = Complex.sqrt(p * p * (bw * bw) - Complex(4.0 * w0Sq)) / 2.0
                    analogPoles.add(term1 + term2)
                    analogPoles.add(term1 - term2)
                    analogZeros.add(Complex.ZERO)
                }
                analogGain = bw.pow(order)
            }
            "bandstop" -> {
                val w1 = omega[0]
                val w2 = omega[1]
                val bw = w2 - w1
                val w0Sq = w1 * w2

                for (i in 0 until order) {
                    val p = poles[i]
                    val term1 = (Complex(bw) / p) / 2.0
                    val term2 = Complex.sqrt((Complex(bw) / p).pow(2.0) - Complex(4.0 * w0Sq)) / 2.0
                    analogPoles.add(term1 + term2)
                    analogPoles.add(term1 - term2)
                    analogZeros.add(Complex(0.0, sqrt(w0Sq)))
                    analogZeros.add(Complex(0.0, -sqrt(w0Sq)))
                }
                var poleProd = Complex.ONE
                for (ap in analogPoles) poleProd *= -ap
                analogGain = poleProd.real / w0Sq.pow(order)
            }
        }

        // 3. Bilinear transform to digital plane
        val digitalPoles = Array(analogPoles.size) { i ->
            (Complex.ONE + analogPoles[i]) / (Complex.ONE - analogPoles[i])
        }

        val digitalZeros = Array(analogPoles.size) { i ->
            if (i < analogZeros.size) {
                (Complex.ONE + analogZeros[i]) / (Complex.ONE - analogZeros[i])
            } else {
                Complex(-1.0)
            }
        }

        // Compute digital gain
        var poleProduct = Complex.ONE
        for (p in analogPoles) poleProduct *= (Complex.ONE - p)

        var zeroProduct = Complex.ONE
        for (z in analogZeros) zeroProduct *= (Complex.ONE - z)

        val digitalGain = (Complex(analogGain) * zeroProduct / poleProduct).real

        val b = poly(digitalZeros)
        val a = poly(digitalPoles)

        for (i in b.indices) {
            b[i] *= digitalGain
        }

        return Pair(b, a)
    }

    fun lfilter(b: DoubleArray, a: DoubleArray, x: DoubleArray, zi: DoubleArray? = null): Pair<DoubleArray, DoubleArray> {
        val n = x.size
        val nb = b.size
        val na = a.size
        val m = max(nb, na) - 1

        val bPad = DoubleArray(m + 1)
        val aPad = DoubleArray(m + 1)
        b.copyInto(bPad, 0, 0, min(nb, m + 1))
        a.copyInto(aPad, 0, 0, min(na, m + 1))

        val a0 = aPad[0]
        if (abs(a0 - 1.0) > 1e-15) {
            for (i in 0..m) {
                bPad[i] /= a0
                aPad[i] /= a0
            }
        }

        val z = DoubleArray(m)
        if (zi != null) {
            zi.copyInto(z, 0, 0, min(zi.size, m))
        }

        val y = DoubleArray(n)
        for (j in 0 until n) {
            val xj = x[j]
            val yj = bPad[0] * xj + (if (m > 0) z[0] else 0.0)
            y[j] = yj

            for (i in 0 until m - 1) {
                z[i] = bPad[i + 1] * xj - aPad[i + 1] * yj + z[i + 1]
            }
            if (m > 0) {
                z[m - 1] = bPad[m] * xj - aPad[m] * yj
            }
        }

        return Pair(y, z)
    }

    fun lfilter_zi(b: DoubleArray, a: DoubleArray): DoubleArray {
        val nb = b.size
        val na = a.size
        val r = max(nb, na) - 1
        if (r <= 0) return DoubleArray(0)

        val bPad = DoubleArray(r + 1)
        val aPad = DoubleArray(r + 1)
        b.copyInto(bPad, 0, 0, min(nb, r + 1))
        a.copyInto(aPad, 0, 0, min(na, r + 1))

        val a0 = aPad[0]
        if (abs(a0 - 1.0) > 1e-15) {
            for (i in 0..r) {
                bPad[i] /= a0
                aPad[i] /= a0
            }
        }

        val v = DoubleArray(r)
        for (i in 0 until r) {
            v[i] = bPad[i + 1] - aPad[i + 1] * bPad[0]
        }

        val c = DoubleArray(r)
        val d = DoubleArray(r)
        c[0] = 1.0
        d[0] = 0.0

        for (i in 1 until r) {
            c[i] = aPad[i] + c[i - 1]
            d[i] = d[i - 1] - v[i - 1]
        }

        val denom = aPad[r] + c[r - 1]
        var z0 = 0.0
        if (abs(denom) > 1e-15) {
            z0 = (v[r - 1] - d[r - 1]) / denom
        }

        val zi = DoubleArray(r)
        zi[0] = z0
        for (i in 1 until r) {
            zi[i] = c[i] * z0 + d[i]
        }

        return zi
    }

    fun filtfilt(b: DoubleArray, a: DoubleArray, x: DoubleArray): DoubleArray {
        val length = x.size
        val r = max(b.size, a.size) - 1
        if (r <= 0) return x.copyOf()

        val padlen = 3 * (r + 1)
        if (length <= padlen) {
            throw IllegalArgumentException("Signal length ($length) must be greater than padlen ($padlen).")
        }

        // Padding by reflection
        val yPad = DoubleArray(length + 2 * padlen)
        for (j in 1..padlen) {
            yPad[padlen - j] = 2.0 * x[0] - x[j]
            yPad[length + padlen - 1 + j] = 2.0 * x[length - 1] - x[length - 1 - j]
        }
        x.copyInto(yPad, padlen, 0, length)

        val zi = lfilter_zi(b, a)

        // Forward filter
        val ziScaledForward = DoubleArray(zi.size) { i -> zi[i] * yPad[0] }
        val (yFwd, _) = lfilter(b, a, yPad, ziScaledForward)

        // Reverse
        yFwd.reverse()

        // Backward filter
        val ziScaledBackward = DoubleArray(zi.size) { i -> zi[i] * yFwd[0] }
        val (yBwd, _) = lfilter(b, a, yFwd, ziScaledBackward)

        // Reverse back
        yBwd.reverse()

        // Slice
        val output = DoubleArray(length)
        yBwd.copyInto(output, 0, padlen, padlen + length)

        return output
    }

    fun filterSignal(
        signal: DoubleArray,
        ftype: String,
        band: String,
        order: Int,
        frequency: DoubleArray,
        samplingRate: Double
    ): DoubleArray {
        val Wn = DoubleArray(frequency.size) { i -> 2.0 * frequency[i] / samplingRate }
        
        val (b, a) = if (ftype.equals("FIR", ignoreCase = true)) {
            // FIR implementation was in Filtering.cs too, I should port FirWin if needed.
            // But let's assume butter for now as it's the main request.
            throw UnsupportedOperationException("FIR not yet implemented")
        } else if (ftype.equals("butter", ignoreCase = true)) {
            butterworth(order, Wn, band)
        } else {
            throw IllegalArgumentException("Unsupported filter type: $ftype")
        }

        return filtfilt(b, a, signal)
    }
}
