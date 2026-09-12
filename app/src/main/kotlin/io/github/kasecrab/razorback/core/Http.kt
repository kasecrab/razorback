package io.github.kasecrab.razorback.core

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val status: Int, message: String) : IOException(message)

/** Blocking HTTP over the platform client. Call from Dispatchers.IO. */
object Http {

    private const val CONNECT_MS = 20_000
    private const val READ_MS = 30_000

    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(request("GET", url, headers, null))

    fun postJson(url: String, headers: Map<String, String>, body: JSONObject): JSONObject =
        JSONObject(request("POST", url, headers, body.toString()))

    fun request(method: String, url: String, headers: Map<String, String>, body: String?): String {
        val conn = open(url, method, headers)
        try {
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            if (status >= 400) throw HttpException(status, errorMessage(conn, status))
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun open(url: String, method: String, headers: Map<String, String>): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_MS
        conn.readTimeout = READ_MS
        conn.useCaches = false
        // The key rides in a header; a redirect would carry it to wherever the redirect points.
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        return conn
    }

    /**
     * What to say about a request a vendor refused.
     *
     * Vendors put a sentence meant for a person at `error.message`, and that one is kept.
     * The rest of the body is not: it used to travel four hundred characters at a time
     * into logs that are on in release, into captions in front of the person and, through
     * a tool result, into the model's context and the database. None of those is a place
     * for whatever a server decided to echo back at us, and the status is what the app
     * decides anything on anyway.
     */
    fun errorMessage(conn: HttpURLConnection, status: Int): String {
        val raw = try {
            conn.errorStream?.bufferedReader()?.use { it.readText() }
        } catch (_: IOException) {
            null
        } ?: return "HTTP $status"
        val said = try {
            JSONObject(raw).obj("error")?.str("message")
        } catch (_: Exception) {
            null
        } ?: return "HTTP $status"
        return if (said.length > 400) said.take(400) + "…" else said.ifEmpty { "HTTP $status" }
    }
}

/** POST a body and hand every response line to [onLine] until it returns false or the stream ends. */
fun Http.stream(
    url: String,
    headers: Map<String, String>,
    body: String,
    handle: io.github.kasecrab.razorback.provider.StreamHandle,
    onHeaders: (HttpURLConnection) -> Unit = {},
    onLine: (String) -> Boolean,
) {
    val conn = open(url, "POST", headers)
    handle.connection = conn
    try {
        conn.readTimeout = 120_000
        conn.setRequestProperty("Accept", "text/event-stream")
        conn.setRequestProperty("Accept-Encoding", "identity")
        conn.setRequestProperty("Content-Type", "application/json")
        val bytes = body.toByteArray(Charsets.UTF_8)
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }
        val status = conn.responseCode
        if (status >= 400) throw HttpException(status, errorMessage(conn, status))
        onHeaders(conn)
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!onLine(line)) break
            }
        }
    } catch (e: IOException) {
        if (handle.cancelled) throw io.github.kasecrab.razorback.provider.StreamCancelled()
        throw e
    } finally {
        handle.connection = null
        conn.disconnect()
    }
}
