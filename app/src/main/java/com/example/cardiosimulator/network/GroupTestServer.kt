package com.example.cardiosimulator.network

import com.example.cardiosimulator.domain.*
import com.example.cardiosimulator.domain.generators.QuizOptionDto
import com.example.cardiosimulator.domain.generators.QuizQuestionDto
import com.example.cardiosimulator.domain.generators.TestGenerator
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class GroupTestServer(
    port: Int,
    private val generateTest: (String, String) -> Test,
    private val resolveImage: (String) -> File?,
    private val onResult: (ExamResult) -> Unit
) : NanoHTTPD("0.0.0.0", port) {

    companion object {
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address) return addr.hostAddress
                    }
                }
            } catch (e: Exception) {}
            return null
        }
    }

    data class Participant(
        val student: ExamStudentInfo,
        val test: Test,
        var result: ExamResult? = null
    )

    private val participants = ConcurrentHashMap<String, Participant>()
    private val json = Json { ignoreUnknownKeys = true }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            method == Method.GET && uri == "/" -> 
                newFixedLengthResponse(Response.Status.OK, "text/html", GroupQuizPage.Html)
            
            method == Method.POST && uri == "/api/register" -> handleRegister(session)
            
            method == Method.GET && uri == "/api/image" -> handleImage(session)
            
            method == Method.POST && uri == "/api/submit" -> handleSubmit(session)
            
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun handleRegister(session: IHTTPSession): Response {
        return try {
            val map = mutableMapOf<String, String>()
            session.parseBody(map)
            val body = map["postData"] ?: ""
            val reg = json.decodeFromString<RegisterRequest>(body)
            
            val token = UUID.randomUUID().toString()
            val test = generateTest(reg.fullName, reg.group)
            
            participants[token] = Participant(
                student = ExamStudentInfo(reg.fullName, reg.group),
                test = test
            )
            
            val response = RegisterResponse(
                token = token,
                questions = TestGenerator.toPublicDto(test)
            )
            newFixedLengthResponse(Response.Status.OK, "application/json", Json.encodeToString(response))
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", e.message)
        }
    }

    private fun handleImage(session: IHTTPSession): Response {
        val qid = session.parameters["qid"]?.firstOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing qid")
        val file = resolveImage(qid) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        
        return try {
            val fis = FileInputStream(file)
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "application/octet-stream"
            }
            newChunkedResponse(Response.Status.OK, mime, fis)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message)
        }
    }

    private fun handleSubmit(session: IHTTPSession): Response {
        return try {
            val map = mutableMapOf<String, String>()
            session.parseBody(map)
            val body = map["postData"] ?: ""
            val sub = json.decodeFromString<SubmitRequest>(body)
            
            val p = participants[sub.token] ?: return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Invalid token")
            
            val result = ExamGrader.grade(p.test, sub.selections, p.student)
            p.result = result
            onResult(result)
            
            val response = SubmitResponse(
                correct = result.correctCount,
                total = result.totalCount,
                passed = result.passed
            )
            newFixedLengthResponse(Response.Status.OK, "application/json", Json.encodeToString(response))
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", e.message)
        }
    }

    fun getParticipants() = participants.values.toList()

    @Serializable
    data class RegisterRequest(val fullName: String, val group: String)

    @Serializable
    data class RegisterResponse(val token: String, val questions: List<QuizQuestionDto>)

    @Serializable
    data class SubmitRequest(val token: String, val selections: Map<String, String>)

    @Serializable
    data class SubmitResponse(val correct: Int, val total: Int, val passed: Boolean)
}
