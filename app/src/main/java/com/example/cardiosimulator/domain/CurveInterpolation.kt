package com.example.cardiosimulator.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Easing functions used by [bakeAnchors].
 *
 * `t` is the normalized segment progress in [0, 1].
 * Result is the interpolated factor in [0, 1] which is then mixed with the
 * segment endpoints.
 */
fun ease(curve: EasingCurve, t: Float): Float {
    val tt = t.coerceIn(0f, 1f)
    return when (curve) {
        EasingCurve.LINEAR -> tt
        EasingCurve.SINE -> -(cos(PI * tt) - 1).toFloat() / 2f
        EasingCurve.SINE_IN -> 1f - cos((tt * PI / 2)).toFloat()
        EasingCurve.SINE_OUT -> sin((tt * PI / 2)).toFloat()
        EasingCurve.QUAD -> if (tt < 0.5f) 2 * tt * tt else 1f - ((-2 * tt + 2).let { it * it } / 2f)
        EasingCurve.QUAD_IN -> tt * tt
        EasingCurve.QUAD_OUT -> 1f - (1 - tt).let { it * it }
        EasingCurve.CUBIC -> if (tt < 0.5f) 4 * tt * tt * tt else 1f - ((-2 * tt + 2).let { it * it * it } / 2f)
        EasingCurve.CUBIC_IN -> tt * tt * tt
        EasingCurve.CUBIC_OUT -> 1f - (1 - tt).let { it * it * it }
        EasingCurve.QUART -> if (tt < 0.5f) 8 * tt * tt * tt * tt else 1f - ((-2 * tt + 2).let { it * it * it * it } / 2f)
        EasingCurve.QUART_IN -> tt * tt * tt * tt
        EasingCurve.QUART_OUT -> 1f - (1 - tt).let { it * it * it * it }
        EasingCurve.CIRC -> if (tt < 0.5f)
            ((1 - sqrt((1 - (2 * tt).let { it * it }).toDouble())) / 2).toFloat()
        else
            ((sqrt((1 - (-2 * tt + 2).let { it * it }).toDouble()) + 1) / 2).toFloat()
        EasingCurve.CIRC_IN -> 1f - sqrt((1 - tt * tt).toDouble()).toFloat()
        EasingCurve.CIRC_OUT -> sqrt((1 - (tt - 1) * (tt - 1)).toDouble()).toFloat()
    }
}

/**
 * Evaluates a cubic Bezier with control points [p0], [p1], [p2], [p3] at
 * parameter [t] in [0, 1].
 */
fun bezierY(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val u = 1f - t
    return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
}

/**
 * Bakes a list of [anchors] into a series of source-coordinate samples.
 *
 * Layout: anchors are pairs (x, y) in source coordinates. Samples are
 * emitted at integer `x` positions between anchors using the curve flag
 * of the *destination* anchor.
 *
 * Cubic-Bezier handles are represented as three consecutive anchors marked
 * with `cubic`: the middle two anchors are handles, the outer two are
 * endpoints. We detect a triple by looking at three consecutive `CUBIC*`
 * curves and consuming them as one cubic segment.
 *
 * Returns a list of (x, y) tuples in source coordinates.
 */
fun bakeAnchors(anchors: List<AnchorPoint>): List<Pair<Float, Float>> {
    if (anchors.size < 2) return anchors.map { it.x to it.y }
    val out = mutableListOf<Pair<Float, Float>>()
    var i = 0
    while (i < anchors.size - 1) {
        val a = anchors[i]
        val b = anchors[i + 1]
        // Look for a Bezier triple: a, h1, h2, b where the inner two carry
        // a cubic curve flag.
        val h1 = anchors.getOrNull(i + 1)
        val h2 = anchors.getOrNull(i + 2)
        val end = anchors.getOrNull(i + 3)
        if (h1 != null && h2 != null && end != null &&
            (h1.curve == EasingCurve.CUBIC || h1.curve == EasingCurve.CUBIC_IN || h1.curve == EasingCurve.CUBIC_OUT) &&
            (h2.curve == EasingCurve.CUBIC || h2.curve == EasingCurve.CUBIC_IN || h2.curve == EasingCurve.CUBIC_OUT)
        ) {
            val span = (end.x - a.x).coerceAtLeast(1f)
            val steps = span.toInt().coerceAtLeast(1)
            for (s in 0..steps) {
                val t = s / steps.toFloat()
                val x = a.x + t * span
                val y = bezierY(a.y, h1.y, h2.y, end.y, t)
                out += x to y
            }
            i += 3
            continue
        }
        // Plain segment: ease from a to b using b's curve flag.
        val span = (b.x - a.x).coerceAtLeast(1f)
        val steps = span.toInt().coerceAtLeast(1)
        for (s in 0..steps) {
            val t = s / steps.toFloat()
            val k = ease(b.curve, t)
            val x = a.x + t * span
            val y = a.y + (b.y - a.y) * k
            out += x to y
        }
        i += 1
    }
    return out
}

/**
 * Convenience: bake anchors directly to a list of Y samples at integer X
 * positions starting at the first anchor's X. Used by the editor's
 * preview path where the renderer wants a flat sample list to feed into
 * [com.example.cardiosimulator.ui.components.ChartCanvas].
 */
fun bakeAnchorsToSamples(anchors: List<AnchorPoint>): List<Float> {
    val pts = bakeAnchors(anchors)
    if (pts.isEmpty()) return emptyList()
    val minX = pts.first().first.toInt()
    val maxX = pts.last().first.toInt()
    if (maxX <= minX) return pts.map { it.second }
    val out = FloatArray(maxX - minX + 1)
    val filled = BooleanArray(out.size)
    // First pass: assign by nearest integer x.
    for ((x, y) in pts) {
        val ix = (x.toInt() - minX).coerceIn(0, out.size - 1)
        out[ix] = y
        filled[ix] = true
    }
    // Second pass: linear-fill any gaps.
    var lastFilled = -1
    for (i in out.indices) {
        if (filled[i]) {
            if (lastFilled >= 0 && i - lastFilled > 1) {
                val span = i - lastFilled
                val y0 = out[lastFilled]
                val y1 = out[i]
                for (k in (lastFilled + 1) until i) {
                    val t = (k - lastFilled).toFloat() / span
                    out[k] = y0 + (y1 - y0) * t
                }
            }
            lastFilled = i
        }
    }
    return out.toList()
}
