package io.github.kasecrab.razorback.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import android.widget.Toast
import io.github.kasecrab.razorback.ui.core.MicPermission
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.ui
import io.github.kasecrab.razorback.voice.DictationLink
import io.github.kasecrab.razorback.voice.MicCapture
import io.github.kasecrab.razorback.voice.Spoken

/**
 * Voice typing into the composer. Interim words show as a grey ghost after the caret;
 * finals become real text. A tap toggles listening, a hold listens until release.
 */
class Dictation(private val context: Context, private val edit: EditText) : DictationLink.Listener {

    private val link = DictationLink(
        { App.instance.secrets.get(Secrets.DEEPGRAM) },
        { App.instance.prefs[Keys.VOICE_DICTATION_MODEL] },
        { App.instance.prefs[Keys.VOICE_LANGUAGE] },
    )
    private val mic = MicCapture { buf, len -> link.audio(buf, len) }
    private val spoken = Spoken()
    private var ghostStart = -1
    private var ghostEnd = -1
    private var settle: Runnable? = null

    enum class State { OFF, CONNECTING, LISTENING }

    var onStateChanged: ((State) -> Unit)? = null
    var state: State = State.OFF
        private set(value) {
            if (field == value) return
            field = value
            onStateChanged?.invoke(value)
        }

    val isListening: Boolean get() = mic.isRunning

    /** Microphone level, 0..1. */
    val level: Float get() = mic.level.get() / 1000f

    init {
        link.listener = this
    }

    fun toggle() {
        if (mic.isRunning) stop() else start()
    }

    fun start() {
        if (mic.isRunning) return
        if (!io.github.kasecrab.razorback.ui.core.KeyNeeded.check(context, Secrets.DEEPGRAM)) return
        if (!io.github.kasecrab.razorback.core.Net.online(context)) {
            Toast.makeText(context, io.github.kasecrab.razorback.core.Net.OFFLINE, Toast.LENGTH_SHORT).show()
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            context.ui().permissions.request(Manifest.permission.RECORD_AUDIO) { granted ->
                if (granted) start() else MicPermission.explain(context)
            }
            return
        }
        settle?.let { edit.removeCallbacks(it) }
        spoken.clear()
        ghostStart = -1
        link.warm()
        if (!mic.start()) {
            Toast.makeText(context, "Microphone unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        link.listen(true)
        state = if (link.state == DictationLink.State.LISTENING) State.LISTENING else State.CONNECTING
    }

    fun stop() {
        if (!mic.isRunning) return
        mic.stop()
        link.listen(false)
        spoken.release(System.currentTimeMillis())
        state = State.OFF
        scheduleSettle()
    }

    /** Tear down entirely, e.g. when leaving the screen. */
    fun release() {
        mic.stop()
        link.stop()
        state = State.OFF
        settle?.let { edit.removeCallbacks(it) }
        clearGhost()
    }

    private fun scheduleSettle() {
        settle?.let { edit.removeCallbacks(it) }
        val r = Runnable {
            if (spoken.settled(System.currentTimeMillis(), mic.isRunning)) {
                commitAll()
                if (!mic.isRunning) link.stop()
            } else {
                scheduleSettle()
            }
        }
        settle = r
        edit.postDelayed(r, 200)
    }

    override fun onInterim(text: String) {
        spoken.interim(text)
        showGhost(spoken.pending)
    }

    override fun onFinal(text: String) {
        spoken.final(text)
        insert(spacer() + text)
        showGhost("")
    }

    override fun onUtteranceEnd() {
        spoken.utteranceEnd()
        showGhost("")
    }

    override fun onState(state: DictationLink.State) {
        if (!mic.isRunning) return
        this.state = if (state == DictationLink.State.LISTENING || state == DictationLink.State.READY) State.LISTENING else State.CONNECTING
    }

    override fun onError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        stop()
    }

    private fun commitAll() {
        val rest = spoken.pending
        clearGhost()
        if (rest.isNotEmpty()) insert(spacer() + rest)
        spoken.clear()
    }

    private fun spacer(): String {
        val at = caret()
        val e = edit.text
        return if (at > 0 && !e[at - 1].isWhitespace()) " " else ""
    }

    private fun caret(): Int {
        clearGhost()
        return edit.selectionStart.coerceAtLeast(0)
    }

    private fun insert(text: String) {
        val at = caret()
        edit.text.insert(at, text)
        edit.setSelection(at + text.length)
    }

    private fun showGhost(text: String) {
        clearGhost()
        if (text.isEmpty()) return
        val at = edit.selectionStart.coerceAtLeast(0)
        val ghost = SpannableStringBuilder(spacerAt(at) + text)
        ghost.setSpan(ForegroundColorSpan(context.appTheme.textTertiary), 0, ghost.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        edit.text.insert(at, ghost)
        ghostStart = at
        ghostEnd = at + ghost.length
        edit.setSelection(at)
    }

    private fun spacerAt(at: Int): String = if (at > 0 && !edit.text[at - 1].isWhitespace()) " " else ""

    private fun clearGhost() {
        if (ghostStart >= 0 && ghostEnd <= edit.text.length) {
            edit.text.delete(ghostStart, ghostEnd)
        }
        ghostStart = -1
        ghostEnd = -1
    }
}
