package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.graphics.Rect
import android.text.GetChars
import android.text.InputType
import android.text.TextUtils
import android.text.method.TransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/** Outlined single-line field; [secret] masks the middle of the text, with a reveal toggle. */
class TextField(context: Context) : FrameLayout(context), Themed {

    val edit = EditText(context)
    private val reveal = IconButton(context)
    private var revealed = false

    var secret: Boolean = false
        set(value) {
            field = value
            reveal.visibility = if (value) View.VISIBLE else View.GONE
            applyMask()
        }

    var text: String
        get() = edit.text.toString()
        set(value) {
            if (edit.text.toString() != value) edit.setText(value)
        }

    var onTextChanged: ((String) -> Unit)? = null

    init {
        edit.background = null
        edit.typeface = Fonts.regular
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        edit.maxLines = 1
        edit.isSingleLine = true
        edit.gravity = Gravity.CENTER_VERTICAL
        edit.setPadding(dp(14), 0, dp(48), 0)
        addView(edit, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        reveal.visibility = View.GONE
        reveal.setOnClickListener {
            revealed = !revealed
            applyMask()
        }
        addView(reveal, LayoutParams(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(2) })
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

    private fun applyMask() {
        val mask = secret && !revealed
        val sel = edit.selectionEnd
        edit.transformationMethod = if (mask) EndsMask else null
        edit.setSelection(sel.coerceIn(0, edit.text.length))
        reveal.iconRes = if (mask) R.drawable.ic_eye else R.drawable.ic_eye_off
        reveal.contentDescription = context.getString(if (mask) R.string.cd_show_key else R.string.cd_hide_key)
    }

    override fun onThemeChanged(theme: Theme) {
        val focused = edit.hasFocus()
        background = Shapes.rounded(
            theme.surface,
            dp(theme.radiusM),
            dp(if (focused) 2 else 1),
            if (focused) theme.accent else theme.outline,
        )
        edit.setTextColor(theme.textPrimary)
        edit.setHintTextColor(theme.textTertiary)
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        edit.highlightColor = theme.accentSoft
        reveal.onThemeChanged(theme)
    }
}

/**
 * Shows the two ends of a key and hides the rest, so a key can be told apart at a glance
 * without a working key sitting on screen. Text too short for that is hidden whole, since
 * its ends would be most of it.
 */
private object EndsMask : TransformationMethod {

    private const val HEAD = 6
    private const val TAIL = 4
    private const val DOT = '•'

    override fun getTransformation(source: CharSequence, view: View): CharSequence = Masked(source)

    override fun onFocusChanged(view: View?, sourceText: CharSequence?, focused: Boolean, direction: Int, previous: Rect?) {}

    private fun shown(index: Int, length: Int): Boolean {
        if (length <= HEAD + TAIL + 2) return false
        return index < HEAD || index >= length - TAIL
    }

    /** Reads through to the live text, so what is typed still shows up as it is typed. */
    private class Masked(private val source: CharSequence) : CharSequence, GetChars {

        override val length: Int get() = source.length

        override fun get(index: Int): Char = if (shown(index, source.length)) source[index] else DOT

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            val out = CharArray(endIndex - startIndex)
            getChars(startIndex, endIndex, out, 0)
            return String(out)
        }

        override fun getChars(start: Int, end: Int, dest: CharArray, destoff: Int) {
            TextUtils.getChars(source, start, end, dest, destoff)
            val n = source.length
            for (i in start until end) if (!shown(i, n)) dest[destoff + i - start] = DOT
        }

        override fun toString(): String = subSequence(0, length).toString()
    }
}
