package io.github.kasecrab.razorback.core.ws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class HandshakeTest {

    private val crlf = "\r\n"

    @Test
    fun acceptKeyMatchesRfcExample() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", Handshake.accept("dGhlIHNhbXBsZSBub25jZQ=="))
    }

    @Test
    fun requestHasUpgradeHeadersAndCustomOnes() {
        val req = String(Handshake.request("api.deepgram.com", "/v1/listen?model=nova-3", "abc", mapOf("Authorization" to "Token x")))
        assertTrue(req.startsWith("GET /v1/listen?model=nova-3 HTTP/1.1$crlf"))
        assertTrue(req.contains("Host: api.deepgram.com$crlf"))
        assertTrue(req.contains("Upgrade: websocket$crlf"))
        assertTrue(req.contains("Sec-WebSocket-Version: 13$crlf"))
        assertTrue(req.contains("Authorization: Token x$crlf"))
        assertTrue(req.endsWith("$crlf$crlf"))
    }

    @Test
    fun responseParsingStopsAtBlankLine() {
        val raw = "HTTP/1.1 101 Switching Protocols${crlf}Upgrade: websocket${crlf}Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=$crlf${crlf}A"
        val input = ByteArrayInputStream(raw.toByteArray(Charsets.ISO_8859_1))
        val r = Handshake.readResponse(input)
        assertEquals(101, r.status)
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", r.headers["sec-websocket-accept"])
        assertEquals('A'.code, input.read())
    }

    @Test
    fun rejectedUpgradeCarriesBody() {
        val raw = "HTTP/1.1 401 Unauthorized${crlf}Content-Length: 12$crlf${crlf}invalid auth"
        val r = Handshake.readResponse(ByteArrayInputStream(raw.toByteArray()))
        assertEquals(401, r.status)
        assertEquals("invalid auth", r.body)
        try {
            Handshake.verify(r, "k")
            throw AssertionError("expected failure")
        } catch (e: HandshakeException) {
            assertEquals(401, e.status)
        }
    }
}
