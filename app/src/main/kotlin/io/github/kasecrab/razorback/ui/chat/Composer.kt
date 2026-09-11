package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.LevelRing
import io.github.kasecrab.razorback.ui.widget.Shapes

/**
 * Input card at the bottom of the chat: text on top, a row of controls below.
 * The trailing button is voice mode while the field is empty and send once it has text.
 */
class Composer(context: Context) : LinearLayout(context), Themed {

    val input = EditText(context)
    val attach = IconButton(context)
    val thinkingChip = Chip(context)
    val temporaryChip = Chip(context)
    val dictate = IconButton(context)
    val primary = IconButton(context)
    val strip = AttachStrip(context)
    private val ring = LevelRing(context)

    val dictation = Dictation(context, input)
    var onSend: ((String, List<AttachStrip.Pending>) -> Unit)? = null
    var onAttach: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onVoiceMode: (() -> Unit)? = null

    /** True while a reply streams: the primary button becomes stop. */
    var streaming: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updatePrimary()
        }

    private val row = LinearLayout(context)

    init {
        orientation = VERTICAL
        clipToOutline = true
        // The same inset at both ends: the plus glyph sits 8 dp inside its button, so the
        // filled button on the right gets an 8 dp margin to land on the same line.
        setPadding(dp(8), dp(8), dp(8), dp(8))

        addView(strip, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        input.background = null
        input.setHint(R.string.composer_hint)
        input.typeface = Fonts.regular
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        input.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        input.maxLines = 6
        input.gravity = Gravity.TOP or Gravity.START
        input.setPadding(dp(12), dp(6), dp(12), dp(8))
        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        attach.iconRes = R.drawable.ic_plus
        attach.contentDescription = context.getString(R.string.cd_attach)
        attach.setOnClickListener { onAttach?.invoke() }
        row.addView(attach, LayoutParams(dp(40), dp(40)))

        thinkingChip.style = Chip.Style.PLAIN
        thinkingChip.leadingIcon = R.drawable.ic_brain
        thinkingChip.text = "Off"
        thinkingChip.setPadding(dp(8), 0, dp(10), 0)
        row.addView(thinkingChip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)).apply { marginStart = dp(4) })

        temporaryChip.style = Chip.Style.PLAIN
        temporaryChip.leadingIcon = R.drawable.ic_incognito
        temporaryChip.contentDescription = context.getString(R.string.cd_temporary)
        temporaryChip.setPadding(dp(8), 0, dp(8), 0)
        temporaryChip.compoundDrawablePadding = 0
        row.addView(temporaryChip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)).apply { marginStart = dp(4) })

        row.addView(View(context), LayoutParams(0, 1, 1f))

        dictate.iconRes = R.drawable.ic_mic
        dictate.contentDescription = context.getString(R.string.cd_dictate)
        dictate.setOnClickListener { dictation.toggle() }
        dictate.setOnLongClickListener {
            dictation.start()
            true
        }
        dictate.setOnTouchListener { _, ev ->
            if (ev.actionMasked == android.view.MotionEvent.ACTION_UP && dictation.isListening && ev.eventTime - ev.downTime > 400) {
                dictation.stop()
                true
            } else {
                false
            }
        }
        dictation.onStateChanged = { state -> showListening(state) }
        ring.level = { dictation.level }
        val micHost = FrameLayout(context)
        micHost.addView(ring, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        micHost.addView(dictate, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        row.addView(micHost, LayoutParams(dp(48), dp(48)))

        primary.filled = true
        primary.tone = IconButton.Tone.ON_ACCENT
        row.addView(primary, LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) })
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = updatePrimary()
        })
        primary.setOnClickListener {
            when {
                streaming -> {
                    Haptics.tap(primary)
                    onStop?.invoke()
                }
                input.text.isBlank() && strip.items.isEmpty() -> {
                    Haptics.confirm(primary)
                    onVoiceMode?.invoke()
                }
                else -> {
                    Haptics.confirm(primary)
                    val text = input.text.toString().trim()
                    val pending = ArrayList(strip.items)
                    input.text.clear()
                    strip.clear()
                    onSend?.invoke(text, pending)
                }
            }
        }
        updatePrimary()
        onThemeChanged(context.appTheme)
    }

    /** Drops the text, the attachments and any dictation still settling into the field. */
    fun clearDraft() {
        dictation.release()
        input.text.clear()
        strip.clear()
        updatePrimary()
    }

    /** The mic button breathes and the field says so, because a colour change alone reads as nothing. */
    private fun showListening(state: Dictation.State) {
        val on = state != Dictation.State.OFF
        dictate.tone = if (on) IconButton.Tone.ACCENT else IconButton.Tone.SECONDARY
        ring.waiting = state == Dictation.State.CONNECTING
        ring.active = on
        input.setHint(
            when (state) {
                Dictation.State.OFF -> R.string.composer_hint
                Dictation.State.CONNECTING -> R.string.voice_connecting
                Dictation.State.LISTENING -> R.string.dictation_listening
            },
        )
        if (on) Haptics.confirm(dictate) else Haptics.tap(dictate)
    }

    fun updatePrimary() {
        val hasText = input.text.isNotBlank() || strip.items.isNotEmpty()
        primary.iconRes = when {
            streaming -> R.drawable.ic_stop
            hasText -> R.drawable.ic_arrow_up
            else -> R.drawable.ic_waveform
        }
        primary.contentDescription = context.getString(
            when {
                streaming -> R.string.cd_stop
                hasText -> R.string.cd_send
                else -> R.string.cd_voice_mode
            },
        )
    }

    override fun onThemeChanged(theme: Theme) {
        background = Shapes.rounded(theme.surface, dp(theme.radiusXl), dp(1), theme.outline)
        input.setTextColor(theme.textPrimary)
        input.setHintTextColor(theme.textTertiary)
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        input.highlightColor = theme.accentSoft
    }
}
