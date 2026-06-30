package com.example.cardiosimulator.signals.biosppy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun computeSqi(signal: DoubleArray, samplingRate: Double): SqiInfo? =
    withContext(Dispatchers.Default) {
        if (signal.size < 500) return@withContext null
        try {
            val det1 = QrsSegmenters.hamiltonSegmenter(signal, samplingRate)
            val det2 = QrsSegmenters.ssfSegmenter(signal, samplingRate)
            SqiInfo(
                quality = Sqi.zz2018(signal, det1, det2, samplingRate, mode = "fuzzy"),
                sSqi = Sqi.ssqi(signal),
                kSqi = Sqi.ksqi(signal),
                pSqi = Sqi.psqi(signal),
            )
        } catch (e: Exception) {
            null
        }
    }
