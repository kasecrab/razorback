package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/** Greeting shown before the first message: a still orb and a line of text. */
class EmptyState(context: Context) : LinearLayout(context), Themed {

    private val orb = OrbGlyph(context)
    private val greeting = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(orb, LayoutParams(dp(96), dp(96)).apply { bottomMargin = dp(24) })
        greeting.typeface = Fonts.medium
        greeting.gravity = Gravity.CENTER
        greeting.setText(R.string.empty_greeting)
        addView(greeting, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        greeting.setTextColor(theme.textPrimary)
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        orb.onThemeChanged(theme)
    }

    private class OrbGlyph(context: Context) : View(context), Themed {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var accent = 0
        private var bg = 0

        override fun onThemeChanged(theme: Theme) {
            accent = theme.accent
            bg = theme.bg
            rebuild()
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

        private fun rebuild() {
            if (width == 0) return
            val r = width / 2f
            val soft = accent and 0x00FFFFFF
            // Light falls from the upper left; the far edge dissolves into the background
            // rather than ending in a hard rim.
            paint.shader = RadialGradient(
                r * 0.78f, r * 0.7f, r * 1.25f,
                intArrayOf(0xFFFFFFFF.toInt(), accent, accent, soft),
                floatArrayOf(0f, 0.4f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            canvas.drawCircle(r, r, r, paint)
        }
    }
}
