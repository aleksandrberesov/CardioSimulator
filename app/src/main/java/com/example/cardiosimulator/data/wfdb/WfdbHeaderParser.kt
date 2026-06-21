package com.example.cardiosimulator.data.wfdb

import java.util.Locale

object WfdbHeaderParser {
    // Groupings: 1:file, 2:fmt, 3:xN, 4:skew, 5:offset, 6:gain, 7:baseline, 8:units, 9:adcres, 10:adczero, 11:initval, 12:checksum, 13:blocksize, 14:description
    private val signalLineRegex = Regex("""^(\S+)\s+(\d+)(?:x(\d+))?(?::(\d+))?(?:\+(\d+))?\s+([\d.eE+-]+)?(?:\(([\d.eE+-]+)\))?(?:/(\S+))?\s+(\d+)\s+([\d.eE+-]+)\s+([\d.eE+-]+)\s+([\d.eE+-]+)\s+(\d+)\s*(.*)$""")

    fun parse(text: String): WfdbHeader {
        val lines = text.lines().map { it.trim() }
        if (lines.isEmpty() || lines.all { it.isEmpty() }) throw IllegalArgumentException("Empty header")

        val comments = lines.filter { it.startsWith("#") }.map { it.substring(1).trim() }
        val dataLines = lines.filter { it.isNotEmpty() && !it.startsWith("#") }

        if (dataLines.isEmpty()) throw IllegalArgumentException("No data lines in header")

        val headerParts = dataLines[0].split(Regex("\\s+"))
        val recordName = headerParts[0]
        val numberOfSignals = headerParts.getOrNull(1)?.toInt() ?: 0
        val fs = headerParts.getOrNull(2)?.toFloat() ?: WfdbConstants.DEFAULT_FS
        val nsamp = headerParts.getOrNull(3)?.toInt() ?: 0
        val baseTime = headerParts.getOrNull(4)
        val baseDate = headerParts.getOrNull(5)

        val signals = mutableListOf<WfdbSignalSpec>()
        for (i in 1 until minOf(dataLines.size, numberOfSignals + 1)) {
            val line = dataLines[i]
            val match = signalLineRegex.matchEntire(line) ?: continue

            val groups = match.groupValues
            signals.add(WfdbSignalSpec(
                fileName = groups[1],
                format = groups[2].toInt(),
                samplesPerFrame = groups[3].ifEmpty { "1" }.toInt(),
                skew = groups[4].ifEmpty { "0" }.toInt(),
                byteOffset = groups[5].ifEmpty { "0" }.toLong(),
                gain = groups[6].ifEmpty { "200" }.toFloat(),
                baseline = groups[7].ifEmpty { null }?.toDouble()?.toInt(),
                units = groups[8].ifEmpty { "mV" },
                adcResolution = groups[9].toInt(),
                adcZero = groups[10].toDouble().toInt(),
                initialValue = groups[11].toDouble().toInt(),
                checksum = groups[12].toDouble().toInt(),
                blockSize = groups[13].toInt(),
                description = groups[14].trim()
            ))
        }

        return WfdbHeader(
            recordName = recordName,
            numberOfSignals = numberOfSignals,
            samplingFrequency = fs,
            numberOfSamplesPerSignal = nsamp,
            baseTime = baseTime,
            baseDate = baseDate,
            signals = signals,
            comments = comments
        )
    }

    fun serialize(header: WfdbHeader): String {
        val sb = StringBuilder()
        sb.append("${header.recordName} ${header.numberOfSignals}")
        if (header.samplingFrequency != WfdbConstants.DEFAULT_FS || header.numberOfSamplesPerSignal != 0 || header.baseTime != null) {
            sb.append(String.format(Locale.US, " %.3f", header.samplingFrequency))
        }
        if (header.numberOfSamplesPerSignal != 0 || header.baseTime != null) {
            sb.append(" ${header.numberOfSamplesPerSignal}")
        }
        if (header.baseTime != null) {
            sb.append(" ${header.baseTime}")
        }
        if (header.baseDate != null) {
            sb.append(" ${header.baseDate}")
        }
        sb.append("\n")

        for (s in header.signals) {
            val fmt = StringBuilder("${s.format}")
            if (s.samplesPerFrame != 1) fmt.append("x${s.samplesPerFrame}")
            if (s.skew != 0) fmt.append(":${s.skew}")
            if (s.byteOffset != 0L) fmt.append("+${s.byteOffset}")

            val gain = StringBuilder(String.format(Locale.US, "%.4f", s.gain))
            if (s.baseline != null) gain.append("(${s.baseline})")
            if (s.units != null) gain.append("/${s.units}")

            sb.append("${s.fileName} $fmt $gain ${s.adcResolution} ${s.adcZero} ${s.initialValue} ${s.checksum} ${s.blockSize} ${s.description}\n")
        }

        for (c in header.comments) {
            sb.append("# $c\n")
        }

        return sb.toString()
    }
}
