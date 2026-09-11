package io.github.kasecrab.razorback.ui.voice

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.Shapes
import io.github.kasecrab.razorback.ui.widget.Sheet
import io.github.kasecrab.razorback.voice.Voice
import io.github.kasecrab.razorback.voice.VoicePreview
import io.github.kasecrab.razorback.voice.Voices

/** Every voice by language, each with a play button so it can be heard before it is chosen. */
class VoicePickerSheet(context: Context, private val onPick: (Voice) -> Unit) : Sheet(context) {

    private class Row(val voice: Voice, val view: LinearLayout, val name: TextView, val meta: TextView, val play: IconButton, val check: ImageView)

    private val app = App.instance
    private val preview = VoicePreview(context) { app.secrets.get(Secrets.DEEPGRAM) }
    private val title = TextView(context)
    private val rows = ArrayList<Row>(Voices.all.size)
    private var selected = app.prefs[Keys.VOICE_TTS_VOICE]

    init {
        title.typeface = Fonts.medium
        title.setText(R.string.voice_voice)
        title.setPadding(dp(20), dp(4), dp(20), dp(4))
        body.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        for ((code, label) in Voices.languages) {
            val h = SectionHeader(context)
            h.text = label
            h.setPadding(dp(20), dp(12), dp(20), dp(4))
            list.addView(h)
            for (v in Voices.all) if (v.language == code) list.addView(row(v), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        preview.onChanged = { paintPlay() }
        preview.onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        preview.onMuted = { Toast.makeText(context, R.string.voice_media_muted, Toast.LENGTH_LONG).show() }
        onDismiss = { preview.stop() }
    }

    private fun row(v: Voice): View {
        val r = LinearLayout(context)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.isClickable = true
        r.setPadding(dp(20), dp(8), dp(12), dp(8))
        val texts = LinearLayout(context)
        texts.orientation = LinearLayout.VERTICAL
        val name = TextView(context)
        name.typeface = Fonts.regular
        name.text = v.name
        val meta = TextView(context)
        meta.typeface = Fonts.regular
        meta.text = context.getString(if (v.feminine) R.string.voice_meta_f else R.string.voice_meta_m, v.accent, v.traits)
        texts.addView(name)
        texts.addView(meta)
        r.addView(texts, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val check = ImageView(context)
        check.scaleType = ImageView.ScaleType.CENTER
        r.addView(check, LinearLayout.LayoutParams(dp(24), dp(24)))
        val play = IconButton(context)
        play.iconRes = R.drawable.ic_waveform
        play.tone = IconButton.Tone.ACCENT
        play.contentDescription = context.getString(R.string.cd_play_sample)
        play.setOnClickListener { preview.toggle(v, context.uiScope) }
        r.addView(play, LinearLayout.LayoutParams(dp(44), dp(44)))
        r.setOnClickListener {
            selected = v.id
            app.prefs[Keys.VOICE_TTS_VOICE] = v.id
            onPick(v)
            dismiss()
        }
        rows.add(Row(v, r, name, meta, play, check))
        return r
    }

    private fun paintPlay() {
        val now = preview.playing
        for (r in rows) {
            val on = r.voice.id == now
            r.play.iconRes = if (on) R.drawable.ic_stop else R.drawable.ic_waveform
            r.play.tone = if (on) IconButton.Tone.PRIMARY else IconButton.Tone.ACCENT
        }
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        for (r in rows) {
            val on = r.voice.id == selected
            r.view.background = Shapes.ripple(theme.accentSoft, null, 0f)
            r.name.setTextColor(if (on) theme.accent else theme.textPrimary)
            r.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            r.meta.setTextColor(theme.textSecondary)
            r.meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
            r.check.setImageDrawable(context.icon(R.drawable.ic_check, theme.accent))
            r.check.visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
    }
}
