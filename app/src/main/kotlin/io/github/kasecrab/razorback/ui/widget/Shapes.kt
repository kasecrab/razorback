package io.github.kasecrab.razorback.ui.widget

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape

object Shapes {
    fun rounded(color: Int, radiusPx: Float, strokePx: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(color)
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun pill(color: Int, strokePx: Int = 0, strokeColor: Int = 0): GradientDrawable =
        rounded(color, 999f, strokePx, strokeColor)

    fun circleRipple(rippleColor: Int): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), null, ShapeDrawable(OvalShape()))

    fun ripple(rippleColor: Int, content: GradientDrawable?, radiusPx: Float): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, rounded(0xFF000000.toInt(), radiusPx))

    fun solid(color: Int) = ColorDrawable(color)
}
