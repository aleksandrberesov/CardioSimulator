package com.example.cardiosimulator.domain

import com.example.cardiosimulator.data.Points

object ElectrodeFault {
    private const val DISPLACEMENT_GAIN = 0.55f
    private val PRECORDIAL = listOf(Lead.V1, Lead.V2, Lead.V3, Lead.V4, Lead.V5, Lead.V6)

    /** Returns [waveforms] transformed for [state]; for [ElectrodeState.Ok] (or empty) returns it as-is. */
    fun apply(waveforms: Map<Lead, Points>, state: ElectrodeState): Map<Lead, Points> {
        if (state == ElectrodeState.Ok || waveforms.isEmpty()) return waveforms
        val result = waveforms.toMutableMap()
        when (state) {
            ElectrodeState.Swapped -> {
                waveforms[Lead.I]?.let { result[Lead.I] = it.scale(-1f) }   // lead I inverts
                swap(result, waveforms, Lead.II, Lead.III)
                swap(result, waveforms, Lead.aVR, Lead.aVL)
                // aVF and V1..V6 are unchanged by an RA/LA exchange.
            }
            ElectrodeState.Displacement ->
                PRECORDIAL.forEach { v -> waveforms[v]?.let { result[v] = it.scale(DISPLACEMENT_GAIN) } }
            ElectrodeState.Ok -> {}
        }
        return result
    }

    private fun swap(result: MutableMap<Lead, Points>, src: Map<Lead, Points>, a: Lead, b: Lead) {
        val pa = src[a]; val pb = src[b]
        if (pa != null) result[b] = pa
        if (pb != null) result[a] = pb
    }

    private fun Points.scale(factor: Float) = Points(values.map { it * factor })
}
