package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import io.github.kasecrab.razorback.ui.core.Spring
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp

/** A toggle drawn by hand: track, thumb, spring between the two ends. */
class SwitchView(context: Context) : View(context), Themed {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var onColor = 0
    private var offColor = 0
    private var thumbOn = 0
    private var thumbOff = 0
    private var position = 0f
    private val spring = Spring(stiffness = 700f, dampingRatio = 0.9f) {
        position = it
        invalidate()
    }

    var checked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (context.appTheme.reduceMotion || !isAttachedToWindow) spring.snapTo(if (value) 1f else 0f) else spring.animateTo(if (value) 1f else 0f)
        }

    var onChange: ((Boolean) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            checked = !checked
            io.github.kasecrab.razorback.ui.core.Haptics.toggle(checked)
            onChange?.invoke(checked)
        }
        onThemeChanged(context.appTheme)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(dp(48), dp(28))

    override fun onThemeChanged(theme: Theme) {
        onColor = theme.accent
        offColor = theme.outline
        thumbOn = theme.onAccent
        thumbOff = theme.textSecondary
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        track.color = blend(offColor, onColor, position)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, track)
        val r = h / 2f - dp(4f)
        val cx = h / 2f + (w - h) * position
        thumb.color = blend(thumbOff, thumbOn, position)
        canvas.drawCircle(cx, h / 2f, r, thumb)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a ushr shift) and 0xFF) * (1 - t) + ((b ushr shift) and 0xFF) * t).toInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
