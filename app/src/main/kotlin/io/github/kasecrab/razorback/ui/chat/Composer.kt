package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.Shapes

/**
 * Input card at the bottom of the chat: text on top, a row of controls below.
 * The trailing button is voice mode while the field is empty and send once it has text.
 */
class Composer(context: Context) : LinearLayout(context), Themed {

    val input = EditText(context)
    val attach = IconButton(context)
    val modelChip = Chip(context)
    val thinkingChip = Chip(context)
    val temporaryChip = Chip(context)
    val dictate = IconButton(context)
    val primary = IconButton(context)

    var onSend: ((String) -> Unit)? = null
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
        setPadding(dp(6), dp(8), dp(6), dp(6))

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
        row.addView(attach, LayoutParams(dp(40), dp(40)))

        modelChip.style = Chip.Style.PLAIN
        modelChip.trailingIcon = R.drawable.ic_chevron_down
        modelChip.contentDescription = context.getString(R.string.cd_model)
        modelChip.text = "Model"
        row.addView(modelChip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))

        thinkingChip.style = Chip.Style.PLAIN
        thinkingChip.leadingIcon = R.drawable.ic_brain
        thinkingChip.text = "Off"
        row.addView(thinkingChip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))

        temporaryChip.style = Chip.Style.PLAIN
        temporaryChip.leadingIcon = R.drawable.ic_incognito
        temporaryChip.contentDescription = context.getString(R.string.cd_temporary)
        temporaryChip.setPadding(dp(8), 0, dp(8), 0)
        temporaryChip.compoundDrawablePadding = 0
        row.addView(temporaryChip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))

        row.addView(View(context), LayoutParams(0, 1, 1f))

        dictate.iconRes = R.drawable.ic_mic
        dictate.contentDescription = context.getString(R.string.cd_dictate)
        row.addView(dictate, LayoutParams(dp(40), dp(40)))

        primary.filled = true
        primary.tone = IconButton.Tone.ON_ACCENT
        row.addView(primary, LayoutParams(dp(40), dp(40)).apply { marginStart = dp(2) })
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = updatePrimary()
        })
        primary.setOnClickListener {
            when {
                streaming -> onStop?.invoke()
                input.text.isBlank() -> onVoiceMode?.invoke()
                else -> {
                    val text = input.text.toString().trim()
                    input.text.clear()
                    onSend?.invoke(text)
                }
            }
        }
        updatePrimary()
        onThemeChanged(context.appTheme)
    }

    private fun updatePrimary() {
        val hasText = input.text.isNotBlank()
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
