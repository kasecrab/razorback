package io.github.kasecrab.razorback.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.ui
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.orb.Orb
import io.github.kasecrab.razorback.ui.orb.OrbView
import io.github.kasecrab.razorback.ui.orb.Orbs
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.voice.SpeechText
import io.github.kasecrab.razorback.voice.VoiceService
import io.github.kasecrab.razorback.voice.VoiceSession

/**
 * The spoken conversation: the transcript fills the screen with the words lighting up as
 * they are said, and a small orb in a state ring sits at the bottom between mute and end.
 */
class VoiceScreen(context: Context) : Screen(context), VoiceSession.Listener {

    private val app = App.instance
    private val voice = app.voice
    private val column = LinearLayout(context)
    private val status = TextView(context)
    private val halo = VoiceHalo(context)
    private val orb: OrbView get() = halo.orb
    private val transcript = TranscriptView(context)
    private val mute = IconButton(context)
    private val end = IconButton(context)
    private val controls = LinearLayout(context)
    private var muted = false
    private var shownState = VoiceSession.State.IDLE
    private var followingSpeech = false
    private val speechFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!followingSpeech) return
            val line = transcript.reply
            if (line != null) {
                val w = voice.spokenWord()
                line.setProgress(w, voice.spokenFraction())
                transcript.revealWord(w)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        keepScreenOn = true
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL

        column.addView(transcript, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        status.typeface = Fonts.medium
        status.gravity = Gravity.CENTER
        status.setPadding(dp(16), dp(4), dp(16), 0)
        if (io.github.kasecrab.razorback.BuildConfig.DEBUG) {
            // Long-press the status line to type a turn when there is no microphone to speak into.
            status.setOnLongClickListener {
                io.github.kasecrab.razorback.ui.widget.InputSheet(context, "Say", "") { voice.injectTurn(it) }.show()
                true
            }
        }
        column.addView(status, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        orb.orb = if (app.prefs[Keys.REDUCE_MOTION]) Orbs.byId("lattice") else Orbs.byId(app.prefs[Keys.VOICE_ORB])
        orb.inLevel = { voice.inLevel }
        orb.outLevel = { voice.outLevel }
        halo.inLevel = { voice.inLevel }
        halo.outLevel = { voice.outLevel }
        halo.contentDescription = context.getString(R.string.cd_orb_style)
        halo.setOnLongClickListener {
            OrbPickerSheet(context) { orb.orb = it }.show()
            true
        }

        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.setPadding(0, dp(2), 0, dp(12))
        mute.iconRes = R.drawable.ic_mic
        mute.filled = true
        mute.tone = IconButton.Tone.PRIMARY
        mute.contentDescription = context.getString(R.string.cd_mute)
        mute.setOnClickListener { toggleMute() }
        controls.addView(mute, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(36) })
        controls.addView(halo, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        end.iconRes = R.drawable.ic_close
        end.filled = true
        end.tone = IconButton.Tone.PRIMARY
        end.contentDescription = context.getString(R.string.voice_end)
        end.setOnClickListener {
            Haptics.heavy(end)
            context.nav.pop()
        }
        controls.addView(end, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = dp(36) })
        column.addView(controls, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onEnter() {
        voice.listener = this
        fillHistory()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            begin()
        } else {
            context.ui().permissions.request(Manifest.permission.RECORD_AUDIO) { granted ->
                if (granted) {
                    begin()
                } else {
                    // Back to the chat, then say why, with the way to allow it.
                    context.nav.pop()
                    io.github.kasecrab.razorback.ui.core.MicPermission.explain(context)
                }
            }
        }
    }

    /** What was already said in this chat, typed or spoken, so the conversation has its past. */
    private fun fillHistory() {
        transcript.clear()
        for (m in app.engine.messages) {
            if (m.status == MessageStatus.ERROR || m.content.isBlank()) continue
            when (m.role) {
                Role.USER -> transcript.addHistory(m.content, fromUser = true)
                Role.ASSISTANT -> transcript.addHistory(SpeechText.strip(m.content), fromUser = false)
                else -> {}
            }
        }
    }

    private fun begin() {
        VoiceService.start(context)
        voice.start()
        onStateChanged(voice.state)
    }

    override fun onExit() {
        followSpeech(false)
        voice.stop()
        voice.listener = null
        VoiceService.stop(context)
    }

    /** While the assistant speaks, every frame asks the session where the voice is and lights the words up to there. */
    private fun followSpeech(on: Boolean) {
        if (followingSpeech == on) return
        followingSpeech = on
        if (on) Choreographer.getInstance().postFrameCallback(speechFrame) else Choreographer.getInstance().removeFrameCallback(speechFrame)
    }

    private fun toggleMute() {
        muted = !muted
        Haptics.toggle(mute, muted)
        voice.setMuted(muted)
        mute.iconRes = if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic
        mute.tone = if (muted) IconButton.Tone.DANGER else IconButton.Tone.PRIMARY
    }

    override fun onInsetsChanged(top: Int, bottom: Int, left: Int, right: Int) {
        setPadding(left, top, right, bottom)
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        status.setTextColor(theme.textSecondary)
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        halo.onThemeChanged(theme)
        transcript.onThemeChanged(theme)
    }

    // VoiceSession.Listener

    /** One tick when what was said has been taken, another when a cut-in landed, so the ear need not watch the screen. */
    override fun onStateChanged(state: VoiceSession.State) {
        val was = shownState
        shownState = state
        when {
            was == VoiceSession.State.USER_SPEAKING && (state == VoiceSession.State.THINKING || state == VoiceSession.State.SEARCHING) -> Haptics.tick(this)
            was == VoiceSession.State.SPEAKING && state == VoiceSession.State.USER_SPEAKING -> Haptics.tick(this)
            state == VoiceSession.State.ERROR && was != VoiceSession.State.ERROR -> Haptics.reject(this)
        }
        status.text = when (state) {
            VoiceSession.State.IDLE, VoiceSession.State.CONNECTING -> context.getString(R.string.voice_connecting)
            VoiceSession.State.LISTENING -> context.getString(R.string.voice_listening)
            VoiceSession.State.USER_SPEAKING -> context.getString(R.string.voice_hearing)
            VoiceSession.State.THINKING -> context.getString(R.string.voice_thinking)
            VoiceSession.State.SEARCHING -> context.getString(R.string.voice_searching)
            VoiceSession.State.SPEAKING -> context.getString(R.string.voice_speaking)
            VoiceSession.State.RECONNECTING -> context.getString(R.string.voice_reconnecting)
            VoiceSession.State.ERROR -> context.getString(R.string.went_wrong)
        }
        halo.state = state
        orb.state = when (state) {
            VoiceSession.State.LISTENING -> Orb.LISTENING
            VoiceSession.State.USER_SPEAKING -> Orb.USER_SPEAKING
            VoiceSession.State.THINKING, VoiceSession.State.SEARCHING -> Orb.THINKING
            VoiceSession.State.SPEAKING -> Orb.SPEAKING
            else -> Orb.IDLE
        }
        followSpeech(state == VoiceSession.State.SPEAKING || state == VoiceSession.State.SEARCHING || state == VoiceSession.State.THINKING)
        if (state == VoiceSession.State.LISTENING) transcript.endReply()
    }

    override fun onUserText(text: String, final: Boolean) = transcript.userSaid(text, final)

    override fun onReplyStarted() = transcript.startReply()

    override fun onSentence(spoken: String) = transcript.replySentence(spoken)

    override fun onError(message: String) {
        if (message == io.github.kasecrab.razorback.voice.DeepgramAccount.KEY_REFUSED) {
            context.nav.pop()
            io.github.kasecrab.razorback.ui.core.KeyNeeded.rejected(context, io.github.kasecrab.razorback.core.Secrets.DEEPGRAM)
            return
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
