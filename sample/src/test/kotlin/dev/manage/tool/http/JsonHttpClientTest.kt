package dev.manage.tool.http

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

class JsonHttpClientTest {
    @Test fun postsUtf8JsonAndReturnsServerStatus() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val received = AtomicReference<List<String>>()
        server.createContext("/events") { exchange ->
            exchange.use {
                received.set(listOf(it.requestMethod, it.requestHeaders.getFirst("Content-Type"),
                    it.requestHeaders.getFirst("X-Source"), it.requestBody.bufferedReader(Charsets.UTF_8).readText()))
                it.sendResponseHeaders(429, -1)
            }
        }
        server.start()
        try {
            val url = URL("http://127.0.0.1:${server.address.port}/events")
            val json = """{"event":"点击"}"""
            assertEquals(429, JsonHttpClient.postJson(url, json, mapOf("X-Source" to "sample")))
            assertEquals(listOf("POST", "application/json; charset=utf-8", "sample", json), received.get())
        } finally {
            server.stop(0)
        }
    }
}
