package io.github.kasecrab.razorback.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
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
import io.github.kasecrab.razorback.voice.VoiceSession
import io.github.kasecrab.razorback.voice.VoiceService

/** The spoken conversation: an orb that breathes with the audio, captions, mute and end. */
class VoiceScreen(context: Context) : Screen(context), VoiceSession.Listener {

    private val app = App.instance
    private val voice = app.voice
    private val orb = OrbView(context)
    private val status = TextView(context)
    private val userCaption = TextView(context)
    private val assistantCaption = TextView(context)
    private val mute = IconButton(context)
    private val end = IconButton(context)
    private val controls = LinearLayout(context)
    private val captions = LinearLayout(context)
    private var muted = false

    init {
        keepScreenOn = true
        orb.orb = Orbs.byId(app.prefs[Keys.VOICE_ORB])
        orb.inLevel = { voice.inLevel }
        orb.outLevel = { voice.outLevel }
        addView(orb, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        status.typeface = Fonts.medium
        status.gravity = Gravity.CENTER
        addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = dp(24) })

        captions.orientation = LinearLayout.VERTICAL
        captions.gravity = Gravity.CENTER_HORIZONTAL
        captions.setPadding(dp(28), 0, dp(28), 0)
        userCaption.typeface = Fonts.regular
        userCaption.gravity = Gravity.CENTER
        userCaption.maxLines = 3
        assistantCaption.typeface = Fonts.regular
        assistantCaption.gravity = Gravity.CENTER
        assistantCaption.maxLines = 4
        captions.addView(userCaption, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        captions.addView(assistantCaption, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        addView(captions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(120) })

        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        mute.iconRes = R.drawable.ic_mic
        mute.filled = true
        mute.tone = IconButton.Tone.PRIMARY
        mute.contentDescription = context.getString(R.string.cd_mute)
        mute.setOnClickListener { toggleMute() }
        controls.addView(mute, LinearLayout.LayoutParams(dp(60), dp(60)).apply { marginEnd = dp(28) })
        end.iconRes = R.drawable.ic_close
        end.filled = true
        end.tone = IconButton.Tone.PRIMARY
        end.contentDescription = context.getString(R.string.voice_end)
        end.setOnClickListener { context.nav.pop() }
        controls.addView(end, LinearLayout.LayoutParams(dp(60), dp(60)))
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(36) })
    }

    override fun onEnter() {
        voice.listener = this
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            begin()
        } else {
            context.ui().permissions.request(Manifest.permission.RECORD_AUDIO) { granted ->
                if (granted) begin() else context.nav.pop()
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
        userCaption.setTextColor(theme.textSecondary)
        userCaption.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        assistantCaption.setTextColor(theme.textPrimary)
        assistantCaption.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        orb.onThemeChanged(theme)
    }

    // VoiceSession.Listener

    override fun onStateChanged(state: VoiceSession.State) {
        status.text = when (state) {
            VoiceSession.State.IDLE, VoiceSession.State.CONNECTING -> context.getString(R.string.voice_connecting)
            VoiceSession.State.LISTENING, VoiceSession.State.USER_SPEAKING -> context.getString(R.string.voice_listening)
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
        if (state == VoiceSession.State.THINKING) assistantCaption.text = ""
    }

    override fun onUserText(text: String, final: Boolean) {
        userCaption.text = text
        userCaption.alpha = if (final) 0.7f else 1f
    }

    override fun onAssistantText(text: String) {
        assistantCaption.text = if (text.length > 220) "…" + text.takeLast(220) else text
    }

    override fun onError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
