package com.example.cardiosimulator.domain

enum class Lead {
    I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6;

    companion object {
        fun fromToken(raw: String): Lead? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}
