package io.github.kasecrab.razorback.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import io.github.kasecrab.razorback.chat.ChatEngine
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.Prefs
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.model.Role

/**
 * A spoken conversation: the mic streams to Flux, a finished turn goes to the model, the
 * reply streams sentence by sentence into Aura, and the speaker plays it. The person can
 * cut in at any time. Everything here runs on the main thread except the audio threads.
 */
class VoiceSession(
    private val context: Context,
    private val prefs: Prefs,
    private val secrets: Secrets,
    private val engine: ChatEngine,
) : SttLink.Listener, TtsLink.Listener, ChatEngine.Listener {

    enum class State { IDLE, CONNECTING, LISTENING, USER_SPEAKING, THINKING, SPEAKING, RECONNECTING, ERROR }

    interface Listener {
        fun onStateChanged(state: State)
        fun onUserText(text: String, final: Boolean)
        fun onAssistantText(text: String)
        fun onError(message: String)
    }

    var listener: Listener? = null
    var state: State = State.IDLE
        private set(value) {
            if (field == value) return
            field = value
            Log.d { "voice: $value" }
            listener?.onStateChanged(value)
        }

    val isActive: Boolean get() = state != State.IDLE && state != State.ERROR
    val inLevel: Float get() = mic.level.get() / 1000f
    val outLevel: Float get() = playback.level.get() / 1000f

    private val stt = SttLink({ secrets.get(Secrets.DEEPGRAM) }, { prefs[Keys.VOICE_STT_MODEL] })
    private val tts = TtsLink({ secrets.get(Secrets.DEEPGRAM) }, { prefs[Keys.VOICE_TTS_VOICE] }, { prefs[Keys.VOICE_SPEED] })
    private val mic = MicCapture(MediaRecorder.AudioSource.VOICE_COMMUNICATION) { buf, len -> stt.audio(buf, len) }
    private val playback = Playback { onDrained() }
    private val focus = AudioFocus(context) { stop() }
    private val chunker = SentenceChunker { sentence -> speak(sentence) }

    private var spokenChars = 0
    @Volatile private var replyIndex = -1
    private var awaitingFlush = false
    private var playbackStartedAt = 0L
    private var muted = false

    fun start() {
        if (isActive) return
        state = State.CONNECTING
        engine.addListener(this)
        stt.listener = this
        tts.listener = this
        focus.acquire()
        playback.start()
        stt.start()
        tts.warm()
        if (!mic.start()) {
            fail("Microphone unavailable")
            return
        }
    }

    fun stop() {
        if (state == State.IDLE) return
        engine.removeListener(this)
        if (engine.isStreaming && replyIndex >= 0) engine.stop()
        mic.stop()
        stt.stop()
        tts.stop()
        playback.stop()
        focus.release()
        chunker.reset()
        replyIndex = -1
        awaitingFlush = false
        state = State.IDLE
    }

    /** Debug builds only: feed a turn as if the person had said it, for phones and emulators without a usable mic. */
    fun injectTurn(text: String) {
        if (!io.github.kasecrab.razorback.BuildConfig.DEBUG || !isActive) return
        onTurn(SttLink.Turn.START, text, -1)
        onTurn(SttLink.Turn.END, text, -1)
    }

    fun setMuted(on: Boolean) {
        muted = on
        mic.muted.set(on || (state == State.SPEAKING && prefs[Keys.VOICE_MUTE_WHILE_SPEAKING]))
    }

    private fun fail(message: String) {
        listener?.onError(message)
        stop()
        state = State.ERROR
    }

    // Speech in

    override fun onConnected() {
        if (state == State.CONNECTING || state == State.RECONNECTING) state = State.LISTENING
    }

    override fun onTurn(kind: SttLink.Turn, transcript: String, turnIndex: Int) {
        when (kind) {
            SttLink.Turn.START -> {
                if (state == State.SPEAKING) {
                    if (echoGuard()) return
                    interrupt()
                }
                if (state == State.THINKING) interrupt()
                state = State.USER_SPEAKING
                listener?.onUserText(transcript, false)
            }
            SttLink.Turn.UPDATE, SttLink.Turn.EAGER_END, SttLink.Turn.RESUMED -> {
                if (state == State.USER_SPEAKING) listener?.onUserText(transcript, false)
            }
            SttLink.Turn.END -> {
                if (transcript.isBlank()) {
                    if (state == State.USER_SPEAKING) state = State.LISTENING
                    return
                }
                listener?.onUserText(transcript, true)
                ask(transcript)
            }
        }
    }

    override fun onDropped(reconnecting: Boolean) {
        if (state == State.LISTENING || state == State.USER_SPEAKING) state = State.RECONNECTING
    }

    override fun onError(message: String) = fail(message)

    /** Barge-in: the person started talking over the reply. */
    private fun interrupt() {
        tts.clear()
        playback.clear()
        chunker.reset()
        awaitingFlush = false
        if (engine.isStreaming) engine.stop()
        replyIndex = -1
    }

    /** Right after playback starts the mic may hear the speaker; ignore a quiet "start". */
    private fun echoGuard(): Boolean {
        val since = SystemClock.elapsedRealtime() - playbackStartedAt
        return since < 250 && mic.level.get() < 60
    }

    private var askedAt = 0L

    private fun ask(text: String) {
        askedAt = SystemClock.elapsedRealtime()
        chunker.reset()
        spokenChars = 0
        awaitingFlush = false
        state = State.THINKING
        replyIndex = engine.messages.size + 1
        engine.send(text)
    }

    // Model reply

    override fun onMessageChanged(index: Int, streaming: Boolean) {
        if (index != replyIndex) return
        val m = engine.messages.getOrNull(index) ?: return
        if (m.role != Role.ASSISTANT) return
        val content = m.content
        if (content.length > spokenChars) {
            chunker.push(content.substring(spokenChars))
            spokenChars = content.length
            listener?.onAssistantText(content)
        }
        if (!streaming) {
            chunker.flush()
            if (m.status == MessageStatus.ERROR) {
                listener?.onError(m.error ?: "The model did not answer")
                replyIndex = -1
                state = State.LISTENING
                return
            }
            awaitingFlush = true
            tts.flush()
            if (spokenChars == 0) {
                // Nothing to say; go straight back to listening.
                awaitingFlush = false
                replyIndex = -1
                state = State.LISTENING
            }
        }
    }

    override fun onStreamingChanged(streaming: Boolean) {
        if (!streaming && replyIndex >= 0 && engine.messages.getOrNull(replyIndex)?.status == MessageStatus.CUT && state == State.THINKING) {
            replyIndex = -1
            state = State.LISTENING
        }
    }

    private fun speak(sentence: String) {
        val text = SpeechText.strip(sentence)
        if (text.isBlank()) return
        tts.speak(text)
    }

    // Speech out

    /** Arrives on the socket reader thread: audio goes straight to the speaker, state hops to main. */
    override fun onAudio(data: ByteArray) {
        if (replyIndex < 0) return
        val first = !playback.isPlaying
        playback.enqueue(data)
        if (first) {
            context.mainExecutor.execute {
                if (playbackStartedAt < askedAt) {
                    playbackStartedAt = SystemClock.elapsedRealtime()
                    Log.d { "voice: first tts audio ${playbackStartedAt - askedAt} ms after the turn ended" }
                }
                if (state == State.THINKING) {
                    state = State.SPEAKING
                    if (prefs[Keys.VOICE_MUTE_WHILE_SPEAKING]) mic.muted.set(true)
                }
            }
        }
    }

    override fun onFlushed() {
        if (awaitingFlush) {
            awaitingFlush = false
            playback.markEnd()
        }
    }

    override fun onCleared() {}

    private fun onDrained() {
        context.mainExecutor.execute {
            if (state == State.SPEAKING) {
                replyIndex = -1
                mic.muted.set(muted)
                state = State.LISTENING
            }
        }
    }

    override fun onReset() {}
    override fun onMessageAdded(index: Int) {}
    override fun onMessageRemoved(index: Int) {}
}
