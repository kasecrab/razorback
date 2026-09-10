package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon

/** Pick one of a few named options. */
class ChoiceSheet(
    context: Context,
    titleText: String,
    private val options: List<Pair<String, String>>,
    private var selected: String,
    private val onPick: (String) -> Unit,
) : Sheet(context) {

    private val title = TextView(context)
    private val rows = ArrayList<Triple<LinearLayout, TextView, ImageView>>()

    init {
        title.typeface = Fonts.medium
        title.text = titleText
        title.setPadding(dp(20), dp(4), dp(20), dp(8))
        body.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        for ((value, label) in options) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.isClickable = true
            row.setPadding(dp(20), dp(13), dp(20), dp(13))
            val text = TextView(context)
            text.typeface = Fonts.regular
            text.text = label
            row.addView(text, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            val check = ImageView(context)
            check.scaleType = ImageView.ScaleType.CENTER
            row.addView(check, LinearLayout.LayoutParams(dp(24), dp(24)))
            row.setOnClickListener {
                selected = value
                onPick(value)
                dismiss()
            }
            list.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            rows.add(Triple(row, text, check))
        }
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        for ((i, r) in rows.withIndex()) {
            val on = options[i].first == selected
            r.first.background = Shapes.ripple(theme.accentSoft, null, 0f)
            r.second.setTextColor(if (on) theme.accent else theme.textPrimary)
            r.second.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            r.third.setImageDrawable(context.icon(R.drawable.ic_check, theme.accent))
            r.third.visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
    }
}
