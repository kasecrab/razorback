package io.github.kasecrab.razorback.core.ws

import io.github.kasecrab.razorback.core.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A WebSocket over TLS with nothing but the platform. One reader thread delivers
 * callbacks on itself; every write is queued onto one writer thread, so callers on the
 * main thread never touch the network and the audio thread never waits on it.
 * [close] from elsewhere is the cancel path: the reader reports closed, not failed.
 */
class WebSocketClient(
    private val url: String,
    private val headers: Map<String, String>,
    private val listener: Listener,
) {
    interface Listener {
        fun onOpen(ws: WebSocketClient) {}
        fun onText(ws: WebSocketClient, text: String) {}
        fun onBinary(ws: WebSocketClient, data: ByteArray) {}
        fun onClosed(ws: WebSocketClient, code: Int, reason: String) {}
        fun onFailure(ws: WebSocketClient, error: Throwable) {}
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var out: OutputStream? = null
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "ws-writer").apply { isDaemon = true } }
    @Volatile private var closing = false
    @Volatile private var opened = false

    val isOpen: Boolean get() = opened && !closing

    /** Dial and upgrade on a new thread, then read until closed. */
    fun connect() {
        val t = Thread({ run() }, "ws-reader")
        t.isDaemon = true
        t.start()
    }

    private fun run() {
        try {
            open()
        } catch (e: Exception) {
            val wasClosing = closing
            cleanup()
            writer.shutdown()
            if (!wasClosing) listener.onFailure(this, e)
            return
        }
        readLoop()
    }

    @Throws(IOException::class)
    private fun open() {
        val uri = URI(url)
        val host = uri.host ?: throw IOException("no host in $url")
        val secure = uri.scheme == "wss"
        val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
        val path = (uri.rawPath.ifEmpty { "/" }) + (uri.rawQuery?.let { "?$it" } ?: "")
        val plain = Socket()
        plain.tcpNoDelay = true
        plain.connect(InetSocketAddress(host, port), CONNECT_MS)
        socket = plain
        val s: Socket = if (secure) {
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, host, port, true) as SSLSocket
            val params = ssl.sslParameters
            params.endpointIdentificationAlgorithm = "HTTPS"
            ssl.sslParameters = params
            ssl.soTimeout = HANDSHAKE_MS
            ssl.startHandshake()
            ssl
        } else {
            plain.soTimeout = HANDSHAKE_MS
            plain
        }
        socket = s
        val key = Handshake.key()
        val output = BufferedOutputStream(s.getOutputStream(), 16 * 1024)
        val hostHeader = if (port == 443 || port == 80) host else "$host:$port"
        output.write(Handshake.request(hostHeader, path, key, headers))
        output.flush()
        val response = Handshake.readResponse(s.getInputStream())
        Handshake.verify(response, key)
        s.soTimeout = 0
        out = output
        opened = true
        listener.onOpen(this)
    }

    private fun readLoop() {
        val s = socket ?: return
        val input = DataInputStream(s.getInputStream().buffered(16 * 1024))
        var fragments: ByteArrayOutputStream? = null
        var fragmentOpcode = 0
        try {
            while (!closing) {
                val h = Frame.readHeader(input)
                val payload = Frame.readPayload(input, h)
                when (h.opcode) {
                    Frame.TEXT, Frame.BINARY -> {
                        if (h.fin) {
                            deliver(h.opcode, payload)
                        } else {
                            fragmentOpcode = h.opcode
                            fragments = ByteArrayOutputStream(payload.size * 2).also { it.write(payload) }
                        }
                    }
                    Frame.CONTINUATION -> {
                        val f = fragments ?: throw IOException("continuation without start")
                        f.write(payload)
                        if (f.size() > Frame.MAX_PAYLOAD) throw IOException("message too large")
                        if (h.fin) {
                            fragments = null
                            deliver(fragmentOpcode, f.toByteArray())
                        }
                    }
                    Frame.PING -> enqueue(Frame.PONG, payload)
                    Frame.PONG -> {}
                    Frame.CLOSE -> {
                        val code = Frame.closeCode(payload)
                        val reason = if (payload.size > 2) String(payload, 2, payload.size - 2, Charsets.UTF_8) else ""
                        if (!closing) {
                            closing = true
                            try {
                                write(Frame.CLOSE, Frame.closePayload(code))
                            } catch (_: IOException) {
                            }
                        }
                        cleanup()
                        writer.shutdown()
                        listener.onClosed(this, code, reason)
                        return
                    }
                    else -> throw IOException("unknown opcode ${h.opcode}")
                }
            }
            cleanup()
            writer.shutdown()
            listener.onClosed(this, 1000, "")
        } catch (e: IOException) {
            val wasClosing = closing
            cleanup()
            writer.shutdown()
            if (wasClosing) listener.onClosed(this, 1000, "") else listener.onFailure(this, e)
        }
    }

    private fun deliver(opcode: Int, payload: ByteArray) {
        if (opcode == Frame.TEXT) listener.onText(this, String(payload, Charsets.UTF_8)) else listener.onBinary(this, payload)
    }

    /** Queues a text frame; safe from any thread, never blocks the caller. */
    fun sendText(text: String) = enqueue(Frame.TEXT, text.toByteArray(Charsets.UTF_8))

    /** Queues a binary frame. The bytes are copied, so the caller may reuse its buffer at once. */
    fun sendBinary(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) =
        enqueue(Frame.BINARY, data.copyOfRange(offset, offset + length))

    private fun enqueue(opcode: Int, payload: ByteArray) {
        if (closing) return
        try {
            writer.execute {
                try {
                    write(opcode, payload)
                } catch (e: IOException) {
                    Log.d { "ws write failed: ${e.message}" }
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    @Throws(IOException::class)
    private fun write(opcode: Int, payload: ByteArray) {
        val o = out ?: throw IOException("not connected")
        Frame.write(o, opcode, payload)
        o.flush()
    }

    /**
     * Ask to close and stop waiting: a close frame goes out if it can within 150 ms, then
     * the socket is torn down regardless of what the server does. Returns at once.
     */
    fun close(code: Int = 1000, reason: String = "") {
        if (closing) return
        closing = true
        try {
            writer.execute {
                try {
                    socket?.soTimeout = CLOSE_MS
                    write(Frame.CLOSE, Frame.closePayload(code, reason))
                } catch (e: Exception) {
                    Log.d { "close frame not sent: ${e.message}" }
                }
                cleanup()
            }
            writer.shutdown()
        } catch (_: RejectedExecutionException) {
            cleanup()
        }
    }

    private fun cleanup() {
        opened = false
        val s = socket
        socket = null
        out = null
        try {
            s?.close()
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val CONNECT_MS = 5000
        const val HANDSHAKE_MS = 5000
        const val CLOSE_MS = 150
    }
}
