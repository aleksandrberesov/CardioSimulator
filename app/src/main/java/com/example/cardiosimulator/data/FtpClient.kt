package com.example.cardiosimulator.data

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class FtpException(message: String) : IOException(message)

class FtpClient(
    private val host: String,
    private val port: Int = 21,
    private val username: String,
    private val password: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) {
    private data class Reply(val code: Int, val message: String)

    fun upload(remotePath: String, payload: ByteArray) {
        Socket().use { control ->
            control.connect(InetSocketAddress(host, port), connectTimeoutMs)
            control.soTimeout = readTimeoutMs
            val reader = BufferedReader(InputStreamReader(control.getInputStream(), Charsets.US_ASCII))
            val writer = OutputStreamWriter(control.getOutputStream(), Charsets.US_ASCII)

            require(readReply(reader).code == 220) { "FTP server did not greet with 220" }

            send(writer, "USER $username")
            val userResp = readReply(reader)
            when (userResp.code) {
                230 -> Unit
                331, 332 -> {
                    send(writer, "PASS $password")
                    val passResp = readReply(reader)
                    if (passResp.code != 230 && passResp.code != 202) {
                        throw FtpException("Login failed (${passResp.code}): ${passResp.message}")
                    }
                }
                else -> throw FtpException("USER rejected (${userResp.code}): ${userResp.message}")
            }

            send(writer, "TYPE I")
            expect(readReply(reader), 200)

            send(writer, "PASV")
            val pasv = readReply(reader)
            if (pasv.code != 227) throw FtpException("PASV failed (${pasv.code}): ${pasv.message}")
            val (dataHost, dataPort) = parsePasv(pasv.message)

            Socket().use { data ->
                data.connect(InetSocketAddress(dataHost, dataPort), connectTimeoutMs)
                data.soTimeout = readTimeoutMs
                send(writer, "STOR $remotePath")
                val storResp = readReply(reader)
                if (storResp.code != 150 && storResp.code != 125) {
                    throw FtpException("STOR rejected (${storResp.code}): ${storResp.message}")
                }
                data.getOutputStream().use { out ->
                    out.write(payload)
                    out.flush()
                }
            }
            val transferResp = readReply(reader)
            if (transferResp.code != 226 && transferResp.code != 250) {
                throw FtpException("Transfer not confirmed (${transferResp.code}): ${transferResp.message}")
            }

            runCatching {
                send(writer, "QUIT")
                readReply(reader)
            }
        }
    }

    private fun send(writer: OutputStreamWriter, line: String) {
        writer.write(line)
        writer.write("\r\n")
        writer.flush()
    }

    private fun readReply(reader: BufferedReader): Reply {
        val first = reader.readLine() ?: throw FtpException("FTP control connection closed")
        if (first.length < 3) throw FtpException("Malformed FTP reply: $first")
        val code = first.substring(0, 3).toIntOrNull()
            ?: throw FtpException("Malformed FTP code: $first")
        val sb = StringBuilder(first.substring(3).trimStart(' ', '-'))
        if (first.length > 3 && first[3] == '-') {
            val terminator = "$code "
            while (true) {
                val line = reader.readLine() ?: break
                sb.append('\n').append(line)
                if (line.startsWith(terminator)) break
            }
        }
        return Reply(code, sb.toString())
    }

    private fun expect(reply: Reply, code: Int) {
        if (reply.code != code) throw FtpException("Expected $code, got ${reply.code}: ${reply.message}")
    }

    private fun parsePasv(message: String): Pair<String, Int> {
        val open = message.indexOf('(')
        val close = message.indexOf(')', startIndex = open + 1)
        if (open < 0 || close < 0) throw FtpException("Malformed PASV response: $message")
        val nums = message.substring(open + 1, close)
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
        if (nums.size != 6) throw FtpException("Malformed PASV tuple: $message")
        val parsedHost = nums.subList(0, 4).joinToString(".")
        val parsedPort = (nums[4] shl 8) or nums[5]
        return parsedHost to parsedPort
    }
}
