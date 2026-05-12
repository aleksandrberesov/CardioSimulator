package com.example.cardiosimulator.domain

/**
 * Mutable counterpart to [WaveformPart] used by the editor. Anchors are the
 * source-of-truth coordinates; [samples] is regenerated on save (or on
 * demand) from the anchor list using the curve interpolation rules.
 *
 * Mirrors RP5's `TPart` semantics: the user edits anchors, the device
 * sample stream is derived.
 */
data class EditablePart(
    val identy: String,
    var title: String,
    var lead: Lead?,
    var pathology: String?,
    var aMax: Int,
    var aValue: Int,
    var duration: Int,
    var amplitude: Float,
    val anchors: MutableList<AnchorPoint>,
    /** Cached baked samples; null = needs rebake from anchors. */
    var samples: List<Int>? = null,
    /** Original source spec — preserved across edits so unknown keys round-trip. */
    var source: SourceSpec?,
    /** Identy on disk (file basename); empty if newly created. */
    val fileName: String = "",
) {
    /** Convenience: source-space samples-per-mV at this part's calibration. */
    val samplesPerMv: Float get() = aMax.toFloat() / aValue.coerceAtLeast(1).toFloat()

    /**
     * Returns the current sample list, baking from anchors if the cache is
     * stale (i.e. anchors changed since the last bake). Samples are stored
     * back into [samples] so subsequent calls are cheap.
     */
    fun bakedSamples(): List<Int> {
        samples?.let { return it }
        if (anchors.isEmpty()) return emptyList()
        // Bake to source-coordinate floats then shift by the 1024 baseline
        // and clip to int — matches what RP5 stores in `points:` files.
        val ys = bakeAnchorsToSamples(anchors)
        val baked = ys.map { (it + 1024f).toInt() }
        samples = baked
        return baked
    }

    fun toWaveformPart(): WaveformPart = WaveformPart(
        identy = identy,
        title = title,
        lead = lead,
        pathology = pathology,
        amplitude = amplitude,
        duration = duration,
        samples = samples ?: bakedSamples(),
        source = (source ?: SourceSpec(
            lead = lead,
            pathology = pathology,
            max = aMax,
            value = aValue,
            name = title,
            identy = identy,
            localizationTag = null,
            center = null,
            anchors = anchors.toList(),
            seriesRefs = emptyList(),
            extras = emptyMap(),
        )).copy(
            lead = lead,
            pathology = pathology,
            max = aMax,
            value = aValue,
            anchors = anchors.toList(),
        ),
        aMax = aMax,
        aValue = aValue,
    )

    companion object {
        fun from(part: WaveformPart, fileName: String = ""): EditablePart = EditablePart(
            identy = part.identy,
            title = part.title,
            lead = part.lead,
            pathology = part.pathology,
            aMax = part.aMax,
            aValue = part.aValue,
            duration = part.duration,
            amplitude = part.amplitude,
            anchors = (part.source?.anchors ?: emptyList()).toMutableList(),
            samples = part.samples,
            source = part.source,
            fileName = fileName,
        )
    }
}

/**
 * Mutable counterpart to [EcgSeries]. Block flags live on the [SeriesPartRef]
 * itself; the editor mutates the [partRefs] list in place.
 */
data class EditableSeries(
    val identy: String,
    var title: String,
    var displayName: String,
    var lead: Lead?,
    var pathology: String?,
    var params: String,
    var aMax: Int,
    var aValue: Int,
    val partRefs: MutableList<SeriesPartRef>,
    var center: Pair<Float, Float>?,
    var source: SourceSpec?,
    val fileName: String,
) {
    fun toEcgSeries(): EcgSeries = EcgSeries(
        identy = identy,
        title = title,
        displayName = displayName,
        lead = lead,
        pathology = pathology,
        params = params,
        partRefs = partRefs.toList(),
        center = center,
        localizationTag = source?.localizationTag,
        source = (source ?: SourceSpec(
            lead = lead,
            pathology = pathology,
            max = aMax,
            value = aValue,
            name = displayName,
            identy = identy,
            localizationTag = null,
            center = center,
            anchors = emptyList(),
            seriesRefs = partRefs.toList(),
            extras = emptyMap(),
        )).copy(
            lead = lead,
            pathology = pathology,
            max = aMax,
            value = aValue,
            center = center,
            seriesRefs = partRefs.toList(),
        ),
        fileName = fileName,
        aMax = aMax,
        aValue = aValue,
    )

    companion object {
        fun from(series: EcgSeries): EditableSeries = EditableSeries(
            identy = series.identy,
            title = series.title,
            displayName = series.displayName,
            lead = series.lead,
            pathology = series.pathology,
            params = series.params,
            aMax = series.aMax,
            aValue = series.aValue,
            partRefs = series.partRefs.toMutableList(),
            center = series.center,
            source = series.source,
            fileName = series.fileName,
        )
    }
}

/**
 * Per-record undo stack. Mirrors RP5's `FStackSourcePoints` push-pop model:
 * before every mutation, push a snapshot; undo pops the last snapshot.
 * Capped at [maxDepth] to bound memory.
 */
class UndoStack<T>(private val maxDepth: Int = 50) {
    private val stack = ArrayDeque<T>()

    fun push(snapshot: T) {
        if (stack.size >= maxDepth) stack.removeFirst()
        stack.addLast(snapshot)
    }

    fun pop(): T? = stack.removeLastOrNull()

    fun peek(): T? = stack.lastOrNull()

    fun clear() = stack.clear()

    val depth: Int get() = stack.size
    val isEmpty: Boolean get() = stack.isEmpty()
}
