package io.github.kasecrab.razorback.ui.voice

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Carousel
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.Sheet
import io.github.kasecrab.razorback.ui.widget.Shapes
import io.github.kasecrab.razorback.voice.Voice
import io.github.kasecrab.razorback.voice.VoiceCatalog
import io.github.kasecrab.razorback.voice.VoicePreview
import kotlinx.coroutines.launch

/**
 * Voices as cards to swipe through, narrowed by language, gender and tone. It opens on
 * the voice in use; every card that lands speaks its sample, and the disc replays or stops.
 */
class VoicePickerSheet(context: Context, private val onPick: (Voice) -> Unit) : Sheet(context) {

    private enum class Filter { LANGUAGE, GENDER, TONE }

    private val app = App.instance
    private val preview = VoicePreview(context) { app.secrets.get(Secrets.DEEPGRAM) }
    private val catalog = app.voices
    private var all: List<Voice> = emptyList()
    private var voices: List<Voice> = emptyList()
    private val title = TextView(context)
    private val counter = Caption(context)
    private val tabs = Filter.entries.map { FilterTab(context) }
    private val strip = LinearLayout(context)
    private val carousel = Carousel(context)
    private val hint = Caption(context)
    private val use = TextView(context)
    private var selected = app.prefs[Keys.VOICE_TTS_VOICE]
    private var open = Filter.LANGUAGE
    private var language = ""
    private var gender = ""
    private var tone = ""
    private val speak = Runnable { if (voices.isNotEmpty()) preview.play(voices[carousel.page], context.uiScope) }

