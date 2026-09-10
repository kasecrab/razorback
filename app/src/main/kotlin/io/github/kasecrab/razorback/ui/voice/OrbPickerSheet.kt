package io.github.kasecrab.razorback.ui.voice

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.orb.Orb
import io.github.kasecrab.razorback.ui.orb.OrbView
import io.github.kasecrab.razorback.ui.orb.Orbs
import io.github.kasecrab.razorback.ui.widget.Sheet
import io.github.kasecrab.razorback.ui.widget.Shapes

/** Every orb, alive in miniature; tap one to use it. */
class OrbPickerSheet(context: Context, private val onPick: (Orb) -> Unit) : Sheet(context) {

    private val title = TextView(context)
    private val labels = ArrayList<Pair<TextView, Orb>>()
    private val cards = ArrayList<Pair<LinearLayout, Orb>>()

    init {
        title.typeface = Fonts.medium
        title.setText(R.string.cd_orb_style)
        title.setPadding(dp(20), dp(4), dp(20), dp(8))
        body.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(12), 0, dp(12), dp(12))
        val current = App.instance.prefs[Keys.VOICE_ORB]
        for (orb in Orbs.all) {
            val card = LinearLayout(context)
            card.orientation = LinearLayout.VERTICAL
            card.gravity = Gravity.CENTER_HORIZONTAL
            card.isClickable = true
            card.setPadding(dp(6), dp(6), dp(6), dp(8))
            val preview = OrbView(context)
            preview.orb = orb
            preview.preview = true
            preview.state = Orb.LISTENING
            preview.clipToOutline = true
            preview.background = Shapes.rounded(0, dp(16f))
            card.addView(preview, LinearLayout.LayoutParams(dp(104), dp(104)))
            val label = TextView(context)
            label.typeface = Fonts.medium
            label.text = orb.name
            label.setPadding(0, dp(6), 0, 0)
            card.addView(label)
            card.setOnClickListener {
                App.instance.prefs[Keys.VOICE_ORB] = orb.id
                onPick(orb)
                dismiss()
            }
            row.addView(card, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
            labels.add(label to orb)
            cards.add(card to orb)
        }
        val scroller = HorizontalScrollView(context)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(row, ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        body.addView(scroller, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        highlight(current)
    }

    private fun highlight(id: String) {
        for ((card, orb) in cards) {
            val theme = context.appTheme
            card.background = if (orb.id == id) Shapes.rounded(theme.accentSoft, dp(theme.radiusL)) else null
        }
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        for ((label, _) in labels) {
            label.setTextColor(theme.textSecondary)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        }
        highlight(App.instance.prefs[Keys.VOICE_ORB])
    }
}
