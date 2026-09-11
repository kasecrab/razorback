package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.ui.core.Accents
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.ThemeMode
import io.github.kasecrab.razorback.ui.core.ThemeResolver
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.SliderView
import io.github.kasecrab.razorback.ui.widget.TopBar

/** Theme mode, accent, text size and motion. Every change applies while you look at it. */
class AppearanceScreen(context: Context) : Screen(context) {

    private val prefs = App.instance.prefs
    private val bar = TopBar(context)
    private val modes = LinearLayout(context)
    private val dots = LinearLayout(context)
    private val hue = SliderView(context)
    private val size = SliderView(context)
    private val modeChips = ArrayList<Pair<Chip, String>>()
    private val dotViews = ArrayList<Pair<Dot, String>>()
    // A choice that leaves the theme as it is (dark picked while the system is already dark)
    // never reaches onThemeChanged, so the chips follow the preferences directly.
    private val onPref: (String) -> Unit = { if (it.startsWith("theme.")) sync() }

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.appearance))
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(32))

        list.addView(SectionHeader(context).apply { setText(R.string.theme) })
        modes.orientation = LinearLayout.HORIZONTAL
        modes.setPadding(dp(16), 0, dp(16), 0)
        for ((label, key) in listOf(R.string.theme_system to "system", R.string.theme_light to "light", R.string.theme_dark to "dark", R.string.theme_amoled to "amoled")) {
            val c = Chip(context)
            c.setText(label)
            c.setOnClickListener { setMode(key) }
            modes.addView(c, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(8) })
            modeChips.add(c to key)
        }
        list.addView(modes)

        list.addView(SectionHeader(context).apply { setText(R.string.accent) })
        dots.orientation = LinearLayout.HORIZONTAL
        dots.setPadding(dp(16), 0, dp(16), dp(8))
        for ((i, a) in Accents.all.withIndex()) addDot(a.color, "p$i")
        addDot(ThemeResolver.accent(context, "dyn", context.appTheme.mode), "dyn", wallpaper = true)
        list.addView(dots)
        hue.min = 0f
        hue.max = 360f
        hue.step = 5f
        hue.onChange = { prefs[Keys.ACCENT] = "h${it.toInt()}" }
        list.addView(labelRow(R.string.accent_custom))
        list.addView(hue, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, dp(8), 0) })

        list.addView(SectionHeader(context).apply { setText(R.string.text_size) })
        size.min = 0.85f
        size.max = 1.3f
        size.step = 0.05f
        size.value = prefs[Keys.FONT_SCALE]
        size.onChange = { prefs[Keys.FONT_SCALE] = it }
        list.addView(size, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, dp(8), 0) })

        val motion = SwitchRow(context)
        motion.set(context.getString(R.string.reduce_motion), context.getString(R.string.reduce_motion_hint), prefs[Keys.REDUCE_MOTION]) { prefs[Keys.REDUCE_MOTION] = it }
        val haptics = SwitchRow(context)
        haptics.set(context.getString(R.string.haptics), context.getString(R.string.haptics_hint), prefs[Keys.HAPTICS]) { prefs[Keys.HAPTICS] = it }
        list.addView(motion, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        list.addView(haptics, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val flutter = SwitchRow(context)
        flutter.set(context.getString(R.string.haptics_stream), context.getString(R.string.haptics_stream_hint), prefs[Keys.HAPTICS_STREAM]) { prefs[Keys.HAPTICS_STREAM] = it }
        list.addView(flutter, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        sync()
    }

    private fun labelRow(res: Int): View = io.github.kasecrab.razorback.ui.widget.Caption(context).apply {
        setText(res)
        setPadding(dp(16), dp(4), dp(16), 0)
    }

    private fun addDot(color: Int, spec: String, wallpaper: Boolean = false) {
        val d = Dot(context, color, wallpaper)
        d.setOnClickListener {
            io.github.kasecrab.razorback.ui.core.Haptics.tick()
            prefs[Keys.ACCENT] = spec
        }
        dots.addView(d, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        dotViews.add(d to spec)
    }

    private fun setMode(key: String) {
        if (key == "system") {
            prefs[Keys.THEME_FOLLOW_SYSTEM] = true
        } else {
            prefs[Keys.THEME_FOLLOW_SYSTEM] = false
            prefs[Keys.THEME_MODE] = when (key) {
                "light" -> ThemeMode.LIGHT
                "amoled" -> ThemeMode.AMOLED
                else -> ThemeMode.DARK
            }
        }
    }

    private fun sync() {
        val follow = prefs[Keys.THEME_FOLLOW_SYSTEM]
        val mode = prefs[Keys.THEME_MODE]
        for ((chip, key) in modeChips) {
            chip.active = when (key) {
                "system" -> follow
                "light" -> !follow && mode == ThemeMode.LIGHT
                "dark" -> !follow && mode == ThemeMode.DARK
                else -> !follow && mode == ThemeMode.AMOLED
            }
        }
        val accent = prefs[Keys.ACCENT]
        for ((dot, spec) in dotViews) dot.chosen = spec == accent
        if (accent.startsWith("h")) hue.value = accent.substring(1).toFloatOrNull() ?: 240f
    }

    override fun onEnter() = prefs.onChange(onPref)

    override fun onExit() = prefs.removeOnChange(onPref)

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        sync()
        for ((dot, spec) in dotViews) if (spec == "dyn") dot.color = ThemeResolver.accent(context, "dyn", theme.mode)
    }

    /** A colour swatch with a ring when chosen. */
    private class Dot(context: Context, var color: Int, private val wallpaper: Boolean) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
        }
        var chosen = false
            set(v) {
                field = v
                invalidate()
            }

        init {
            isClickable = true
        }

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            fill.color = color
            canvas.drawCircle(r, r, r - dp(4f), fill)
            if (wallpaper) {
                fill.color = context.getColor(android.R.color.system_accent2_400)
                canvas.drawCircle(r, r, r * 0.35f, fill)
            }
            if (chosen) {
                ring.color = context.appTheme.textPrimary
                canvas.drawCircle(r, r, r - dp(1f), ring)
            }
        }
    }
}
