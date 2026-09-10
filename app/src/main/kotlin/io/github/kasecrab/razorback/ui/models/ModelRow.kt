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
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.Shapes

/** One model: name, id, context and price, capability glyphs, star, and a check when selected. */
class ModelRow(context: Context) : LinearLayout(context), Themed {

    private val name = TextView(context)
    private val meta = TextView(context)
    private val caps = LinearLayout(context)
    private val check = ImageView(context)
    val star = IconButton(context)

    private val capReasoning = ImageView(context)
    private val capTools = ImageView(context)
    private val capVision = ImageView(context)
    private val capImage = ImageView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        setPadding(dp(16), dp(10), dp(4), dp(10))
        val texts = LinearLayout(context)
        texts.orientation = VERTICAL
        name.typeface = Fonts.medium
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        meta.typeface = Fonts.regular
        meta.maxLines = 1
        meta.ellipsize = android.text.TextUtils.TruncateAt.END
        texts.addView(name, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        texts.addView(meta, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        caps.orientation = HORIZONTAL
        for (v in listOf(capReasoning, capTools, capVision, capImage)) {
            v.scaleType = ImageView.ScaleType.CENTER
            caps.addView(v, LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(6) })
        }
        texts.addView(caps, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        check.scaleType = ImageView.ScaleType.CENTER
        addView(check, LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(4) })
        star.iconRes = R.drawable.ic_star
        star.contentDescription = context.getString(R.string.cd_favorite)
        addView(star, LayoutParams(dp(44), dp(44)))
        onThemeChanged(context.appTheme)
    }

    fun bind(m: ModelInfo, selected: Boolean, favorite: Boolean) {
        val theme = context.appTheme
        name.text = m.name
        val parts = ArrayList<String>(3)
        parts.add(m.id)
        // A model the catalogue has not described yet shows only its id.
        if (m.contextLength > 0) parts.add("${Fmt.context(m.contextLength)} ctx")
        if (m.contextLength > 0 || !m.isFree) parts.add(if (m.isFree) "free" else "${Fmt.perM(m.promptPerM)} / ${Fmt.perM(m.completionPerM)} per M")
        meta.text = parts.joinToString("  ·  ")
        capReasoning.visibility = if (m.supportsReasoning) View.VISIBLE else View.GONE
        capTools.visibility = if (m.supportsTools) View.VISIBLE else View.GONE
        capVision.visibility = if (m.acceptsImages) View.VISIBLE else View.GONE
        capImage.visibility = if (m.producesImages) View.VISIBLE else View.GONE
        caps.visibility = if (m.supportsReasoning || m.supportsTools || m.acceptsImages || m.producesImages) View.VISIBLE else View.GONE
        check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        star.iconRes = if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star
        star.tone = if (favorite) IconButton.Tone.ACCENT else IconButton.Tone.SECONDARY
        name.setTextColor(if (selected) theme.accent else theme.textPrimary)
    }

    override fun onThemeChanged(theme: Theme) {
        background = Shapes.ripple(theme.accentSoft, null, 0f)
        name.setTextColor(theme.textPrimary)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        meta.setTextColor(theme.textSecondary)
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        capReasoning.setImageDrawable(context.icon(R.drawable.ic_brain, theme.textTertiary))
        capTools.setImageDrawable(context.icon(R.drawable.ic_wrench, theme.textTertiary))
        capVision.setImageDrawable(context.icon(R.drawable.ic_eye, theme.textTertiary))
        capImage.setImageDrawable(context.icon(R.drawable.ic_image, theme.textTertiary))
        check.setImageDrawable(context.icon(R.drawable.ic_check, theme.accent))
        star.onThemeChanged(theme)
    }
}
