package com.example.cardiosimulator.signals.biosppy

import kotlin.math.*

object Dsp {
    fun getWindow(kernel: String, size: Int): DoubleArray {
        val w = DoubleArray(size)
        if (size <= 0) return w
        if (size == 1) {
            w[0] = 1.0
            return w
        }

        when (kernel.lowercase()) {
            "boxcar", "box", "ones", "rect", "rectangular" -> {
                for (i in 0 until size) w[i] = 1.0
            }
            "hamming", "hamm", "ham" -> {
                for (i in 0 until size) {
                    w[i] = 0.54 - 0.46 * cos(2.0 * PI * i / (size - 1))
                }
            }
            "hanning", "hann", "han" -> {
                for (i in 0 until size) {
                    w[i] = 0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))
                }
            }
            "parzen", "parz", "par" -> {
                for (i in 0 until size) {
                    val t = i - (size - 1) / 2.0
                    val absT = abs(t)
                    if (absT <= size / 4.0) {
                        val ratio = absT / (size / 2.0)
                        w[i] = 1.0 - 6.0 * ratio * ratio * (1.0 - ratio)
                    } else if (absT <= size / 2.0) {
                        val ratio = absT / (size / 2.0)
                        w[i] = 2.0 * (1.0 - ratio).pow(3.0)
                    } else {
                        w[i] = 0.0
                    }
                }
            }
            else -> throw IllegalArgumentException("Unsupported window kernel: $kernel")
        }

        // Normalize sum to 1.0
        val sum = w.sum()
        if (sum > 0) {
            for (i in 0 until size) w[i] /= sum
        }
        return w
    }

    fun convolveSame(a: DoubleArray, v: DoubleArray): DoubleArray {
        val na = a.size
        val nv = v.size
        val output = DoubleArray(na)
        val start = (nv - 1) / 2

        for (n in 0 until na) {
            var sum = 0.0
            for (k in 0 until nv) {
                val idx = n + start - k
                if (idx >= 0 && idx < na) {
                    sum += a[idx] * v[k]
                }
            }
            output[n] = sum
        }
        return output
    }

    fun smoother(signal: DoubleArray, kernel: String = "boxzen", size: Int = 10, mirror: Boolean = true): DoubleArray {
        if (signal.isEmpty()) return DoubleArray(0)

        var mSize = size
        if (mSize > signal.size) mSize = signal.size - 1
        if (mSize < 1) mSize = 1

        if (kernel.equals("boxzen", ignoreCase = true)) {
            val aux = smoother(signal, "boxcar", mSize, mirror)
            return smoother(aux, "parzen", mSize, mirror)
        }

        val w = getWindow(kernel, mSize)

        return if (mirror) {
            val aux = DoubleArray(signal.size + 2 * mSize)
            for (i in 0 until mSize) aux[i] = signal[0]
            signal.copyInto(aux, mSize, 0, signal.size)
            for (i in 0 until mSize) aux[mSize + signal.size + i] = signal[signal.size - 1]

            val smoothedAux = convolveSame(aux, w)
            val smoothed = DoubleArray(signal.size)
            smoothedAux.copyInto(smoothed, 0, mSize, mSize + signal.size)
            smoothed
        } else {
            convolveSame(signal, w)
        }
    }

    fun nextPowerOf2(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    fun radix2FFT(a: Array<Complex>, inverse: Boolean = false) {
        val n = a.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val temp = a[i]
                a[i] = a[j]
                a[j] = temp
            }
        }

        var len = 2
        while (len <= n) {
            val angle = 2 * PI / len * (if (inverse) 1 else -1)
            val wlen = Complex(cos(angle), sin(angle))
            for (i in 0 until n step len) {
                var w = Complex.ONE
                for (k in 0 until len / 2) {
                    val u = a[i + k]
                    val v = a[i + k + len / 2] * w
                    a[i + k] = u + v
                    a[i + k + len / 2] = u - v
                    w *= wlen
                }
            }
            len = len shl 1
        }

        if (inverse) {
            for (i in 0 until n) {
                a[i] = a[i] / n.toDouble()
            }
        }
    }

    fun welch(signal: DoubleArray, fs: Double, nperseg: Int = 1024): Pair<DoubleArray, DoubleArray> {
        val n = signal.size
        var mNperseg = nperseg
        if (n < mNperseg) mNperseg = n
        mNperseg = nextPowerOf2(mNperseg)

        val step = mNperseg / 2
        val win = DoubleArray(mNperseg)
        var winSumSq = 0.0
        for (i in 0 until mNperseg) {
            val valW = 0.5 - 0.5 * cos(2.0 * PI * i / (mNperseg - 1))
            win[i] = valW
            winSumSq += valW * valW
        }

        val segmentPsds = mutableListOf<DoubleArray>()
        for (start in 0..n - mNperseg step step) {
            val segment = DoubleArray(mNperseg)
            var mean = 0.0
            for (i in 0 until mNperseg) {
                segment[i] = signal[start + i]
                mean += segment[i]
            }
            mean /= mNperseg

            val fftInput = Array(mNperseg) { i ->
                Complex((segment[i] - mean) * win[i], 0.0)
            }

            radix2FFT(fftInput)

            val half = mNperseg / 2
            val segmentPsd = DoubleArray(half + 1)
            val scale = 2.0 / (fs * winSumSq)
            val scaleDcNyq = 1.0 / (fs * winSumSq)

            segmentPsd[0] = fftInput[0].magnitude.pow(2.0) * scaleDcNyq
            for (i in 1 until half) {
                segmentPsd[i] = fftInput[i].magnitude.pow(2.0) * scale
            }
            segmentPsd[half] = fftInput[half].magnitude.pow(2.0) * scaleDcNyq

            segmentPsds.Add(segmentPsd)
        }

        if (segmentPsds.isEmpty()) {
            return Pair(DoubleArray(0), DoubleArray(0))
        }

        val psdLen = mNperseg / 2 + 1
        val psd = DoubleArray(psdLen)
        for (i in 0 until psdLen) {
            var sum = 0.0
            for (j in segmentPsds.indices) {
                sum += segmentPsds[j][i]
            }
            psd[i] = sum / segmentPsds.size
        }

        val freqs = DoubleArray(psdLen) { i -> i * (fs / mNperseg) }

        return Pair(freqs, psd)
    }

    private fun MutableList<DoubleArray>.Add(element: DoubleArray) {
        this.add(element)
    }

    fun integrateTrapz(y: DoubleArray, x: DoubleArray): Double {
        if (y.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until y.size - 1) {
            sum += 0.5 * (y[i] + y[i + 1]) * (x[i + 1] - x[i])
        }
        return sum
    }
}
