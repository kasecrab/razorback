package io.github.kasecrab.razorback.ui.core

import android.content.Context
import android.graphics.Typeface
import android.view.View

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(value: Int): Int = context.dp(value)
fun View.dp(value: Float): Float = context.dp(value)

object Fonts {
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val mono: Typeface = Typeface.MONOSPACE
}

/** Spacing scale in dp. */
object Space {
    const val XS = 4
    const val S = 8
    const val M = 12
    const val L = 16
    const val XL = 24
    const val XXL = 32
}

/** Type scale in sp, before the user's font scale. */
object Type {
    const val DISPLAY = 28f
    const val TITLE = 20f
    const val BODY = 16f
    const val SECONDARY = 14f
    const val CAPTION = 12f
}
