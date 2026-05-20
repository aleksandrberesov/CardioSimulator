package com.example.cardiosimulator.cli

import com.example.cardiosimulator.network.TcpMessage
import com.example.cardiosimulator.network.TcpProtocol
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal blocking TCP client for the CardioSimulator protocol.
 *
 * One instance owns a single socket. The protocol uses line-delimited JSON
 * for control frames and mixed framing for `upload` (JSON header line +
 * raw payload bytes) — see docs/tcp-protocol.md in the repo root.
 */
class TcpClient(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 0,
) : AutoCloseable {

    private val socket = Socket().apply {
        connect(InetSocketAddress(host, port), connectTimeoutMs)
        soTimeout = readTimeoutMs
    }

    private val out: OutputStream = socket.getOutputStream()
    private val inStream: InputStream = socket.getInputStream()
    private val reader: BufferedReader = inStream.bufferedReader(Charsets.UTF_8)

    fun sendMessage(msg: TcpMessage) {
        val line = TcpProtocol.encode(msg) + "\n"
        out.write(line.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /**
     * Sends an `upload` header then streams the file's raw bytes. Does NOT
     * wait for the ack — call [readMessage] afterwards if you need it.
     */
    fun sendUpload(file: File, id: String? = null) {
        val header = TcpMessage.UploadMessage(
            id = id,
            filename = file.name,
            size = file.length(),
        )
        val headerLine = TcpProtocol.encode(header) + "\n"
        out.write(headerLine.toByteArray(Charsets.UTF_8))
        out.flush()
        file.inputStream().use { input ->
            input.copyTo(out, bufferSize = 64 * 1024)
        }
        out.flush()
    }

    /** Reads one JSON-line frame, decodes via [TcpProtocol], or returns null on EOF. */
    fun readMessage(): TcpMessage? {
        val line = reader.readLine() ?: return null
        return TcpProtocol.decodeOrNull(line)
    }

    /** Reads one raw line (un-decoded). For listen mode. */
    fun readLine(): String? = reader.readLine()

    override fun close() {
        try { socket.close() } catch (_: Exception) {}
    }
}
