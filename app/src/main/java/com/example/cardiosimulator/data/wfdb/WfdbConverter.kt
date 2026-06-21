package com.example.cardiosimulator.data.wfdb

import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.LeadStream
import com.example.cardiosimulator.domain.PathologyFile
import com.example.cardiosimulator.data.EcgCalibration

object WfdbConverter {
    fun toPathologyFile(record: WfdbRecord, id: String, titleEn: String, nameRu: String?): PathologyFile {
        val leadsMap = mutableMapOf<Lead, LeadStream>()
        val cal = EcgCalibration()
        
        for ((i, spec) in record.header.signals.withIndex()) {
            val lead = Lead.fromToken(spec.description) ?: continue
            if (i >= record.samples.size) continue
            
            val wfdbSamples = record.samples[i]
            val pathologySamples = IntArray(wfdbSamples.size)
            
            val gain = spec.effectiveGain
            val baseline = spec.effectiveBaseline
            
            for (j in wfdbSamples.indices) {
                val mv = (wfdbSamples[j] - baseline) / gain
                pathologySamples[j] = (1024 + mv * cal.adcCountsPerMv).toInt()
            }
            
            leadsMap[lead] = LeadStream(lead, pathologySamples)
        }
        
        return PathologyFile(
            id = id,
            titleEn = titleEn,
            nameRu = nameRu,
            leads = leadsMap
        )
    }

    fun fromPathologyFile(file: PathologyFile, recordName: String): WfdbRecord {
        val signals = mutableListOf<WfdbSignalSpec>()
        val allSamples = mutableListOf<IntArray>()
        val cal = EcgCalibration()
        
        for ((lead, stream) in file.leads) {
            val pathSamples = stream.samples
            val wfdbSamples = IntArray(pathSamples.size)
            
            val gain = 1000f
            val baseline = 0
            
            for (i in pathSamples.indices) {
                val mv = (pathSamples[i] - 1024) / cal.adcCountsPerMv
                wfdbSamples[i] = (mv * gain + baseline).toInt()
            }
            
            signals.add(WfdbSignalSpec(
                fileName = "$recordName.dat",
                format = 16,
                gain = gain,
                baseline = baseline,
                description = lead.name,
                adcResolution = 16
            ))
            allSamples.add(wfdbSamples)
        }
        
        val header = WfdbHeader(
            recordName = recordName,
            numberOfSignals = signals.size,
            samplingFrequency = 500f,
            numberOfSamplesPerSignal = if (allSamples.isNotEmpty()) allSamples[0].size else 0,
            signals = signals
        )
        
        return WfdbRecord(header, allSamples.toTypedArray())
    }
}
