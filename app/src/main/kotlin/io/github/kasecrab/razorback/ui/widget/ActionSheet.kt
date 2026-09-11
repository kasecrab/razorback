package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon

/** A short list of actions in a bottom sheet. */
class ActionSheet(context: Context) : Sheet(context) {

    private class Item(val icon: Int, val label: String, val danger: Boolean, val row: LinearLayout, val image: ImageView, val text: TextView)

    private val items = ArrayList<Item>(4)
    private var title: TextView? = null
    private var text: TextView? = null

    /** A heading and a line of explanation above the actions. */
    fun header(heading: String, explanation: String): ActionSheet {
        val t = TextView(context)
        t.typeface = Fonts.medium
        t.text = heading
        t.setPadding(dp(20), dp(4), dp(20), dp(4))
        body.addView(t, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val e = TextView(context)
        e.typeface = Fonts.regular
        e.text = explanation
        e.setPadding(dp(20), 0, dp(20), dp(12))
        body.addView(e, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        title = t
        text = e
        return this
    }

    fun add(icon: Int, label: String, danger: Boolean = false, onClick: () -> Unit): ActionSheet {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.isClickable = true
        row.setPadding(dp(20), dp(14), dp(20), dp(14))
        val image = ImageView(context)
        image.scaleType = ImageView.ScaleType.CENTER
        row.addView(image, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
        val text = TextView(context)
        text.typeface = Fonts.regular
        text.text = label
        row.addView(text, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            dismiss()
            onClick()
        }
        body.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        items.add(Item(icon, label, danger, row, image, text))
        return this
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        body.setPadding(0, 0, 0, dp(4))
        title?.let {
            it.setTextColor(theme.textPrimary)
            it.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        }
        text?.let {
            it.setTextColor(theme.textSecondary)
            it.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        }
        for (it in items) {
            val color = if (it.danger) theme.danger else theme.textPrimary
            it.row.background = Shapes.ripple(theme.accentSoft, null, 0f)
            it.image.setImageDrawable(context.icon(it.icon, if (it.danger) theme.danger else theme.textSecondary))
            it.text.setTextColor(color)
            it.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        }
    }
}
