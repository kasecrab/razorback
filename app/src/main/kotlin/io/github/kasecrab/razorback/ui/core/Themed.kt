package io.github.kasecrab.razorback.ui.core

/** A view that repaints itself when the theme changes, without being recreated. */
interface Themed {
    fun onThemeChanged(theme: Theme)
}
