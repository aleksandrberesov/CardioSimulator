package com.example.cardiosimulator.data

import com.example.cardiosimulator.domain.*

/**
 * Android implementation of the equal-parts ECG slicer.
 */
object EcgAssemblyBuilder {
    /**
     * Builds an [EcgAssembly] by slicing a pathology lead into [partCount] equal pieces.
     * Window is capped to 5 seconds.
     */
    fun build(
        repository: PathologyRepository,
        sourceId: String,
        lead: Lead,
        partCount: Int,
        fs: Int
    ): EcgAssembly? {
        val file = repository.readPathology(sourceId) ?: return null
        val baseline = repository.manifest()?.baseline ?: 1024
        val stream = file.leads[lead] ?: return null
        
        // Cap window to 5 seconds
        val windowSamples = fs * 5
        
        val parts = EcgAssemblySlicer.split(stream.samples, baseline, partCount, windowSamples) ?: return null
        
        return EcgAssembly(
            sampleRateHz = fs,
            parts = parts,
            sourcePathologyId = sourceId,
            sliceLead = lead
        )
    }
}
