package com.example.cardiosimulator.data.wfdb

import java.io.File

enum class WfdbStorage { DAT, MAT }

object WfdbWriter {
    fun writeRecord(record: WfdbRecord, dirPath: String, storage: WfdbStorage) {
        val dir = File(dirPath)
        if (!dir.exists()) dir.mkdirs()

        val (header, signalBytes) = build(record, storage)
        
        val headerFile = File(dir, "${record.header.recordName}.hea")
        headerFile.writeText(WfdbHeaderParser.serialize(header))
        
        val fileName = header.signals.firstOrNull()?.fileName ?: (record.header.recordName + (if (storage == WfdbStorage.MAT) ".mat" else ".dat"))
        File(dir, fileName).writeBytes(signalBytes)
    }

    fun build(record: WfdbRecord, storage: WfdbStorage): Pair<WfdbHeader, ByteArray> {
        val recordName = record.header.recordName
        val numberOfSignals = record.samples.size
        val numberOfSamples = if (numberOfSignals > 0) record.samples[0].size else 0
        
        val ext = if (storage == WfdbStorage.MAT) ".mat" else ".dat"
        val fileName = "$recordName$ext"
        val format = 16 // We only write format 16
        
        val updatedSignals = record.header.signals.mapIndexed { i, spec ->
            val samples = if (i < record.samples.size) record.samples[i] else intArrayOf()
            spec.copy(
                fileName = fileName,
                format = format,
                byteOffset = if (storage == WfdbStorage.MAT) (20 + recordName.length + 1).toLong() else 0L,
                initialValue = if (samples.isNotEmpty()) samples[0] else 0,
                checksum = calculateChecksum(samples)
            )
        }

        val bytes = if (storage == WfdbStorage.MAT) {
            // Flatten column-major for .mat
            val flat = IntArray(numberOfSignals * numberOfSamples)
            var k = 0
            for (c in 0 until numberOfSamples) {
                for (r in 0 until numberOfSignals) {
                    flat[k++] = record.samples[r][c]
                }
            }
            MatlabLevel4.writeInt16Matrix(recordName, numberOfSignals, numberOfSamples, flat)
        } else {
            WfdbSignalCodec.encode(16, numberOfSignals, numberOfSamples, record.samples)
        }

        return record.header.copy(signals = updatedSignals) to bytes
    }

    private fun calculateChecksum(samples: IntArray): Int {
        var sum: Short = 0
        for (s in samples) {
            sum = (sum + s).toShort()
        }
        return sum.toInt()
    }
}
