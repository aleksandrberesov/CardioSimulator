package com.example.cardiosimulator.domain

/**
 * Bakes a list of [anchors] into a dense sample list by connecting
 * consecutive anchors with straight line segments.
 *
 * Anchors are (x, y) pairs in source coordinates. The result holds one Y
 * sample at every integer X position from the first anchor's X to the last
 * anchor's X; between two anchors the Y value is filled by a straight-line
 * (linear) connection. This is the minimum transform needed to turn sparse
 * anchors into the dense per-sample array that the `.ecg` file format and
 * the renderer expect — there is no easing or curve shaping.
 *
 * Used by [EditablePart.bakedSamples]. The renderer
 * ([com.example.cardiosimulator.ui.components.ChartCanvas]) plots one
 * sample per integer X.
 */
fun bakeAnchorsToSamples(anchors: List<AnchorPoint>): List<Float> {
    if (anchors.isEmpty()) return emptyList()
    if (anchors.size == 1) return listOf(anchors.first().y)

    // Anchors are authored left-to-right; sort defensively by X so an anchor
    // dragged past a neighbour still bakes monotonically.
    val sorted = anchors.sortedBy { it.x }
    val minX = sorted.first().x.toInt()
    val maxX = sorted.last().x.toInt()
    if (maxX <= minX) return listOf(sorted.first().y)

    val out = FloatArray(maxX - minX + 1)
    for (i in 0 until sorted.size - 1) {
        val a = sorted[i]
        val b = sorted[i + 1]
        val x0 = a.x.toInt()
        val x1 = b.x.toInt()
        val span = (x1 - x0).coerceAtLeast(1)
        for (xi in x0..x1) {
            val t = (xi - x0).toFloat() / span
            val idx = (xi - minX).coerceIn(0, out.size - 1)
            out[idx] = a.y + (b.y - a.y) * t
        }
    }
    return out.toList()
}
