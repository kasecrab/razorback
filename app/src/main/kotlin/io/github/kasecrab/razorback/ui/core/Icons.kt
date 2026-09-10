package io.github.kasecrab.razorback.ui.core

import android.content.Context
import android.graphics.drawable.Drawable

fun Context.icon(id: Int, tint: Int): Drawable =
    getDrawable(id)!!.mutate().apply { setTint(tint) }
