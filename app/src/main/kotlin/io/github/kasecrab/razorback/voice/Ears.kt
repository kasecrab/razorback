package io.github.kasecrab.razorback.voice

/**
 * Whatever turns the microphone into turns of speech for voice mode. Flux does it with
 * turn detection built in; Nova does it with endpointing and the app's own bookkeeping.
 */
interface Ears {

    enum class Turn { START, UPDATE, EAGER_END, RESUMED, END }

    interface Listener {
        fun onConnected()
        /** [confidence] is the transcriber's own, 0..1, or 1 when it gives none. */
        fun onTurn(kind: Turn, transcript: String, turnIndex: Int, confidence: Float)
        fun onDropped(reconnecting: Boolean)
        fun onError(message: String)
    }

    var listener: Listener?
    val isOpen: Boolean

    fun start()

    /** From the audio thread; silently dropped while the socket is down. */
    fun audio(chunk: ByteArray, len: Int)

    /** Keeps the stream open while no audio is being sent, as while the mic is muted. */
    fun keepAlive() {}

    fun stop()
}
