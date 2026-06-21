package com.example.cardiosimulator.network

import com.example.cardiosimulator.data.wfdb.WfdbHeaderParser
import com.example.cardiosimulator.data.wfdb.WfdbReader
import com.example.cardiosimulator.data.wfdb.WfdbRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class PhysioNetException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PhysioNetClient {
    private const val BASE_URL = "https://physionet.org/files/"
    private const val USER_AGENT = "CardioSimulator/1.0 (Android; alexandr.beresov@gmail.com)"

    suspend fun downloadRecord(projectPath: String, recordName: String): WfdbRecord = withContext(Dispatchers.IO) {
        val sanitizedProjectPath = projectPath.trim('/')
        val baseUrl = "$BASE_URL$sanitizedProjectPath/$recordName"
        
        try {
            val heaBytes = downloadFile("$baseUrl.hea")
            val heaContent = String(heaBytes)
            val header = WfdbHeaderParser.parse(heaContent)
            
            val bytesMap = mutableMapOf<String, ByteArray>()
            bytesMap["$recordName.hea"] = heaBytes
            
            // Download each distinct signal file
            val signalFiles = header.signals.map { it.fileName }.distinct()
            for (fileName in signalFiles) {
                val signalFileUrl = "$BASE_URL$sanitizedProjectPath/$fileName"
                bytesMap[fileName] = downloadFile(signalFileUrl)
            }
            
            WfdbReader.readRecord(header) { fileName ->
                bytesMap[fileName] ?: throw PhysioNetException("File $fileName not downloaded")
            }
        } catch (e: Exception) {
            if (e is PhysioNetException) throw e
            throw PhysioNetException("Failed to download record $recordName from $projectPath: ${e.message}", e)
        }
    }

    private fun downloadFile(urlString: String): ByteArray {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw PhysioNetException("Server returned HTTP $responseCode for $urlString")
        }
        
        return connection.inputStream.use { it.readBytes() }
    }
}
