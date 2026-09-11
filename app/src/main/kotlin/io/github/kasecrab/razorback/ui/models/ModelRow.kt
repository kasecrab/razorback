package io.github.kasecrab.razorback.ui.models

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Fmt
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.Shapes

/**
 * One model: its name, one quiet line under it, a star, and a check when it is the one
 * in use. The line is the price unless the list has something more telling to say.
 */
class ModelRow(context: Context) : LinearLayout(context), Themed {

    private val name = TextView(context)
    private val meta = TextView(context)
    private val check = ImageView(context)
    val star = IconButton(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        setPadding(dp(16), dp(10), dp(4), dp(10))
        val texts = LinearLayout(context)
        texts.orientation = VERTICAL
        name.typeface = Fonts.regular
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        meta.typeface = Fonts.regular
        meta.maxLines = 1
        meta.ellipsize = android.text.TextUtils.TruncateAt.END
        texts.addView(name, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        texts.addView(meta, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        check.scaleType = ImageView.ScaleType.CENTER
        addView(check, LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(4) })
        star.iconRes = R.drawable.ic_star
        star.contentDescription = context.getString(R.string.cd_favorite)
        addView(star, LayoutParams(dp(44), dp(44)))
        onThemeChanged(context.appTheme)
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        if (handled) Haptics.tick(this)
        return handled
    }

    /** [note] goes in front of the price: a score, an age, whatever the list is ordered by. */
    fun bind(m: ModelInfo, selected: Boolean, favorite: Boolean, note: String? = null) {
        val theme = context.appTheme
        name.text = m.name
        meta.text = if (note == null) price(m) else "$note  ·  ${price(m)}"
        check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        star.iconRes = if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star
        star.tone = if (favorite) IconButton.Tone.ACCENT else IconButton.Tone.SECONDARY
        name.setTextColor(if (selected) theme.accent else theme.textPrimary)
    }

    /** A model the catalogue has not described yet has no price to show, only its id. */
    private fun price(m: ModelInfo): String = when {
        m.contextLength == 0 && m.isFree -> m.id
        m.isFree -> context.getString(R.string.price_free)
        else -> context.getString(R.string.price_per_million, Fmt.perM(m.promptPerM), Fmt.perM(m.completionPerM))
    }

    override fun onThemeChanged(theme: Theme) {
        background = Shapes.ripple(theme.accentSoft, null, 0f)
        name.setTextColor(theme.textPrimary)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        meta.setTextColor(theme.textSecondary)
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        check.setImageDrawable(context.icon(R.drawable.ic_check, theme.accent))
        star.onThemeChanged(theme)
    }
}
