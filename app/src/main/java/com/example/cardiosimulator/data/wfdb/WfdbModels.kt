package com.example.cardiosimulator.data.wfdb

object WfdbConstants {
    const val DEFAULT_FS = 250f
    const val DEFAULT_GAIN = 200f
    const val DEFAULT_ADC_RES = 12
    const val DEFAULT_ADC_ZERO = 0
}

data class WfdbSignalSpec(
    val fileName: String,
    val format: Int,
    val samplesPerFrame: Int = 1,
    val skew: Int = 0,
    val byteOffset: Long = 0,
    val gain: Float = WfdbConstants.DEFAULT_GAIN,
    val baseline: Int? = null,
    val units: String? = "mV",
    val adcResolution: Int = WfdbConstants.DEFAULT_ADC_RES,
    val adcZero: Int = WfdbConstants.DEFAULT_ADC_ZERO,
    val initialValue: Int = 0,
    val checksum: Int = 0,
    val blockSize: Int = 0,
    val description: String = ""
) {
    val effectiveBaseline: Int get() = baseline ?: adcZero
    val effectiveGain: Float get() = if (gain == 0f) WfdbConstants.DEFAULT_GAIN else gain
}

data class WfdbHeader(
    val recordName: String,
    val numberOfSignals: Int,
    val samplingFrequency: Float = WfdbConstants.DEFAULT_FS,
    val numberOfSamplesPerSignal: Int = 0,
    val baseTime: String? = null,
    val baseDate: String? = null,
    val signals: List<WfdbSignalSpec> = emptyList(),
    val comments: List<String> = emptyList()
)

data class WfdbRecord(
    val header: WfdbHeader,
    val samples: Array<IntArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WfdbRecord) return false
        if (header != other.header) return false
        return samples.contentDeepEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + samples.contentDeepHashCode()
        return result
    }
}
