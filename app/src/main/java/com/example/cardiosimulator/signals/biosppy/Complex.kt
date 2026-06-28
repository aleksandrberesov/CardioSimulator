package com.example.cardiosimulator.signals.biosppy

import kotlin.math.*

data class Complex(val real: Double, val imag: Double = 0.0) {
    operator fun plus(other: Complex) = Complex(real + other.real, imag + other.imag)
    operator fun plus(other: Double) = Complex(real + other, imag)
    
    operator fun minus(other: Complex) = Complex(real - other.real, imag - other.imag)
    operator fun minus(other: Double) = Complex(real - other, imag)
    
    operator fun times(other: Complex) = Complex(
        real * other.real - imag * other.imag,
        real * other.imag + imag * other.real
    )
    operator fun times(other: Double) = Complex(real * other, imag * other)
    
    operator fun div(other: Complex): Complex {
        val den = other.real * other.real + other.imag * other.imag
        return Complex(
            (real * other.real + imag * other.imag) / den,
            (imag * other.real - real * other.imag) / den
        )
    }
    operator fun div(other: Double) = Complex(real / other, imag / other)

    fun pow(n: Int): Complex {
        var result = ONE
        for (i in 0 until n) {
            result *= this
        }
        return result
    }

    fun pow(x: Double): Complex {
        val r = magnitude
        val theta = atan2(imag, real)
        return fromPolar(r.pow(x), theta * x)
    }

    operator fun unaryMinus() = Complex(-real, -imag)

    val magnitude: Double get() = sqrt(real * real + imag * imag)

    companion object {
        val ZERO = Complex(0.0, 0.0)
        val ONE = Complex(1.0, 0.0)
        val I = Complex(0.0, 1.0)

        fun fromPolar(r: Double, theta: Double) = Complex(r * cos(theta), r * sin(theta))
        
        fun sqrt(c: Complex): Complex {
            val r = c.magnitude
            val real = sqrt((r + c.real) / 2.0)
            val imag = sign(c.imag) * sqrt((r - c.real) / 2.0)
            return Complex(real, imag)
        }
    }
}

operator fun Double.plus(c: Complex) = Complex(this + c.real, c.imag)
operator fun Double.minus(c: Complex) = Complex(this - c.real, -c.imag)
operator fun Double.times(c: Complex) = Complex(this * c.real, this * c.imag)
operator fun Double.div(c: Complex) = Complex(this) / c
