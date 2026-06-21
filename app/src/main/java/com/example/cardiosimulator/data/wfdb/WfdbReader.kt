package com.example.cardiosimulator.data.wfdb

import java.io.File

object WfdbReader {
    fun readHeader(path: String): WfdbHeader {
        val file = File(path)
        if (!file.exists()) throw IllegalArgumentException("File not found: $path")
        return WfdbHeaderParser.parse(file.readText())
    }

    fun readRecord(path: String): WfdbRecord {
        val header = readHeader(path)
        val dir = File(path).parentFile
        return readRecord(header) { fileName ->
            File(dir, fileName).readBytes()
        }
    }

    fun readRecord(header: WfdbHeader, resolver: (String) -> ByteArray): WfdbRecord {
        val allSamples = Array(header.numberOfSignals) { IntArray(header.numberOfSamplesPerSignal) }
        
        // Group signals by file to avoid multiple reads
        val signalsByFile = header.signals.groupBy { it.fileName }
        
        for ((fileName, signals) in signalsByFile) {
            val bytes = try {
                resolver(fileName)
            } catch (e: Exception) {
                // If file is missing, we might still want to try other signals, 
                // but usually WFDB record is broken without its signals.
                continue 
            }
            
            if (fileName.lowercase().endsWith(".mat")) {
                val (_, matrix) = MatlabLevel4.readMatrix(bytes)
                // Matrix is [rows][cols] -> [signals][samples]
                for ((i, spec) in signals.withIndex()) {
                    val sigIndex = header.signals.indexOf(spec)
                    // In WFDB .mat files, signals are usually rows in the matrix
                    if (i < matrix.size) {
                        val row = matrix[i]
                        val limit = minOf(header.numberOfSamplesPerSignal, row.size)
                        System.arraycopy(row, 0, allSamples[sigIndex], 0, limit)
                    }
                }
            } else {
                // Assume all signals in the same file have the same format
                val firstSpec = signals[0]
                val decoded = WfdbSignalCodec.decode(bytes, firstSpec.format, signals.size, header.numberOfSamplesPerSignal)
                for ((i, spec) in signals.withIndex()) {
                    val sigIndex = header.signals.indexOf(spec)
                    if (i < decoded.size) {
                        System.arraycopy(decoded[i], 0, allSamples[sigIndex], 0, header.numberOfSamplesPerSignal)
                    }
                }
            }
        }
        
        return WfdbRecord(header, allSamples)
    }
}
