package io.github.kasecrab.razorback.voice

/**
 * Whatever turns the microphone into turns of speech for voice mode. Flux does it with
 * turn detection built in; Nova does it with endpointing and the app's own bookkeeping.
 */
interface Ears {

    enum class Turn { START, UPDATE, EAGER_END, RESUMED, END }

    interface Listener {
        fun onConnected()
        fun onTurn(kind: Turn, transcript: String, turnIndex: Int)
        fun onDropped(reconnecting: Boolean)
        fun onError(message: String)
    }

    var listener: Listener?
    val isOpen: Boolean

    fun start()

    /** From the audio thread; silently dropped while the socket is down. */
    fun audio(chunk: ByteArray, len: Int)

    fun stop()
}
