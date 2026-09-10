package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/** Multi-line outlined editor for prompts. */
class TextArea(context: Context) : FrameLayout(context), Themed {

    val edit = EditText(context)
    var onTextChanged: ((String) -> Unit)? = null

    var text: String
        get() = edit.text.toString()
        set(value) {
            if (edit.text.toString() != value) edit.setText(value)
        }

    init {
        edit.background = null
        edit.typeface = Fonts.regular
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        edit.gravity = Gravity.TOP or Gravity.START
        edit.minLines = 4
        edit.setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(edit, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        edit.setOnFocusChangeListener { _, _ -> onThemeChanged(context.appTheme) }
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                onTextChanged?.invoke(s?.toString() ?: "")
            }
        })
        onThemeChanged(context.appTheme)
    }

    fun setHint(resId: Int) = edit.setHint(resId)

    override fun onThemeChanged(theme: Theme) {
        val focused = edit.hasFocus()
        background = Shapes.rounded(theme.surface, dp(theme.radiusM), dp(if (focused) 2 else 1), if (focused) theme.accent else theme.outline)
        edit.setTextColor(theme.textPrimary)
        edit.setHintTextColor(theme.textTertiary)
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        edit.highlightColor = theme.accentSoft
    }
}
