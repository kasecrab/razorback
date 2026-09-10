package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme

/** Circle with the first letter of a name; no bitmaps. */
class Avatar(context: Context) : View(context), Themed {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.medium
        textAlign = Paint.Align.CENTER
    }

    var letter: String = "Y"
        set(value) {
            field = value.take(1).uppercase()
            invalidate()
        }

    init {
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        fill.color = theme.accent
        text.color = theme.onAccent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        canvas.drawCircle(r, r, r, fill)
        text.textSize = r * 1.05f
        val y = r - (text.descent() + text.ascent()) / 2f
        canvas.drawText(letter, r, y, text)
    }
}
