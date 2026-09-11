package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.model.Reasoning
import io.github.kasecrab.razorback.model.ThinkingLevel
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Sheet
import io.github.kasecrab.razorback.ui.widget.Shapes

/** How hard the model should think before answering. */
class ThinkingLevelSheet(context: Context) : Sheet(context) {

    private val app = App.instance
    private val title = TextView(context)
    private val rows = ArrayList<Row>()

    private class Row(val level: ThinkingLevel, val view: LinearLayout, val name: TextView, val hint: TextView, val check: ImageView)

    init {
        title.typeface = Fonts.medium
        title.setText(R.string.thinking)
        title.setPadding(dp(20), dp(4), dp(20), dp(8))
        body.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val info = app.catalog.find(app.engine.model)
        val note = when {
            info != null && !info.supportsReasoning -> context.getString(R.string.thinking_unsupported)
            info?.reasoning?.mandatory == true -> context.getString(R.string.thinking_mandatory)
            else -> null
        }
        if (note != null) {
            val c = Caption(context)
            c.text = note
            c.setPadding(dp(20), 0, dp(20), dp(8))
            body.addView(c)
        }
        app.engine.fitThinking()
        val offered = Reasoning.available(info)
        for (level in ThinkingLevel.entries) {
            if (level !in offered) continue
            val v = LinearLayout(context)
            v.orientation = LinearLayout.HORIZONTAL
            v.gravity = Gravity.CENTER_VERTICAL
            v.isClickable = true
            v.setPadding(dp(20), dp(12), dp(20), dp(12))
            val texts = LinearLayout(context)
            texts.orientation = LinearLayout.VERTICAL
            val name = TextView(context)
            name.typeface = Fonts.regular
            name.text = level.label
            val hint = TextView(context)
            hint.typeface = Fonts.regular
            hint.text = level.hint
            texts.addView(name)
            texts.addView(hint)
            v.addView(texts, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            val check = ImageView(context)
            check.scaleType = ImageView.ScaleType.CENTER
            v.addView(check, LinearLayout.LayoutParams(dp(24), dp(24)))
            v.setOnClickListener {
                io.github.kasecrab.razorback.ui.core.Haptics.tick()
                app.engine.thinking = level
                dismiss()
            }
            body.addView(v, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            rows.add(Row(level, v, name, hint, check))
        }
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        val current = app.engine.thinking
        for (r in rows) {
            r.view.background = Shapes.ripple(theme.accentSoft, null, 0f)
            r.name.setTextColor(if (r.level == current) theme.accent else theme.textPrimary)
            r.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            r.hint.setTextColor(theme.textSecondary)
            r.hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
            r.check.setImageDrawable(context.icon(R.drawable.ic_check, theme.accent))
            r.check.visibility = if (r.level == current) View.VISIBLE else View.INVISIBLE
        }
    }
}
