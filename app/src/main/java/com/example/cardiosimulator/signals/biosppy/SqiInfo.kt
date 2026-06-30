package com.example.cardiosimulator.signals.biosppy

import com.example.cardiosimulator.domain.Lead

data class SqiInfo(
    val quality: String,
    val sSqi: Double,
    val kSqi: Double,
    val pSqi: Double,
    val lead: Lead? = null,
)
