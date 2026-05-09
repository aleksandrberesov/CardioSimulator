package com.example.cardiosimulator.network

import com.example.cardiosimulator.domain.Lead
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class TcpProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

object TcpProtocol {

    private const val KEY_TYPE = "type"
    private const val KEY_ID = "id"
    private const val KEY_SAMPLE_RATE = "sampleRate"
    private const val KEY_PARAMS = "params"
    private const val KEY_LEAD = "lead"
    private const val KEY_IDENTY = "identy"
    private const val KEY_OFFSET = "offset"
    private const val KEY_VALUES = "values"
    private const val KEY_FILENAME = "filename"
    private const val KEY_SIZE = "size"
    private const val KEY_BYTES = "bytes"

    fun encode(message: TcpMessage): String = toJson(message).toString()

    fun toJson(message: TcpMessage): JSONObject {
        val obj = JSONObject().put(KEY_TYPE, message.type)
        message.id?.let { obj.put(KEY_ID, it) }
        when (message) {
            is TcpMessage.StartCommand -> {
                message.sampleRate?.let { obj.put(KEY_SAMPLE_RATE, it) }
                if (message.params.isNotEmpty()) obj.put(KEY_PARAMS, JSONObject(message.params))
            }
            is TcpMessage.StopCommand -> Unit
            is TcpMessage.PointsMessage -> {
                message.lead?.let { obj.put(KEY_LEAD, it.name) }
                message.identy?.let { obj.put(KEY_IDENTY, it) }
                if (message.offset != 0) obj.put(KEY_OFFSET, message.offset)
                obj.put(KEY_VALUES, JSONArray(message.values))
            }
            is TcpMessage.UploadMessage -> {
                obj.put(KEY_FILENAME, message.filename)
                obj.put(KEY_SIZE, message.size)
            }
            is TcpMessage.AckMessage -> {
                obj.put(KEY_FILENAME, message.filename)
                obj.put(KEY_BYTES, message.bytes)
            }
        }
        return obj
    }

    fun decode(json: String): TcpMessage = try {
        fromJson(JSONObject(json))
    } catch (e: JSONException) {
        throw TcpProtocolException("Invalid JSON: ${e.message}", e)
    }

    fun decodeOrNull(json: String): TcpMessage? =
        try { decode(json) } catch (_: TcpProtocolException) { null }

    fun fromJson(obj: JSONObject): TcpMessage {
        val type = obj.optStringOrNull(KEY_TYPE)
            ?: throw TcpProtocolException("Missing required field: $KEY_TYPE")
        val id = obj.optStringOrNull(KEY_ID)
        return when (type) {
            TcpMessage.StartCommand.TYPE -> TcpMessage.StartCommand(
                id = id,
                sampleRate = obj.optIntOrNull(KEY_SAMPLE_RATE),
                params = obj.optJSONObject(KEY_PARAMS)?.toStringMap().orEmpty(),
            )
            TcpMessage.StopCommand.TYPE -> TcpMessage.StopCommand(id = id)
            TcpMessage.PointsMessage.TYPE -> TcpMessage.PointsMessage(
                id = id,
                lead = obj.optStringOrNull(KEY_LEAD)?.let { token ->
                    Lead.fromToken(token) ?: throw TcpProtocolException("Unknown lead: $token")
                },
                identy = obj.optStringOrNull(KEY_IDENTY),
                offset = obj.optInt(KEY_OFFSET, 0),
                values = parseValues(obj),
            )
            TcpMessage.UploadMessage.TYPE -> TcpMessage.UploadMessage(
                id = id,
                filename = obj.optStringOrNull(KEY_FILENAME)
                    ?: throw TcpProtocolException("Missing required field: $KEY_FILENAME"),
                size = obj.optLongOrNull(KEY_SIZE)
                    ?: throw TcpProtocolException("Missing required field: $KEY_SIZE"),
            )
            TcpMessage.AckMessage.TYPE -> TcpMessage.AckMessage(
                id = id,
                filename = obj.optStringOrNull(KEY_FILENAME)
                    ?: throw TcpProtocolException("Missing required field: $KEY_FILENAME"),
                bytes = obj.optLongOrNull(KEY_BYTES)
                    ?: throw TcpProtocolException("Missing required field: $KEY_BYTES"),
            )
            else -> throw TcpProtocolException("Unknown message type: $type")
        }
    }

    fun decodeFrames(lines: Sequence<String>): Sequence<TcpMessage> =
        lines.map(String::trim).filter(String::isNotEmpty).map(::decode)

    private fun parseValues(obj: JSONObject): List<Float> {
        val arr = obj.optJSONArray(KEY_VALUES)
            ?: throw TcpProtocolException("Missing required field: $KEY_VALUES")
        return List(arr.length()) { i ->
            try {
                arr.getDouble(i).toFloat()
            } catch (e: JSONException) {
                throw TcpProtocolException("Invalid number in $KEY_VALUES at index $i: ${e.message}", e)
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.toStringMap(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val it = keys()
        while (it.hasNext()) {
            val k = it.next()
            out[k] = optString(k, "")
        }
        return out
    }
}
