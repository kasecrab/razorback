package io.github.kasecrab.razorback.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import io.github.kasecrab.razorback.chat.ChatEngine
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.Net
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
) : Ears.Listener, TtsLink.Listener, ChatEngine.Listener {

    enum class State { IDLE, CONNECTING, LISTENING, USER_SPEAKING, THINKING, SEARCHING, SPEAKING, RECONNECTING, ERROR }

    interface Listener {
        fun onStateChanged(state: State)
        fun onUserText(text: String, final: Boolean)
        /** A turn has gone to the model; what follows in [onSentence] is its reply. */
        fun onReplyStarted()
        /** One sentence, in the form the voice will say it, handed to the speaker. */
        fun onSentence(spoken: String)
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

    private var stt: Ears = ears()
    private val tts = TtsLink({ secrets.get(Secrets.DEEPGRAM) }, { prefs[Keys.VOICE_TTS_VOICE] }, { prefs[Keys.VOICE_SPEED] })
    private var mic = MicCapture(MediaRecorder.AudioSource.VOICE_COMMUNICATION) { buf, len -> hear(buf, len) }
    private val playback = Playback { onDrained() }
    private val focus = AudioFocus(context) { stop() }
    private val chunker = SentenceChunker { sentence -> speak(sentence) }

    /** Where the voice is in the reply being read; the screen asks it every frame while speaking. */
    val clock = SpeechClock()

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private var spokenChars = 0
    @Volatile private var replyIndex = -1
    private var streamDone = false
    /** The model asked for a tool; the answer comes in a later reply of the same turn. */
    private var toolRound = false
    /** A cue has been said and nothing else is queued; when it has played, the wait goes on quietly. */
    private var cuePending = false
    private var cues = 0
    private var thinkCues = 0
    private val slowThinking = Runnable {
        if (replyIndex >= 0 && state == State.THINKING && spokenChars == 0 && clock.sentenceCount == 0 && unflushed.isEmpty()) {
            cue(THINK_CUES[thinkCues++ % THINK_CUES.size])
        }
    }
    private var replyStartBytes = 0L
    @Volatile private var playbackStartedAt = 0L
    /** Audio has arrived for the stretch of speech now playing; cleared when the speaker goes quiet. */
    @Volatile private var audioArrived = false
    @Volatile private var playbackEndedAt = 0L
    private var muted = false
    /** A finished turn that arrived while the previous reply was still being cancelled. */
    private var pendingTurn: String? = null
    /** Words the voice has been given this reply, to tell the speaker's echo from the person. */
    private val spokenWords = HashSet<String>(256)
    private val spokenStems = HashSet<String>(256)
    /** Speech heard over the reply that has not yet proved to be the person rather than the speaker. */
    private var tentative = false
    /** Text the voice has been given but not yet told to say; see [paceFlush]. */
    private val unflushed = StringBuilder()
    private val flushLater = Runnable { paceFlush(overdue = true) }

    /** Which service listens, from settings: Nova for accuracy, Flux for the quickest turn-taking. */
    private fun ears(): Ears {
        val m = prefs[Keys.VOICE_STT_MODEL]
        return if (m.startsWith("flux")) {
            SttLink({ secrets.get(Secrets.DEEPGRAM) }, { m }, { prefs[Keys.VOICE_TURN] })
        } else {
            NovaEars({ secrets.get(Secrets.DEEPGRAM) }, { m }, { prefs[Keys.VOICE_LANGUAGE] }, { prefs[Keys.VOICE_TURN] })
        }
    }

    fun start() {
        if (isActive) return
        if (!Net.online(context)) {
            fail(Net.OFFLINE)
            return
        }
        state = State.CONNECTING
        engine.addListener(this)
        stt = ears()
        // Call processing cancels the speaker's echo so the person can cut in; the plain
        // source hears more faithfully on phones whose call path narrows the sound.
        val source = if (prefs[Keys.VOICE_MIC] == "clean") MediaRecorder.AudioSource.VOICE_RECOGNITION else MediaRecorder.AudioSource.VOICE_COMMUNICATION
        mic = MicCapture(source) { buf, len -> hear(buf, len) }
        muted = false
        echoRatio = ECHO_RATIO_FLOOR
        stt.listener = this
        tts.listener = this
        focus.acquire()
        playback.start(Speed.stretch(prefs[Keys.VOICE_SPEED]))
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
        // Nothing that arrives from here on is ours: not audio, not text.
        replyIndex = -1
        mic.stop()
        stt.stop()
        tts.stop()
        playback.stop()
        focus.release()
        chunker.reset()
        clock.reset()
        unflushed.setLength(0)
        main.removeCallbacks(flushLater)
        pendingTurn = null
        streamDone = false
        toolRound = false
        main.removeCallbacks(slowThinking)
        main.removeCallbacks(keepAlive)
        state = State.IDLE
    }

    /** Debug builds only: feed a turn as if the person had said it, for phones and emulators without a usable mic. */
    fun injectTurn(text: String) {
        if (!io.github.kasecrab.razorback.BuildConfig.DEBUG || !isActive) return
        onTurn(Ears.Turn.START, text, -1, 1f)
        onTurn(Ears.Turn.END, text, -1, 1f)
    }

    /**
     * Muting lets go of the microphone altogether, so the system's mic indicator goes out
     * and nothing is recorded; the ears are kept awake with keep-alives until it is back.
     */
    fun setMuted(on: Boolean) {
        if (muted == on) return
        muted = on
        if (!isActive) return
        main.removeCallbacks(keepAlive)
        if (on) {
            mic.stop()
            main.postDelayed(keepAlive, KEEP_ALIVE_MS)
        } else if (!mic.start()) {
            fail("Microphone unavailable")
        }
    }

    private val keepAlive = object : Runnable {
        override fun run() {
            if (!muted || !isActive) return
            stt.keepAlive()
            main.postDelayed(this, KEEP_ALIVE_MS)
        }
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

    override fun onTurn(kind: Ears.Turn, transcript: String, turnIndex: Int, confidence: Float) {
        when (kind) {
            Ears.Turn.START -> {
                if (busy) {
                    // Over the reply, speech is not trusted until it is clearly not the
                    // speaker heard back through the microphone.
                    if (echoGuard() || !surelyThePerson(transcript)) {
                        tentative = true
                        return
                    }
                    interrupt()
                }
                tentative = false
                state = State.USER_SPEAKING
                listener?.onUserText(transcript, false)
            }
            Ears.Turn.UPDATE, Ears.Turn.EAGER_END, Ears.Turn.RESUMED -> {
                if (state == State.USER_SPEAKING) {
                    listener?.onUserText(transcript, false)
                } else if (tentative && busy && surelyThePerson(transcript)) {
                    tentative = false
                    interrupt()
                    state = State.USER_SPEAKING
                    listener?.onUserText(transcript, false)
                }
            }
            Ears.Turn.END -> {
                if (tentative) {
                    tentative = false
                    if (busy) {
                        if (transcript.isBlank() || isEcho(transcript)) return
                        interrupt()
                    }
                }
                // The transcript of an echo lands after the speaker has gone quiet, and a
                // muffled echo the transcriber half-understood scores low and short.
                if (!busy && isEcho(transcript)) return
                if (recentlySpoke() && (confidence < DOUBTFUL || wordCount(transcript) < 2)) return
                if (transcript.isBlank()) {
                    if (state == State.USER_SPEAKING) state = State.LISTENING
                    return
                }
                listener?.onUserText(transcript, true)
                ask(transcript)
            }
        }
    }

    /** A reply is being made or read; speech now is either a cut-in or the speaker's echo. */
    private val busy: Boolean get() = state == State.SPEAKING || state == State.THINKING || state == State.SEARCHING

    /** Two or more words that are not just the reply coming back through the microphone. */
    private fun surelyThePerson(transcript: String): Boolean = wordCount(transcript) >= 2 && !isEcho(transcript)

    /** True when most of what was heard is what the voice has just been saying. */
    private fun recentlySpoke(): Boolean = playback.isPlaying || SystemClock.elapsedRealtime() - playbackEndedAt < ECHO_WORDS_MS

    private fun isEcho(transcript: String): Boolean {
        if (state != State.SPEAKING && !recentlySpoke()) return false
        var total = 0
        var known = 0
        for (w in words(transcript)) {
            // "A", "to" and "is" are in every reply; they say nothing about whose speech this is.
            if (w.length < 3) continue
            total++
            // Echo is muffled, so a word counts when its stem matches too.
            if (w in spokenWords || (w.length >= 4 && w.substring(0, 4) in spokenStems)) known++
        }
        // Only tiny words around speech is the tail of the echo, not a turn.
        return total == 0 || known * 10 >= total * 6
    }

    private fun wordCount(text: String): Int {
        var n = 0
        for (w in words(text)) n++
        return n
    }

    private fun words(text: String): Sequence<String> =
        text.lowercase().splitToSequence(NON_WORD).filter { it.isNotEmpty() }

    override fun onDropped(reconnecting: Boolean) {
        if (state == State.LISTENING || state == State.USER_SPEAKING) state = State.RECONNECTING
    }

    override fun onError(message: String) = fail(message)

    override fun onSpeedUnavailable() = playback.setStretch(Speed.stretch(prefs[Keys.VOICE_SPEED]) * Speed.server(prefs[Keys.VOICE_SPEED]))

    /** Barge-in: the person started talking over the reply. */
    private fun interrupt() {
        tts.clear()
        playback.clear()
        playbackEndedAt = SystemClock.elapsedRealtime()
        audioArrived = false
        chunker.reset()
        clock.reset()
        unflushed.setLength(0)
        main.removeCallbacks(flushLater)
        streamDone = false
        toolRound = false
        main.removeCallbacks(slowThinking)
        if (engine.isStreaming) engine.stop()
        replyIndex = -1
    }

    /** Right after playback starts the mic may hear the speaker; ignore a quiet "start". */
    private fun echoGuard(): Boolean {
        val since = SystemClock.elapsedRealtime() - playbackStartedAt
        return since < 250 && mic.level.get() < 60
    }

    // Echo gate, on the microphone thread.

    /** How loud the speaker comes back through the microphone, as a share of the level sent out. */
    @Volatile private var echoRatio = ECHO_RATIO_FLOOR
    /** Levels of the last chunks written to the speaker; the echo of any of them may be arriving now. */
    private val outRecent = FloatArray(OUT_WINDOW)
    private var outAt = 0
    private var passUntil = 0L
    private var gateChunks = 0
    private val silence = ByteArray(MicCapture.CHUNK_BYTES)

    /**
     * While the assistant talks, the phone hears it too. On the call path the phone's own
     * echo canceller takes the speaker out of the microphone almost entirely; this gate
     * covers what is left, and phones whose canceller does less. The first half second of
     * every stretch of speech, when the person has just finished talking, measures how
     * much of the speaker's level leaks in; after that a chunk goes through when the
     * microphone is clearly louder than that leak, which is the person cutting in, and
     * for a moment after, so their words are not chopped. Everything else is sent as
     * silence so the transcriber's timing holds. The leak is measured only in that window:
     * learning from later chunks judged to be echo turned quiet speech into a higher
     * estimate, which shut the gate on the person altogether.
     */
    private fun hear(buf: ByteArray, len: Int) {
        if (passes(len)) stt.audio(buf, len) else stt.audio(silence, len)
    }

    private fun passes(len: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        outRecent[outAt] = playback.level.get().toFloat()
        outAt = (outAt + 1) % OUT_WINDOW
        val speakerBusy = playback.isPlaying || now - playbackEndedAt < ECHO_TAIL_MS
        if (!speakerBusy) return true
        // The loudest of the last several chunks: what was written a moment ago is what
        // is coming back now, and a pause between sentences is no reason to trust the mic.
        var outHold = 0f
        for (v in outRecent) if (v > outHold) outHold = v
        if (outHold < 20f) return true
        val heard = mic.level.get().toFloat()
        val since = now - playbackStartedAt
        if (since < CALIBRATE_MS) {
            echoRatio = maxOf(echoRatio, minOf(heard / outHold, ECHO_RATIO_CAP))
            return false
        }
        val expected = echoRatio * outHold
        if (Log.ON && ++gateChunks % 6 == 0) Log.d { "gate: heard $heard out $outHold ratio $echoRatio expected $expected" }
        if (heard > expected * MARGIN + 20f) {
            passUntil = now + HANGOVER_MS
            return true
        }
        return now < passUntil
    }

    private var askedAt = 0L

    private fun ask(text: String) {
        askedAt = SystemClock.elapsedRealtime()
        chunker.reset()
        clock.reset()
        unflushed.setLength(0)
        main.removeCallbacks(flushLater)
        spokenChars = 0
        streamDone = false
        toolRound = false
        tentative = false
        cuePending = false
        spokenWords.clear()
        spokenStems.clear()
        replyStartBytes = playback.enqueuedBytes.get()
        main.removeCallbacks(slowThinking)
        main.postDelayed(slowThinking, SLOW_THINKING_MS)
        state = State.THINKING
        listener?.onReplyStarted()
        if (engine.isStreaming) {
            // The cut-off reply has not let go of the stream yet; the engine would drop a
            // send now. The turn waits for the stream to end and goes out then.
            pendingTurn = text
            replyIndex = -1
            engine.stop()
            return
        }
        replyIndex = engine.messages.size + 1
        engine.send(text, thinking = prefs[Keys.VOICE_THINKING], model = prefs[Keys.VOICE_MODEL].ifEmpty { engine.model }, spoken = true)
    }

    // Model reply

    override fun onMessageChanged(index: Int, streaming: Boolean) {
        if (index != replyIndex) return
        val m = engine.messages.getOrNull(index) ?: return
        if (m.role != Role.ASSISTANT) return
        val content = m.content
        if (content.length > spokenChars) {
            if (spokenChars == 0) {
                Log.d { "voice: first token ${SystemClock.elapsedRealtime() - askedAt} ms after the turn ended" }
                main.removeCallbacks(slowThinking)
                cuePending = false
            }
            chunker.push(content.substring(spokenChars))
            spokenChars = content.length
        }
        if (!streaming) {
            chunker.flush()
            flushPending()
            if (m.status == MessageStatus.ERROR) {
                listener?.onError(m.error ?: "The model did not answer")
                replyIndex = -1
                state = State.LISTENING
                return
            }
            if (m.status == MessageStatus.COMPLETE && m.toolCalls.isNotEmpty()) {
                // The engine is about to run the tool and ask again. Say so, so the wait
                // is not dead air, and keep the turn open for the reply that follows.
                toolRound = true
                main.removeCallbacks(slowThinking)
                if (state == State.THINKING) state = State.SEARCHING
                cue(if (m.content.isBlank()) CUES[cues++ % CUES.size] else m.content)
                return
            }
            streamDone = true
            maybeEnd()
        }
    }

    /**
     * The reply is over once the model has stopped and the voice has returned every
     * sentence's audio; only then can the speaker be told that nothing more is coming.
     */
    private fun maybeEnd() {
        if (!streamDone || !clock.allFlushed) return
        if (clock.sentenceCount == 0 || clock.totalBytes == 0L) {
            // Nothing to say, or the voice had nothing to give; straight back to listening.
            replyIndex = -1
            state = State.LISTENING
            return
        }
        playback.markEnd()
    }

    /** How far the voice is through the reply, as a word index into the sentences given to [Listener.onSentence]. */
    fun spokenWord(): Int {
        clock.seek(playback.playedBytes() - replyStartBytes)
        return clock.word
    }

    fun spokenFraction(): Float = clock.fraction

    override fun onStreamingChanged(streaming: Boolean) {
        if (streaming) return
        val next = pendingTurn
        if (next != null) {
            pendingTurn = null
            ask(next)
            return
        }
        if (replyIndex >= 0 && engine.messages.getOrNull(replyIndex)?.status == MessageStatus.CUT && state == State.THINKING) {
            replyIndex = -1
            state = State.LISTENING
        }
    }

    /** Something short to say about the wait; once it has played the wait goes on in silence. */
    private fun cue(text: String) {
        cuePending = true
        speak(text)
        flushPending()
    }

    private fun speak(sentence: String) {
        val text = SpeechText.strip(sentence)
        if (text.isBlank()) return
        Log.d { "voice: sentence of ${text.length} chars to aura ${SystemClock.elapsedRealtime() - askedAt} ms after the turn ended" }
        for (w in words(text)) {
            spokenWords.add(w)
            if (w.length >= 4) spokenStems.add(w.substring(0, 4))
        }
        listener?.onSentence(text)
        // A space at the end keeps this sentence apart from the next in the voice's buffer.
        tts.speak("$text ")
        if (unflushed.isNotEmpty()) unflushed.append(' ')
        unflushed.append(text)
        paceFlush()
    }

    /**
     * Aura says nothing until told to flush, and it says each flushed run as one piece.
     * Flushing every sentence gives the quickest start, but Deepgram warns that very
     * frequent flushes degrade the audio and caps them at twenty a minute; a reply of
     * short sentences flushed one by one is where the voice was heard to waver. So only
     * the first words go out on their own; after that, text gathers until there is a good
     * run of it or the speaker is down to its last few seconds, whichever comes first.
     */
    private fun paceFlush(overdue: Boolean = false) {
        main.removeCallbacks(flushLater)
        val chars = unflushed.length
        if (chars == 0) return
        if (clock.sentenceCount == 0) {
            if (chars >= FIRST_FLUSH_CHARS || overdue) flushPending() else main.postDelayed(flushLater, FIRST_FLUSH_WAIT_MS)
            return
        }
        if (chars >= GROUP_CHARS) {
            flushPending()
            return
        }
        // What is queued is a floor: a run just flushed is still arriving. Judging by the
        // floor errs towards one flush more, never towards the speaker running dry.
        val ahead = audioAheadMs()
        if (ahead <= LOW_WATER_MS) {
            flushPending()
        } else {
            Log.d { "voice: $chars chars wait, speaker $ahead ms ahead" }
            main.postDelayed(flushLater, ahead - LOW_WATER_MS)
        }
    }

    /** Tell the voice to say everything it has been given since the last flush. */
    private fun flushPending() {
        main.removeCallbacks(flushLater)
        if (unflushed.isEmpty()) return
        val run = unflushed.toString()
        unflushed.setLength(0)
        Log.d { "voice: flush of ${run.length} chars, speaker ${audioAheadMs()} ms ahead" }
        clock.sentence(run)
        tts.flush()
    }

    /** Milliseconds of speech queued on the speaker and not yet heard, at the pace it is played. */
    private fun audioAheadMs(): Long {
        val bytes = playback.enqueuedBytes.get() - playback.playedBytes()
        return (bytes / Playback.BYTES_PER_MS / Speed.stretch(prefs[Keys.VOICE_SPEED])).toLong()
    }

    // Speech out

    /** Arrives on the socket reader thread: audio goes straight to the speaker, state hops to main. */
    override fun onAudio(data: ByteArray) {
        if (replyIndex < 0) return
        val first = !audioArrived
        audioArrived = true
        clock.audio(data.size)
        if (first) {
            // Set here, before the audio can reach the speaker, so the gate calibrates from the first chunk.
            playbackStartedAt = SystemClock.elapsedRealtime()
            echoRatio = maxOf(ECHO_RATIO_FLOOR, echoRatio * 0.9f)
            java.util.Arrays.fill(outRecent, 0f)
            Log.d { "voice: first tts audio ${SystemClock.elapsedRealtime() - askedAt} ms after the turn ended" }
        }
        playback.enqueue(data)
        // The answer's audio can start while a cue is still playing, so every chunk checks the
        // state, not just the first; a cue while searching keeps the searching status.
        if (state == State.THINKING || (state == State.SEARCHING && !toolRound)) {
            context.mainExecutor.execute {
                if (replyIndex < 0) return@execute
                if (state == State.THINKING || (state == State.SEARCHING && !toolRound)) {
                    state = State.SPEAKING
                    if (prefs[Keys.VOICE_MUTE_WHILE_SPEAKING]) mic.muted.set(true)
                }
            }
        }
    }

    /** Socket thread, in order with the audio: the sentence boundary goes to the clock at once, the rest to main. */
    override fun onFlushed() {
        if (replyIndex < 0) return
        clock.flushed()
        context.mainExecutor.execute {
            if (replyIndex < 0) return@execute
            if (toolRound || cuePending) {
                // Nothing more will be said until the model has more; let the cue play out.
                if (clock.allFlushed) playback.markEnd()
                return@execute
            }
            maybeEnd()
        }
    }

    override fun onCleared() {}

    private fun onDrained() {
        playbackEndedAt = SystemClock.elapsedRealtime()
        audioArrived = false
        context.mainExecutor.execute {
            if (state != State.SPEAKING && state != State.SEARCHING) return@execute
            if (toolRound || (engine.isStreaming && !streamDone)) {
                // The cue has been said; the answer is still on its way.
                cuePending = false
                state = if (toolRound) State.SEARCHING else State.THINKING
                return@execute
            }
            replyIndex = -1
            mic.muted.set(false)
            state = State.LISTENING
        }
    }

    private companion object {
        /** Said while a tool runs, so the silence is not mistaken for a stall. */
        val CUES = listOf("Let me grab some more info on that.", "Let me look that up.", "One second, checking online.")
        val THINK_CUES = listOf("Let me think about that for a second.", "Hmm, give me a moment.", "Okay, let me think.")
        const val SLOW_THINKING_MS = 2500L
        /** Deepgram drops a stream that goes quiet for about ten seconds; this keeps it well inside that. */
        const val KEEP_ALIVE_MS = 5000L
        const val ECHO_RATIO_FLOOR = 0.05f
        /** Louder than this share of the speaker is not echo any canceller leaves; it is the person. */
        const val ECHO_RATIO_CAP = 0.8f
        const val CALIBRATE_MS = 600L
        const val HANGOVER_MS = 700L
        /** Track buffer, air and input latency together; the last word is still arriving well after the end marker. */
        const val ECHO_TAIL_MS = 1200L
        const val ECHO_WORDS_MS = 3000L
        const val MARGIN = 2f
        /** Chunks of 80 ms: 640 ms of recent speaker level. */
        const val OUT_WINDOW = 8
        const val DOUBTFUL = 0.6f
        /** The first run goes out with a sentence's worth, or after a short wait for one. */
        const val FIRST_FLUSH_CHARS = 12
        const val FIRST_FLUSH_WAIT_MS = 300L
        /** Text gathers up to this much before a flush while the speaker has plenty queued. */
        const val GROUP_CHARS = 200
        /**
         * A flush goes out when the speaker has less than this left to say: the voice's first
         * audio after a flush takes about four hundred milliseconds and varies, so the
         * queue is kept well ahead of it or the speech comes out in fits and starts.
         */
        const val LOW_WATER_MS = 3000L
        val NON_WORD = Regex("[^\\p{L}\\p{N}']+")
    }

    override fun onReset() {}

    /**
     * A turn can hold several replies: one that only asks for a tool, then the answer.
     * Whichever assistant message starts streaming while the turn is ours is the one to read.
     */
    override fun onMessageAdded(index: Int) {
        // The answer after a tool round arrives while the state is still SEARCHING.
        if (!busy) return
        val m = engine.messages.getOrNull(index) ?: return
        if (m.role != Role.ASSISTANT || m.status != MessageStatus.STREAMING) return
        replyIndex = index
        spokenChars = 0
        toolRound = false
        cuePending = false
        main.removeCallbacks(slowThinking)
        main.postDelayed(slowThinking, SLOW_THINKING_MS)
    }
    override fun onMessageRemoved(index: Int) {}
}
