package com.example.cardiosimulator.domain

import kotlin.math.atan2

enum class EosAxisClass {
    Normal,
    Horizontal,
    Vertical,
    LeftDeviation,
    RightDeviation,
    ExtremeDeviation
}

data class EosLeadMeasure(
    val qMm: Double,
    val rMm: Double,
    val sMm: Double
) {
    val netMm: Double get() = rMm - (qMm + sMm)
}

data class EosResult(
    val leadI: EosLeadMeasure,
    val leadAvf: EosLeadMeasure,
    val angleDeg: Double,
    val axisClass: EosAxisClass
)

data class EcgSpan(val startSample: Int, val endSample: Int)

object EosAxis {
    fun angleDegrees(netI: Double, netAvf: Double): Double {
        return Math.toDegrees(atan2(netAvf, netI))
    }

    fun classify(angleDeg: Double): EosAxisClass {
        // Normalize to (-180, 180]
        var normalized = angleDeg % 360.0
        if (normalized > 180.0) normalized -= 360.0
        if (normalized <= -180.0) normalized += 360.0

        return when {
            normalized in 30.0..69.0 -> EosAxisClass.Normal
            normalized in 0.0..29.0 -> EosAxisClass.Horizontal
            normalized in 70.0..90.0 -> EosAxisClass.Vertical
            normalized > 90.0 && normalized <= 180.0 -> EosAxisClass.RightDeviation
            normalized > -90.0 && normalized < 0.0 -> EosAxisClass.LeftDeviation
            else -> EosAxisClass.ExtremeDeviation // [-180, -90]
        }
    }

    fun from(leadI: EosLeadMeasure, leadAvf: EosLeadMeasure): EosResult {
        val angle = angleDegrees(leadI.netMm, leadAvf.netMm)
        return EosResult(
            leadI = leadI,
            leadAvf = leadAvf,
            angleDeg = angle,
            axisClass = classify(angle)
        )
    }
}
