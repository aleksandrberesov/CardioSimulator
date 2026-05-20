package com.example.cardiosimulator.cli

import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.network.TcpMessage
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """
cardiosim-test — test client for the CardioSimulator TCP protocol

Usage:
  cardiosim-test upload   --host H --port P --file FILE [--id ID]
  cardiosim-test points   --host H --port P --lead LEAD --csv FILE
                          [--offset N] [--identy STR] [--id ID]
  cardiosim-test points   --host H --port P --lead LEAD --values V1,V2,...
                          [--offset N] [--identy STR] [--id ID]
  cardiosim-test start    --host H --port P [--rate N] [--param k=v]... [--id ID]
  cardiosim-test stop     --host H --port P [--id ID]
  cardiosim-test listen   --host H --port P [--timeout-ms N]

Common:
  --host H            server hostname or IP
  --port P            server TCP port
  --id ID             optional message id (echoed by server in ack)

upload waits for the server's ack frame and prints it.
listen prints every received frame, one per line, until EOF or Ctrl-C.
"""

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsageAndExit(0)
    }
    val command = args[0]
    val rest = args.drop(1).toTypedArray()
    try {
        when (command) {
            "upload"   -> cmdUpload(Args(rest))
            "points"   -> cmdPoints(Args(rest))
            "start"    -> cmdStart(Args(rest))
            "stop"     -> cmdStop(Args(rest))
            "listen"   -> cmdListen(Args(rest))
            "-h", "--help", "help" -> printUsageAndExit(0)
            else -> {
                System.err.println("unknown command: $command")
                printUsageAndExit(2)
            }
        }
    } catch (e: IllegalStateException) {
        System.err.println("error: ${e.message}")
        exitProcess(2)
    } catch (e: Exception) {
        System.err.println("error: ${e.message ?: e.javaClass.simpleName}")
        exitProcess(1)
    }
}

private fun printUsageAndExit(code: Int): Nothing {
    println(USAGE.trim())
    exitProcess(code)
}

private fun connectArgs(a: Args): Pair<String, Int> {
    val host = a.req("host")
    val port = a.intOpt("port") ?: error("missing required --port")
    return host to port
}

private fun cmdUpload(a: Args) {
    val (host, port) = connectArgs(a)
    val file = File(a.req("file"))
    if (!file.isFile) error("file not found: ${file.absolutePath}")
    val id = a.opt("id")

    println("connecting to $host:$port…")
    TcpClient(host, port, readTimeoutMs = 30_000).use { client ->
        println("uploading ${file.name} (${file.length()} bytes)…")
        client.sendUpload(file, id = id)
        val reply = client.readMessage()
        when (reply) {
            is TcpMessage.AckMessage -> println(
                "ack: filename=${reply.filename} bytes=${reply.bytes}" +
                    (reply.id?.let { " id=$it" } ?: "")
            )
            null -> {
                System.err.println("no ack: connection closed by peer")
                exitProcess(1)
            }
            else -> {
                System.err.println("unexpected reply: $reply")
                exitProcess(1)
            }
        }
    }
}

private fun cmdPoints(a: Args) {
    val (host, port) = connectArgs(a)
    val leadToken = a.req("lead")
    val lead = Lead.fromToken(leadToken)
        ?: error("unknown lead '$leadToken'. Valid: ${Lead.entries.joinToString { it.name }}")
    val offset = a.intOpt("offset") ?: 0
    val identy = a.opt("identy")
    val id = a.opt("id")

    val values: List<Float> = when {
        a.opt("csv") != null -> readCsvValues(File(a.req("csv")))
        a.opt("values") != null -> a.req("values").split(',').map { it.trim().toFloat() }
        else -> error("provide either --csv FILE or --values V1,V2,...")
    }
    if (values.isEmpty()) error("no sample values to send")

    val msg = TcpMessage.PointsMessage(
        id = id,
        lead = lead,
        identy = identy,
        offset = offset,
        values = values,
    )

    println("connecting to $host:$port…")
    TcpClient(host, port).use { client ->
        client.sendMessage(msg)
        println("sent ${values.size} samples on lead=${lead.name}" +
            (identy?.let { " identy=$it" } ?: "") +
            (if (offset != 0) " offset=$offset" else ""))
    }
}

private fun cmdStart(a: Args) {
    val (host, port) = connectArgs(a)
    val rate = a.intOpt("rate")
    val params = a.all("param").associate {
        val eq = it.indexOf('=')
        if (eq <= 0) error("--param expects k=v, got '$it'")
        it.substring(0, eq) to it.substring(eq + 1)
    }
    val msg = TcpMessage.StartCommand(id = a.opt("id"), sampleRate = rate, params = params)
    println("connecting to $host:$port…")
    TcpClient(host, port).use { client ->
        client.sendMessage(msg)
        println("sent: start" +
            (rate?.let { " rate=$it" } ?: "") +
            (if (params.isNotEmpty()) " params=$params" else ""))
    }
}

private fun cmdStop(a: Args) {
    val (host, port) = connectArgs(a)
    val msg = TcpMessage.StopCommand(id = a.opt("id"))
    println("connecting to $host:$port…")
    TcpClient(host, port).use { client ->
        client.sendMessage(msg)
        println("sent: stop")
    }
}

private fun cmdListen(a: Args) {
    val (host, port) = connectArgs(a)
    val timeoutMs = a.intOpt("timeout-ms") ?: 0
    println("listening on $host:$port (Ctrl-C to stop)…")
    TcpClient(host, port, readTimeoutMs = timeoutMs).use { client ->
        while (true) {
            val line = client.readLine() ?: break
            println(line)
        }
        println("(peer closed connection)")
    }
}

/**
 * Reads ECG samples from a CSV file. Accepts either one number per line
 * or comma-separated rows. Blank lines and lines starting with `#` are
 * skipped.
 */
private fun readCsvValues(file: File): List<Float> {
    if (!file.isFile) error("csv file not found: ${file.absolutePath}")
    val out = mutableListOf<Float>()
    file.useLines { lines ->
        for (raw in lines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            for (cell in trimmed.split(',')) {
                val c = cell.trim()
                if (c.isEmpty()) continue
                out += c.toFloatOrNull()
                    ?: error("not a number in ${file.name}: '$c'")
            }
        }
    }
    return out
}
