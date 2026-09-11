package io.github.kasecrab.razorback.ui.voice

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
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
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Carousel
import io.github.kasecrab.razorback.ui.widget.Sheet
import io.github.kasecrab.razorback.ui.widget.Shapes
import io.github.kasecrab.razorback.voice.Voice
import io.github.kasecrab.razorback.voice.VoicePreview

/**
 * Voices as cards to swipe through. It opens on the voice in use; every card that lands
 * speaks its sample, and the disc replays or stops it.
 */
class VoicePickerSheet(context: Context, private val onPick: (Voice) -> Unit) : Sheet(context) {

    private val app = App.instance
    private val preview = VoicePreview(context) { app.secrets.get(Secrets.DEEPGRAM) }
    private val voices = io.github.kasecrab.razorback.voice.Voices.all
    private val title = TextView(context)
    private val counter = Caption(context)
    private val carousel = Carousel(context)
    private val hint = Caption(context)
    private val use = TextView(context)
    private var selected = app.prefs[Keys.VOICE_TTS_VOICE]
    private val speak = Runnable { preview.play(voices[carousel.page], context.uiScope) }

    init {
        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(20), dp(4), dp(20), dp(12))
        title.typeface = Fonts.medium
        title.setText(R.string.voice_voice)
        head.addView(title, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        head.addView(counter, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        body.addView(head, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        carousel.count = voices.size
        carousel.create = {
            VoiceCard(context).also { card ->
                card.disc.level = { preview.level() }
                card.disc.setOnClickListener { tapped(card, true) }
                card.setOnClickListener { tapped(card, false) }
            }
        }
        carousel.bind = { i, v ->
            v.tag = i
            (v as VoiceCard).bind(voices[i], voices[i].id == selected)
            paintDisc(v)
        }
        carousel.onPage = {
            removeCallbacks(speak)
            postDelayed(speak, SETTLE_MS)
            sync()
        }
        body.addView(carousel, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        hint.gravity = Gravity.CENTER_HORIZONTAL
        hint.setText(R.string.voice_swipe_hint)
        hint.setPadding(dp(20), dp(12), dp(20), 0)
        body.addView(hint, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        use.typeface = Fonts.medium
        use.gravity = Gravity.CENTER
        use.isClickable = true
        use.isFocusable = true
        use.setOnClickListener {
            val v = voices[carousel.page]
            selected = v.id
            app.prefs[Keys.VOICE_TTS_VOICE] = v.id
            onPick(v)
            dismiss()
        }
        body.addView(use, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(dp(20), dp(12), dp(20), dp(4)) })

        preview.onChanged = { carousel.forEachCard { _, v -> paintDisc(v as VoiceCard) } }
        preview.onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        preview.onMuted = { Toast.makeText(context, R.string.voice_media_muted, Toast.LENGTH_LONG).show() }
        onDismiss = {
            removeCallbacks(speak)
            preview.stop()
        }
        carousel.jumpTo(voices.indexOfFirst { it.id == selected }.coerceAtLeast(0))
        sync()
    }

    /** A side card slides to the centre; the centre card's disc, or the card itself, replays or stops. */
    private fun tapped(card: VoiceCard, disc: Boolean) {
        val i = card.tag as? Int ?: return
        if (i != carousel.page) {
            carousel.scrollTo(i)
        } else if (disc || preview.playing != voices[i].id) {
            removeCallbacks(speak)
            preview.toggle(voices[i], context.uiScope)
        }
    }

    private fun paintDisc(card: VoiceCard) {
        val id = card.voice?.id
        card.disc.state = when {
            id == null || preview.playing != id -> VoiceDisc.State.IDLE
            preview.loading -> VoiceDisc.State.LOADING
            else -> VoiceDisc.State.PLAYING
        }
    }

    private fun sync() {
        val v = voices[carousel.page]
        counter.text = context.getString(R.string.voice_count, carousel.page + 1, voices.size)
        use.text = context.getString(if (v.id == selected) R.string.voice_keep else R.string.voice_use, v.name)
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        use.setTextColor(theme.onAccent)
        use.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        use.background = Shapes.ripple(theme.accentSoft, Shapes.pill(theme.accent), 999f)
        counter.visibility = View.VISIBLE
    }

    private companion object {
        /** A card speaks once the finger has left it this long, so a fast flick past voices stays quiet. */
        const val SETTLE_MS = 150L
    }
}
