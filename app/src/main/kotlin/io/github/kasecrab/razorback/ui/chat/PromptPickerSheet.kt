package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.settings.NavRow
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Sheet
import kotlinx.coroutines.launch

/** Saved prompts to drop into the composer. */
class PromptPickerSheet(context: Context, private val onPick: (String) -> Unit) : Sheet(context) {

    private val title = TextView(context)
    private val rows = LinearLayout(context)

    init {
        title.typeface = Fonts.medium
        title.setText(R.string.saved_prompts)
        title.setPadding(dp(20), dp(4), dp(20), dp(8))
        body.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        rows.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        context.uiScope.launch {
            val prompts = App.instance.prompts.list()
            if (prompts.isEmpty()) {
                val c = Caption(context)
                c.setText(R.string.saved_prompts_empty)
                c.setPadding(dp(20), 0, dp(20), dp(16))
                rows.addView(c)
            }
            for (p in prompts) {
                val row = NavRow(context)
                row.set(R.drawable.ic_file, p.name, p.text.lineSequence().firstOrNull()?.take(80))
                row.setOnClickListener {
                    dismiss()
                    onPick(p.text)
                }
                rows.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        }
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
    }
}
