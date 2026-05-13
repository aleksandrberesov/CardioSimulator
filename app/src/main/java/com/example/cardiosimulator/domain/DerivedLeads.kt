package com.example.cardiosimulator.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Linear combinations and angular projections used to derive the missing
 * leads of a 12-lead ECG from a smaller recorded set using Einthoven /
 * Goldberger and V-lead angular projection.
 *
 * Inputs and outputs are in source-coordinate units. The caller is
 * responsible for ensuring inputs are aligned in time and sampled at the
 * same rate; mismatches are silently truncated to the shorter length.
 */
object DerivedLeads {

    /**
     * Einthoven / Goldberger combinations from limb leads I and II.
     *
     *  III  = II - I
     *  aVR  = -(I + II) / 2
     *  aVL  = I - II / 2     = (2·I - II) / 2
     *  aVF  = II - I / 2     = (2·II - I) / 2
     *
     * Falls back to an empty list if [target] is not one of III/aVR/aVL/aVF
     * or if either input is empty.
     */
    fun combineIII_aVR_aVL_aVF(leadI: List<Float>, leadII: List<Float>, target: Lead): List<Float> {
        if (leadI.isEmpty() || leadII.isEmpty()) return emptyList()
        val n = minOf(leadI.size, leadII.size)
        val out = FloatArray(n)
        when (target) {
            Lead.III -> for (i in 0 until n) out[i] = leadII[i] - leadI[i]
            Lead.aVR -> for (i in 0 until n) out[i] = -(leadI[i] + leadII[i]) / 2f
            Lead.aVL -> for (i in 0 until n) out[i] = (2f * leadI[i] - leadII[i]) / 2f
            Lead.aVF -> for (i in 0 until n) out[i] = (2f * leadII[i] - leadI[i]) / 2f
            else -> return emptyList()
        }
        return out.toList()
    }

    /**
     * Angular projection from V2 and V6 onto the missing V-lead positions.
     *
     * The precordial leads are assumed to be at fixed angles on the chest:
     *
     *  V1: 115°,  V2: 94°,   V3: 70°,
     *  V4: 45°,   V5: 23°,   V6: 0°
     *
     * Given V2 and V6 as basis vectors, each other lead's projection is
     * derived by decomposing the angular position into cos/sin components
     * relative to V2 and V6.
     */
    fun combineV1_V3_V4_V5(leadV2: List<Float>, leadV6: List<Float>, target: Lead): List<Float> {
        if (leadV2.isEmpty() || leadV6.isEmpty()) return emptyList()
        val n = minOf(leadV2.size, leadV6.size)

        val angles = mapOf(
            Lead.V1 to 115.0,
            Lead.V3 to 70.0,
            Lead.V4 to 45.0,
            Lead.V5 to 23.0,
        )
        val angleDeg = angles[target] ?: return emptyList()
        val a = Math.toRadians(angleDeg)
        val v2a = Math.toRadians(94.0)
        val v6a = Math.toRadians(0.0)

        // Decompose `a` into a 2D basis spanned by V2 (94°) and V6 (0°).
        // Solve cos(a) = α·cos(v2a) + β·cos(v6a)
        //       sin(a) = α·sin(v2a) + β·sin(v6a)
        val det = cos(v2a) * sin(v6a) - cos(v6a) * sin(v2a)
        if (det == 0.0) return emptyList()
        val alpha = (cos(a) * sin(v6a) - cos(v6a) * sin(a)) / det
        val beta = (cos(v2a) * sin(a) - cos(a) * sin(v2a)) / det

        val out = FloatArray(n)
        for (i in 0 until n) {
            out[i] = (alpha * leadV2[i] + beta * leadV6[i]).toFloat()
        }
        return out.toList()
    }

    /** Set of leads producible from I + II. */
    val DerivableFromIandII: Set<Lead> = setOf(Lead.III, Lead.aVR, Lead.aVL, Lead.aVF)

    /** Set of leads producible from V2 + V6. */
    val DerivableFromV2andV6: Set<Lead> = setOf(Lead.V1, Lead.V3, Lead.V4, Lead.V5)
}
