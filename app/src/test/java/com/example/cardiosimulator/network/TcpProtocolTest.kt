package com.example.cardiosimulator.network

import com.example.cardiosimulator.domain.Lead
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TcpProtocolTest {

    @Test
    fun encodeStartCommand() {
        val msg = TcpMessage.StartCommand(id = "m1", sampleRate = 250)
        val json = JSONObject(TcpProtocol.encode(msg))
        assertEquals("start", json.getString("type"))
        assertEquals("m1", json.getString("id"))
        assertEquals(250, json.getInt("sampleRate"))
    }

    @Test
    fun encodeStopCommand() {
        val json = JSONObject(TcpProtocol.encode(TcpMessage.StopCommand(id = "m2")))
        assertEquals("stop", json.getString("type"))
        assertEquals("m2", json.getString("id"))
    }

    @Test
    fun encodePointsMessage() {
        val msg = TcpMessage.PointsMessage(
            id = "m3", lead = Lead.II, identy = "abc",
            offset = 10, values = listOf(0.1f, 0.2f, 0.3f),
        )
        val json = JSONObject(TcpProtocol.encode(msg))
        assertEquals("points", json.getString("type"))
        assertEquals("II", json.getString("lead"))
        assertEquals("abc", json.getString("identy"))
        assertEquals(10, json.getInt("offset"))
        val arr = json.getJSONArray("values")
        assertEquals(3, arr.length())
        assertEquals(0.1, arr.getDouble(0), 1e-6)
    }

    @Test
    fun encodePointsOmitsZeroOffsetAndAbsentFields() {
        val msg = TcpMessage.PointsMessage(values = listOf(1f))
        val json = JSONObject(TcpProtocol.encode(msg))
        assertTrue(!json.has("offset"))
        assertTrue(!json.has("lead"))
        assertTrue(!json.has("identy"))
        assertTrue(!json.has("id"))
    }

    @Test
    fun startRoundTrip() {
        val msg = TcpMessage.StartCommand(
            id = "m1", sampleRate = 500, params = mapOf("source" to "ecg"),
        )
        assertEquals(msg, TcpProtocol.decode(TcpProtocol.encode(msg)))
    }

    @Test
    fun stopRoundTrip() {
        val msg = TcpMessage.StopCommand(id = "m2")
        assertEquals(msg, TcpProtocol.decode(TcpProtocol.encode(msg)))
    }

    @Test
    fun pointsRoundTrip() {
        val msg = TcpMessage.PointsMessage(
            id = "m3", lead = Lead.aVF, identy = "series-1",
            offset = 5, values = listOf(1f, 2f, 3.5f),
        )
        assertEquals(msg, TcpProtocol.decode(TcpProtocol.encode(msg)))
    }

    @Test
    fun decodeAcceptsLowercaseAndPaddedLeadToken() {
        val msg = TcpProtocol.decode("""{"type":"points","lead":" ii ","values":[0]}""")
        assertEquals(Lead.II, (msg as TcpMessage.PointsMessage).lead)
    }

    @Test
    fun decodeUnknownLeadThrows() {
        assertThrowsContains("Unknown lead") {
            TcpProtocol.decode("""{"type":"points","lead":"XYZ","values":[1,2]}""")
        }
    }

    @Test
    fun decodeUnknownTypeThrows() {
        assertThrowsContains("Unknown message type") {
            TcpProtocol.decode("""{"type":"foo"}""")
        }
    }

    @Test
    fun decodeInvalidJsonThrows() {
        assertThrowsContains("Invalid JSON") { TcpProtocol.decode("not json") }
    }

    @Test
    fun decodeMissingTypeThrows() {
        assertThrowsContains("type") { TcpProtocol.decode("""{"id":"x"}""") }
    }

    @Test
    fun decodeMissingValuesThrowsForPoints() {
        assertThrowsContains("values") {
            TcpProtocol.decode("""{"type":"points","lead":"I"}""")
        }
    }

    @Test
    fun decodeOrNullReturnsNullOnBadInput() {
        assertNull(TcpProtocol.decodeOrNull("{}"))
        assertNull(TcpProtocol.decodeOrNull("not json"))
        assertNotNull(TcpProtocol.decodeOrNull("""{"type":"stop"}"""))
    }

    @Test
    fun decodeFramesParsesMultipleLinesAndSkipsBlanks() {
        val a = TcpProtocol.encode(TcpMessage.StartCommand())
        val b = TcpProtocol.encode(TcpMessage.PointsMessage(lead = Lead.I, values = listOf(1f, 2f)))
        val c = TcpProtocol.encode(TcpMessage.StopCommand())
        val out = TcpProtocol.decodeFrames(sequenceOf(a, "", "  ", b, c)).toList()
        assertEquals(3, out.size)
        assertTrue(out[0] is TcpMessage.StartCommand)
        assertTrue(out[1] is TcpMessage.PointsMessage)
        assertTrue(out[2] is TcpMessage.StopCommand)
    }

    private inline fun assertThrowsContains(needle: String, block: () -> Unit) {
        try {
            block()
            fail("expected TcpProtocolException containing '$needle'")
        } catch (e: TcpProtocolException) {
            assertTrue(
                "expected message to contain '$needle', was '${e.message}'",
                e.message?.contains(needle, ignoreCase = true) == true,
            )
        }
    }
}
