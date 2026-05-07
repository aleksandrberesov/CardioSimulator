package com.example.cardiosimulator.network

import com.example.cardiosimulator.domain.Lead

sealed class TcpMessage {
    abstract val type: String
    abstract val id: String?

    data class StartCommand(
        override val id: String? = null,
        val sampleRate: Int? = null,
        val params: Map<String, String> = emptyMap(),
    ) : TcpMessage() {
        override val type: String get() = TYPE
        companion object { const val TYPE = "start" }
    }

    data class StopCommand(
        override val id: String? = null,
    ) : TcpMessage() {
        override val type: String get() = TYPE
        companion object { const val TYPE = "stop" }
    }

    data class PointsMessage(
        override val id: String? = null,
        val lead: Lead? = null,
        val identy: String? = null,
        val offset: Int = 0,
        val values: List<Float> = emptyList(),
    ) : TcpMessage() {
        override val type: String get() = TYPE
        companion object { const val TYPE = "points" }
    }
}
