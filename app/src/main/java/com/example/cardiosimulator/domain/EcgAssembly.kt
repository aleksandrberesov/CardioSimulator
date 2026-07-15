package com.example.cardiosimulator.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable

/**
 * A single contiguous part of an ECG trace, baseline-zeroed.
 */
@Serializable
data class EcgAssemblyPart(
    val samples: List<Int>
)

/**
 * Specification for an "Assemble the ECG" question.
 * A single rhythm's lead is cut into [partCount] equal contiguous parts.
 */
@Serializable
data class EcgAssembly(
    val sampleRateHz: Int,
    val parts: List<EcgAssemblyPart>,
    val sourcePathologyId: String? = null,
    val sliceLead: Lead = Lead.II
) {
    val partCount: Int get() = parts.size
    val isComplete: Boolean get() = partCount >= 2
}

/**
 * Pure slicer that cuts a waveform into equal parts.
 */
object EcgAssemblySlicer {
    private const val MIN_PARTS = 2
    private const val MAX_PARTS = 8

    /**
     * Slices the [samples] (after subtracting [baseline]) into [partCount] equal contiguous parts.
     * If [windowSamples] > 0, only the first [windowSamples] are considered.
     * Returns null if the input is too short or partCount is out of range.
     */
    fun split(samples: IntArray, baseline: Int, partCount: Int, windowSamples: Int = 0): List<EcgAssemblyPart>? {
        if (partCount < MIN_PARTS || partCount > MAX_PARTS) return null
        
        val limit = if (windowSamples > 0 && windowSamples < samples.size) windowSamples else samples.size
        if (limit < partCount) return null
        
        val partLen = limit / partCount
        if (partLen <= 0) return null
        
        val result = mutableListOf<EcgAssemblyPart>()
        for (i in 0 until partCount) {
            val start = i * partLen
            // Slice and subtract baseline
            val partSamples = List(partLen) { idx -> samples[start + idx] - baseline }
            result.add(EcgAssemblyPart(partSamples))
        }
        return result
    }
}

/**
 * A shuffled part in the learner's palette.
 */
@Serializable
data class AssemblyPaletteItem(
    val correctIndex: Int,
    val samples: List<Int>,
    val key: String
)

/**
 * Runtime state for one assembly attempt.
 */
class AssemblyAttempt(val spec: EcgAssembly, seed: Int) {
    /** The shuffled pool of parts. */
    val palette: List<AssemblyPaletteItem> = spec.parts.mapIndexed { index, part ->
        AssemblyPaletteItem(index, part.samples, "part_$index")
    }.shuffled(kotlin.random.Random(seed.toLong()))

    /** The N ordered slots on the tape. */
    val slots: List<SlotState> = List(spec.partCount) { SlotState(it) }

    class SlotState(val index: Int) {
        /** The key of the [AssemblyPaletteItem] currently placed in this slot, or null if empty. */
        var placedKey by mutableStateOf<String?>(null)
    }

    /** Returns the item matching the key, if any. */
    fun itemByKey(key: String): AssemblyPaletteItem? = palette.find { it.key == key }

    /** Returns the item currently in [slotIndex]. */
    fun placedAt(slotIndex: Int): AssemblyPaletteItem? {
        val key = slots.getOrNull(slotIndex)?.placedKey ?: return null
        return itemByKey(key)
    }

    /** Returns the index of the slot containing this key, or -1. */
    fun slotOf(key: String): Int {
        return slots.indexOfFirst { it.placedKey == key }
    }

    /** 
     * Places the item [key] into [slotIndex].
     * Moves the item out of any old slot; bumps any current occupant back to the pool.
     */
    fun place(slotIndex: Int, key: String?) {
        if (slotIndex !in slots.indices) return
        
        if (key == null) {
            slots[slotIndex].placedKey = null
            return
        }

        // Move the item out of any old slot
        val oldSlot = slotOf(key)
        if (oldSlot != -1) {
            slots[oldSlot].placedKey = null
        }

        // Bumps any current occupant back to the pool (by overwriting)
        slots[slotIndex].placedKey = key
    }

    /** Clears the specified slot. */
    fun clear(slotIndex: Int) {
        if (slotIndex in slots.indices) {
            slots[slotIndex].placedKey = null
        }
    }

    /** Items currently not placed in any slot. */
    val available: List<AssemblyPaletteItem>
        get() = palette.filter { item -> slots.none { it.placedKey == item.key } }

    /** True if all slots are filled. */
    val isComplete: Boolean get() = slots.all { it.placedKey != null }
    
    /** True if every slot i holds the part whose original index == i. */
    val allCorrect: Boolean 
        get() = isComplete && slots.all { slot ->
            placedAt(slot.index)?.correctIndex == slot.index
        }
}