    init {
        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(20), dp(4), dp(20), dp(4))
        title.typeface = Fonts.medium
        title.setText(R.string.voice_voice)
        head.addView(title, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        head.addView(counter, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        body.addView(head, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val tabRow = LinearLayout(context)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.setPadding(dp(12), 0, dp(12), 0)
        for ((i, tab) in tabs.withIndex()) {
            tab.setOnClickListener {
                open = Filter.entries[i]
                fillStrip()
            }
            tabRow.addView(tab, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        body.addView(tabRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val scroller = HorizontalScrollView(context)
        scroller.isHorizontalScrollBarEnabled = false
        strip.orientation = LinearLayout.HORIZONTAL
        strip.setPadding(dp(18), dp(6), dp(18), dp(10))
        scroller.addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        body.addView(scroller, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

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
        hint.setPadding(dp(20), dp(10), dp(20), 0)
        body.addView(hint, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        use.typeface = Fonts.medium
        use.gravity = Gravity.CENTER
        use.isClickable = true
        use.isFocusable = true
        use.setOnClickListener {
            if (voices.isEmpty()) return@setOnClickListener
            io.github.kasecrab.razorback.ui.core.Haptics.confirm()
            val v = voices[carousel.page]
            selected = v.id
            app.prefs[Keys.VOICE_TTS_VOICE] = v.id
            onPick(v)
            dismiss()
        }
        body.addView(use, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(dp(20), dp(10), dp(20), dp(4)) })

        preview.onChanged = { carousel.forEachCard { _, v -> paintDisc(v as VoiceCard) } }
        preview.onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        preview.onMuted = { Toast.makeText(context, R.string.voice_media_muted, Toast.LENGTH_LONG).show() }
        onDismiss = {
            removeCallbacks(speak)
            preview.stop()
        }
        takeCatalog(catalog.voices)
        if (catalog.isStale) {
            context.uiScope.launch {
                val changed = try {
                    catalog.refresh()
                } catch (e: Exception) {
                    if (all.isEmpty()) Toast.makeText(context, R.string.voice_catalog_failed, Toast.LENGTH_SHORT).show()
                    false
                }
                if (changed) takeCatalog(catalog.voices)
            }
        }
    }

    /** Swaps in a catalogue; the voice in use is always listed, even before the first fetch. */
    private fun takeCatalog(list: List<Voice>) {
        all = if (list.any { it.id == selected }) list else listOf(Voice.placeholder(selected)) + list
        if (language.isEmpty() || all.none { it.language == language }) language = all.first { it.id == selected }.language
        if (tone.isNotEmpty() && catalog.tones().none { it.first == tone }) tone = ""
        fillStrip()
        applyFilters(play = false)
    }

    /** Options for the open tab, the chosen one lit. */
    private fun fillStrip() {
        for ((i, tab) in tabs.withIndex()) tab.active = Filter.entries[i] == open
        tabs[0].set(R.string.voice_filter_language, if (language.isEmpty()) context.getString(R.string.voice_any) else VoiceCatalog.languageName(language))
        tabs[1].set(R.string.voice_filter_gender, context.getString(when (gender) { "f" -> R.string.voice_feminine; "m" -> R.string.voice_masculine; else -> R.string.voice_any }))
        tabs[2].set(R.string.voice_filter_tone, if (tone.isEmpty()) context.getString(R.string.voice_any) else tone.replaceFirstChar { it.uppercase() })
        strip.removeAllViews()
        val any = context.getString(R.string.voice_any)
        val options: List<Pair<String, String>> = when (open) {
            Filter.LANGUAGE -> listOf("" to any) + catalog.languages()
            Filter.GENDER -> listOf("" to any, "f" to context.getString(R.string.voice_feminine), "m" to context.getString(R.string.voice_masculine))
            Filter.TONE -> listOf("" to any) + catalog.tones()
        }
        val current = when (open) {
            Filter.LANGUAGE -> language
            Filter.GENDER -> gender
            Filter.TONE -> tone
        }
        for ((key, label) in options) {
            val chip = Chip(context)
            chip.style = Chip.Style.PLAIN
            chip.text = label
            chip.active = key == current
            chip.setOnClickListener {
                when (open) {
                    Filter.LANGUAGE -> language = key
                    Filter.GENDER -> gender = key
                    Filter.TONE -> tone = key
                }
                fillStrip()
                applyFilters(play = true)
            }
            strip.addView(chip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
    }

    private fun applyFilters(play: Boolean) {
        removeCallbacks(speak)
        preview.stop()
        voices = all.filter { v ->
            (language.isEmpty() || v.language == language) &&
                (gender.isEmpty() || (gender == "f") == v.feminine) &&
                (tone.isEmpty() || tone in v.tags)
        }
        carousel.count = voices.size
        carousel.jumpTo(voices.indexOfFirst { it.id == selected }.coerceAtLeast(0))
        if (play && voices.isNotEmpty()) postDelayed(speak, SETTLE_MS)
        sync()
    }

    /** A side card slides to the centre; the centre card's disc, or the card itself, replays or stops. */
    private fun tapped(card: VoiceCard, disc: Boolean) {
        val i = card.tag as? Int ?: return
        if (i >= voices.size) return
        io.github.kasecrab.razorback.ui.core.Haptics.tick()
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
        val empty = voices.isEmpty()
        counter.text = if (empty) context.getString(R.string.voice_none_match) else context.getString(R.string.voice_count, carousel.page + 1, voices.size)
        hint.visibility = if (voices.size > 1) View.VISIBLE else View.INVISIBLE
        use.alpha = if (empty) 0.38f else 1f
        use.isEnabled = !empty
        if (!empty) {
            val v = voices[carousel.page]
            use.text = context.getString(if (v.id == selected) R.string.voice_keep else R.string.voice_use, v.name)
        }
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        use.setTextColor(theme.onAccent)
        use.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        use.background = Shapes.ripple(theme.accentSoft, Shapes.pill(theme.accent), 999f)
    }

    /** One filter as a tab: what it filters on top, the chosen value beneath, a bar when open. */
    private class FilterTab(context: Context) : LinearLayout(context), Themed {

        private val label = TextView(context)
        private val value = TextView(context)
        private val bar = View(context)

        var active = false
            set(v) {
                if (field == v) return
                field = v
                paint(context.appTheme)
            }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(6))
            label.typeface = Fonts.medium
            label.isAllCaps = true
            label.letterSpacing = 0.06f
            value.typeface = Fonts.medium
            value.maxLines = 1
            addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
            addView(bar, LayoutParams(dp(24), dp(2)).apply { topMargin = dp(6) })
            onThemeChanged(context.appTheme)
        }

        fun set(labelRes: Int, valueText: String) {
            label.setText(labelRes)
            value.text = valueText
        }

        override fun onThemeChanged(theme: Theme) = paint(theme)

        private fun paint(theme: Theme) {
            label.setTextColor(theme.textTertiary)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION) * 0.85f)
            value.setTextColor(if (active) theme.accent else theme.textPrimary)
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
            bar.background = Shapes.pill(if (active) theme.accent else 0)
            background = Shapes.ripple(theme.accentSoft, null, dp(theme.radiusM))
        }
    }

    private companion object {
        /** A card speaks once the finger has left it this long, so a fast flick past voices stays quiet. */
        const val SETTLE_MS = 150L
    }
}
