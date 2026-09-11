package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.dp

/** One text field and a save button, for renames and short edits. */
class InputSheet(context: Context, title: String, initial: String, private val onSave: (String) -> Unit) : Sheet(context) {

    private val heading = TextView(context)
    private val field = TextField(context)

    init {
        heading.typeface = Fonts.medium
        heading.text = title
        heading.setPadding(dp(20), dp(4), dp(20), dp(12))
        body.addView(heading, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        field.text = initial
        body.addView(field, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(20), 0, dp(20), dp(12)) })
        val actions = LinearLayout(context)
        actions.gravity = Gravity.END
        val save = Chip(context)
        save.style = Chip.Style.ACCENT
        save.setText(R.string.save)
        save.setOnClickListener { commit() }
        actions.addView(save, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))
        body.addView(actions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(20), 0, dp(20), dp(8)) })
        field.edit.setOnEditorActionListener { _, _, _ ->
            commit()
            true
        }
    }

    private fun commit() {
        val text = field.text.trim()
        if (text.isEmpty()) {
            io.github.kasecrab.razorback.ui.core.Haptics.reject(field)
            return
        }
        io.github.kasecrab.razorback.ui.core.Haptics.confirm(field)
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.edit.windowToken, 0)
        dismiss()
        onSave(text)
    }

    override val wantsKeyboard: Boolean get() = true

    override fun show() {
        super.show()
        field.edit.requestFocus()
        field.edit.setSelection(field.text.length)
        field.postDelayed({ context.getSystemService(InputMethodManager::class.java).showSoftInput(field.edit, 0) }, 150)
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        heading.setTextColor(theme.textPrimary)
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
    }
}
