package dev.manage.tool.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object JsonHttpClient {
    suspend fun postJson(url: URL, json: String, headers: Map<String, String> = emptyMap()): Int =
        withContext(Dispatchers.IO) {
            require(url.protocol == "https" || url.protocol == "http") { "Expected an HTTP or HTTPS URL" }
            val payload = json.toByteArray(Charsets.UTF_8)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
}
