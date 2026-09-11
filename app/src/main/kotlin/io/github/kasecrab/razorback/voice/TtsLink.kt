package io.github.kasecrab.razorback.voice

import android.os.Handler
import android.os.Looper
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.core.ws.HandshakeException
import io.github.kasecrab.razorback.core.ws.WebSocketClient
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Deepgram Aura streaming text to speech. Sentences go in as they finish; audio comes
 * back as raw PCM. Text sent before the socket is up waits in a queue.
 */
class TtsLink(
    private val key: () -> String?,
    private val model: () -> String,
    private val speed: () -> Float,
) {
    /**
     * [onAudio] and [onFlushed] arrive on the socket thread, in the order the voice sent
     * them, so a listener can tell exactly which audio belongs to which sentence. The rest
     * arrives on the main thread.
     */
    interface Listener {
        fun onAudio(data: ByteArray)
        fun onFlushed()
        fun onCleared()
        fun onError(message: String)

        /** The voice in use takes no speed parameter; the phone has to pace it instead. */
        fun onSpeedUnavailable() {}
    }

    var listener: Listener? = null
    val isOpen: Boolean get() = ws?.isOpen == true

    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocketClient? = null
    private val queue = ArrayList<String>()
    @Volatile private var armed = false
    private var backoffMs = 400L
    /** Set once a voice refused the speed parameter, and cleared when the voice changes. */
    private var speedRefused = false
    private var speedSent = false
    private var lastModel = ""

    fun warm() {
        if (armed) return
        armed = true
        dial()
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        send(json("Speak", text))
    }

    fun flush() = send("{\"type\":\"Flush\"}")

    /** Drop everything queued upstream; for barge-in. */
    fun clear() {
        synchronized(queue) { queue.clear() }
        val socket = ws
        if (socket != null && socket.isOpen) {
            try {
                socket.sendText("{\"type\":\"Clear\"}")
            } catch (e: IOException) {
                Log.d { "clear failed: ${e.message}" }
            }
        }
    }

    fun stop() {
        armed = false
        synchronized(queue) { queue.clear() }
        val socket = ws
        ws = null
        if (socket != null) {
            if (socket.isOpen) socket.sendText("{\"type\":\"Close\"}")
            socket.close()
        }
    }

    private fun send(frame: String) {
        if (!armed) warm()
        val socket = ws
        if (socket == null || !socket.isOpen) {
            synchronized(queue) { queue.add(frame) }
            if (socket == null) dial()
            return
        }
        try {
            socket.sendText(frame)
        } catch (e: IOException) {
            Log.w("tts send failed: ${e.message}")
            synchronized(queue) { queue.add(frame) }
        }
    }

    private fun json(type: String, text: String): String {
        val o = JSONObject()
        o.put("type", type)
        o.put("text", text)
        return o.toString()
    }

    private fun url(): String {
        val m = model()
        if (m != lastModel) {
            lastModel = m
            speedRefused = false
        }
        val sb = StringBuilder("wss://api.deepgram.com/v1/speak?model=")
        sb.append(URLEncoder.encode(m, "UTF-8"))
        sb.append("&encoding=linear16&sample_rate=").append(Playback.SAMPLE_RATE)
        val s = if (speedRefused) 1f else Speed.server(speed())
        speedSent = s != 1f
        if (speedSent) sb.append("&speed=").append(String.format(java.util.Locale.US, "%.2f", s))
        return sb.toString()
    }

    private fun dial() {
        if (ws != null) return
        val apiKey = key()
        if (apiKey == null) {
            main.post { listener?.onError("no Deepgram key") }
            armed = false
            return
        }
        val socket = WebSocketClient(url(), mapOf("Authorization" to "Token $apiKey"), object : WebSocketClient.Listener {
            override fun onOpen(ws: WebSocketClient) {
                backoffMs = 400L
                val pending = synchronized(queue) { ArrayList(queue).also { queue.clear() } }
                for (f in pending) {
                    try {
                        ws.sendText(f)
                    } catch (e: IOException) {
                        Log.w("tts replay failed: ${e.message}")
                    }
                }
            }

            override fun onBinary(ws: WebSocketClient, data: ByteArray) {
                // A socket that stop() let go of may still drain for a moment; its audio is not ours.
                if (this@TtsLink.ws !== ws) return
                listener?.onAudio(data)
            }

            override fun onText(ws: WebSocketClient, text: String) {
                if (this@TtsLink.ws !== ws) return
                val type = try {
                    JSONObject(text).str("type")
                } catch (_: JSONException) {
                    null
                }
                when (type) {
                    "Flushed" -> listener?.onFlushed()
                    "Cleared" -> main.post { listener?.onCleared() }
                    "Warning", "Error" -> Log.w("tts: $text")
                }
            }

            override fun onClosed(ws: WebSocketClient, code: Int, reason: String) = dropped(ws, null)

            override fun onFailure(ws: WebSocketClient, error: Throwable) = dropped(ws, error)
        })
        ws = socket
        socket.connect()
    }

    /** Deepgram puts the reason at err_msg; the status is the fallback. */
    private fun refusal(e: HandshakeException): String = try {
        org.json.JSONObject(e.body).optString("err_msg").ifEmpty { "HTTP ${e.status}" }
    } catch (_: org.json.JSONException) {
        "HTTP ${e.status}"
    }

    private fun dropped(socket: WebSocketClient, error: Throwable?) {
        if (ws !== socket) return
        ws = null
        if (!armed) return
        if (error is HandshakeException && (error.status == 401 || error.status == 403)) {
            armed = false
            main.post { listener?.onError("Deepgram refused the key") }
            return
        }
        if (error is HandshakeException && error.status == 400 && speedSent && !speedRefused) {
            // Older voices take no speed parameter; say the same words at their own pace.
            speedRefused = true
            Log.w("tts: ${lastModel} refused the speed parameter, retrying without it")
            main.post { listener?.onSpeedUnavailable() }
            dial()
            return
        }
        if (error is HandshakeException && error.status in 400..499) {
            // Anything else the service refuses is said out loud rather than retried in silence.
            armed = false
            main.post { listener?.onError("Deepgram: " + refusal(error)) }
            return
        }
        // Reconnect straight away if something is waiting; otherwise the next speak() dials.
        val waiting = synchronized(queue) { queue.isNotEmpty() }
        if (waiting) {
            val wait = backoffMs
            backoffMs = minOf(backoffMs * 2, 8000L)
            main.postDelayed({ if (armed && ws == null) dial() }, wait)
        }
    }
}
