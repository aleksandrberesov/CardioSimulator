package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.Lead

object EcgExport {
    private val LEAD_ORDER = listOf(
        Lead.I, Lead.II, Lead.III,
        Lead.aVR, Lead.aVL, Lead.aVF,
        Lead.V1, Lead.V2, Lead.V3, Lead.V4, Lead.V5, Lead.V6,
    )

    fun toCsv(
        rhythm: String,
        timestampIso: String,
        waveforms: Map<Lead, Points>,
    ): ByteArray {
        val orderedLeads = LEAD_ORDER.filter { waveforms.containsKey(it) }
        val maxLen = waveforms.values.maxOfOrNull { it.values.size } ?: 0
        val sb = StringBuilder(maxLen * orderedLeads.size * 8 + 256)
        sb.append("# CardioSimulator export\r\n")
        sb.append("# rhythm: ").append(rhythm).append("\r\n")
        sb.append("# timestamp: ").append(timestampIso).append("\r\n")
        sb.append("# samples: ").append(maxLen).append("\r\n")
        sb.append(orderedLeads.joinToString(",") { it.name }).append("\r\n")
        for (i in 0 until maxLen) {
            for ((index, lead) in orderedLeads.withIndex()) {
                if (index > 0) sb.append(',')
                val values = waveforms[lead]?.values
                if (values != null && i < values.size) {
                    sb.append(formatSample(values[i]))
                }
            }
            sb.append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun formatSample(value: Float): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val rounded = (value * 1000f).toInt()
        return when {
            rounded == 0 -> "0"
            rounded % 1000 == 0 -> (rounded / 1000).toString()
            else -> "%.3f".format(value)
        }
    }
}
