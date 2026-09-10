package io.github.kasecrab.razorback.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.TypedValue
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
import io.github.kasecrab.razorback.ui.orb.Orb
import io.github.kasecrab.razorback.ui.orb.OrbView
import io.github.kasecrab.razorback.ui.orb.Orbs
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.voice.SpeechText
import io.github.kasecrab.razorback.voice.VoiceService
import io.github.kasecrab.razorback.voice.VoiceSession

/**
 * The spoken conversation: an orb that breathes with the audio, the whole transcript
 * beneath it with the words lighting up as they are said, and mute, orb style and end.
 */
class VoiceScreen(context: Context) : Screen(context), VoiceSession.Listener {

    private val app = App.instance
    private val voice = app.voice
    private val column = LinearLayout(context)
    private val status = TextView(context)
    private val orb = OrbView(context)
    private val transcript = TranscriptView(context)
    private val mute = IconButton(context)
    private val style = IconButton(context)
    private val end = IconButton(context)
    private val controls = LinearLayout(context)
    private var muted = false

    init {
        keepScreenOn = true
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL

        status.typeface = Fonts.medium
        status.gravity = Gravity.CENTER
        status.setPadding(dp(16), dp(16), dp(16), 0)
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
        val side = minOf(dp(240), (resources.displayMetrics.widthPixels * 0.5f).toInt())
        column.addView(orb, LinearLayout.LayoutParams(side, side).apply { topMargin = dp(4); bottomMargin = dp(4) })

        column.addView(transcript, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        controls.setPadding(0, dp(12), 0, dp(24))
        mute.iconRes = R.drawable.ic_mic
        mute.filled = true
        mute.tone = IconButton.Tone.PRIMARY
        mute.contentDescription = context.getString(R.string.cd_mute)
        mute.setOnClickListener { toggleMute() }
        controls.addView(mute, LinearLayout.LayoutParams(dp(60), dp(60)).apply { marginEnd = dp(28) })
        style.iconRes = R.drawable.ic_image
        style.filled = true
        style.tone = IconButton.Tone.PRIMARY
        style.contentDescription = context.getString(R.string.cd_orb_style)
        style.setOnClickListener { OrbPickerSheet(context) { orb.orb = it }.show() }
        controls.addView(style, LinearLayout.LayoutParams(dp(60), dp(60)).apply { marginEnd = dp(28) })
        end.iconRes = R.drawable.ic_close
        end.filled = true
        end.tone = IconButton.Tone.PRIMARY
        end.contentDescription = context.getString(R.string.voice_end)
        end.setOnClickListener { context.nav.pop() }
        controls.addView(end, LinearLayout.LayoutParams(dp(60), dp(60)))
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
                if (granted) begin() else context.nav.pop()
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
        voice.stop()
        voice.listener = null
        VoiceService.stop(context)
    }

    private fun toggleMute() {
        muted = !muted
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
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        orb.onThemeChanged(theme)
        transcript.onThemeChanged(theme)
    }

    // VoiceSession.Listener

    override fun onStateChanged(state: VoiceSession.State) {
        status.text = when (state) {
            VoiceSession.State.IDLE, VoiceSession.State.CONNECTING -> context.getString(R.string.voice_connecting)
            VoiceSession.State.LISTENING -> context.getString(R.string.voice_listening)
            VoiceSession.State.USER_SPEAKING -> context.getString(R.string.voice_hearing)
            VoiceSession.State.THINKING -> context.getString(R.string.voice_thinking)
            VoiceSession.State.SPEAKING -> context.getString(R.string.voice_speaking)
            VoiceSession.State.RECONNECTING -> context.getString(R.string.voice_reconnecting)
            VoiceSession.State.ERROR -> context.getString(R.string.went_wrong)
        }
        orb.state = when (state) {
            VoiceSession.State.LISTENING -> Orb.LISTENING
            VoiceSession.State.USER_SPEAKING -> Orb.USER_SPEAKING
            VoiceSession.State.THINKING -> Orb.THINKING
            VoiceSession.State.SPEAKING -> Orb.SPEAKING
            else -> Orb.IDLE
        }
        if (state == VoiceSession.State.LISTENING) transcript.endReply()
    }

    override fun onUserText(text: String, final: Boolean) = transcript.userSaid(text, final)

    override fun onReplyStarted() = transcript.startReply()

    override fun onSentence(spoken: String) = transcript.replySentence(spoken)

    override fun onError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
