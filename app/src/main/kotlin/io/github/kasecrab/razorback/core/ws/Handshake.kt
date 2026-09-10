package io.github.kasecrab.razorback.core.ws

import java.util.Base64
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom

class HandshakeException(val status: Int, val body: String) : IOException("handshake failed: HTTP $status ${body.take(200)}")

/** The HTTP upgrade request and its reply. */
object Handshake {

    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val CRLF = "\r\n"
    private val random = SecureRandom()

    fun key(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun accept(key: String): String {
        val sha = MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(sha)
    }

    fun request(host: String, path: String, key: String, headers: Map<String, String>): ByteArray {
        val sb = StringBuilder(256)
        sb.append("GET ").append(path).append(" HTTP/1.1").append(CRLF)
        sb.append("Host: ").append(host).append(CRLF)
        sb.append("Upgrade: websocket").append(CRLF)
        sb.append("Connection: Upgrade").append(CRLF)
        sb.append("Sec-WebSocket-Key: ").append(key).append(CRLF)
        sb.append("Sec-WebSocket-Version: 13").append(CRLF)
        sb.append("User-Agent: razorback").append(CRLF)
        for ((k, v) in headers) sb.append(k).append(": ").append(v).append(CRLF)
        sb.append(CRLF)
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    class Response(val status: Int, val headers: Map<String, String>, val body: String)

    /** Reads status line and headers byte by byte so nothing past the blank line is consumed. */
    @Throws(IOException::class)
    fun readResponse(input: InputStream): Response {
        val head = StringBuilder(512)
        var tail = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("connection closed during handshake")
            head.append(b.toChar())
            tail = ((tail shl 8) or b) and 0xFFFFFFFF.toInt()
            if (tail == 0x0D0A0D0A) break
            if (head.length > 16384) throw IOException("handshake response too long")
        }
        val lines = head.toString().split(CRLF).filter { it.isNotEmpty() }
        val status = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: throw IOException("bad status line")
        val headers = HashMap<String, String>()
        var contentLength = 0
        for (l in lines.drop(1)) {
            val i = l.indexOf(':')
            if (i <= 0) continue
            val name = l.substring(0, i).trim().lowercase()
            val value = l.substring(i + 1).trim()
            headers[name] = value
            if (name == "content-length") contentLength = value.toIntOrNull() ?: 0
        }
        var body = ""
        if (status != 101 && contentLength > 0) {
            val buf = ByteArray(minOf(contentLength, 65536))
            var read = 0
            while (read < buf.size) {
                val n = input.read(buf, read, buf.size - read)
                if (n < 0) break
                read += n
            }
            body = String(buf, 0, read, Charsets.UTF_8)
        }
        return Response(status, headers, body)
    }

    @Throws(IOException::class)
    fun verify(response: Response, key: String) {
        if (response.status != 101) throw HandshakeException(response.status, response.body)
        if (response.headers["sec-websocket-accept"] != accept(key)) throw IOException("bad Sec-WebSocket-Accept")
    }
}
