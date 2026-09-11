package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon

/** A flat pill for filtering a list: a search glyph in front, the text, and a clear button once there is any. */
class SearchBox(context: Context) : FrameLayout(context), Themed {

    val edit = EditText(context)
    private val glyph = ImageView(context)
    private val clear = IconButton(context)

    var text: String
        get() = edit.text.toString()
        set(value) {
            if (edit.text.toString() != value) edit.setText(value)
        }

    var onTextChanged: ((String) -> Unit)? = null

    init {
        glyph.scaleType = ImageView.ScaleType.CENTER
        addView(glyph, LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL))
        edit.background = null
        edit.typeface = Fonts.regular
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        edit.maxLines = 1
        edit.isSingleLine = true
        edit.gravity = Gravity.CENTER_VERTICAL
        edit.setPadding(dp(42), 0, dp(44), 0)
        addView(edit, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        clear.iconRes = R.drawable.ic_close
        clear.tone = IconButton.Tone.SECONDARY
        clear.contentDescription = context.getString(R.string.cd_clear_search)
        clear.visibility = View.GONE
        clear.setOnClickListener { edit.setText("") }
        addView(clear, LayoutParams(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL))
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val t = s?.toString() ?: ""
                clear.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE
                onTextChanged?.invoke(t)
            }
        })
        onThemeChanged(context.appTheme)
    }

    fun setHint(resId: Int) = edit.setHint(resId)

    override fun onThemeChanged(theme: Theme) {
        background = Shapes.pill(theme.surface)
        glyph.setImageDrawable(context.icon(R.drawable.ic_search, theme.textTertiary))
        edit.setTextColor(theme.textPrimary)
        edit.setHintTextColor(theme.textTertiary)
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        edit.highlightColor = theme.accentSoft
        clear.onThemeChanged(theme)
    }
}
