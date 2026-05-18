package com.example.cardiosimulator.domain

/**
 * Results of baking sparse anchors into a dense sample list.
 */
data class BakedWaveform(
    val samples: List<Float>,        // baseline-relative source units
    val originX: Int,                // source-X of samples[0] (== minX)
    val anchorSampleIndex: List<Int>,// original-order anchor k -> index into samples
)

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
fun bakeAnchorsToSamples(anchors: List<AnchorPoint>): BakedWaveform {
    if (anchors.isEmpty()) return BakedWaveform(emptyList(), 0, emptyList())
    if (anchors.size == 1) return BakedWaveform(listOf(anchors.first().y), anchors.first().x.toInt(), listOf(0))

    // Anchors are authored left-to-right; sort defensively by X so an anchor
    // dragged past a neighbour still bakes monotonically.
    val sortedWithOriginalIndex = anchors.mapIndexed { index, anchor -> index to anchor }
        .sortedBy { it.second.x }
    val sorted = sortedWithOriginalIndex.map { it.second }

    val minX = sorted.first().x.toInt()
    val maxX = sorted.last().x.toInt()

    if (maxX <= minX) {
        val anchorSampleIndex = anchors.map { 0 }
        return BakedWaveform(listOf(sorted.first().y), minX, anchorSampleIndex)
    }

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

    val anchorSampleIndex = anchors.map { anchor ->
        (anchor.x.toInt() - minX).coerceIn(out.indices)
    }

    return BakedWaveform(out.toList(), minX, anchorSampleIndex)
}
