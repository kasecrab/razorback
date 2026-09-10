package io.github.kasecrab.razorback.ui.core

import android.content.Context
import android.widget.FrameLayout

/**
 * A full-screen page in the [ScreenStack]. Built in code, themed in place, told about
 * window insets so it can lay its own chrome under the system bars.
 */
abstract class Screen(context: Context) : FrameLayout(context), Themed {

    init {
        isClickable = true
        isFocusable = true
    }

    /** Pushed and shown. */
    open fun onEnter() {}

    /** Popped for good. */
    open fun onExit() {}

    /** Uncovered after the screen above was popped. */
    open fun onResume() {}

    /** Covered by another screen. */
    open fun onPause() {}

    /** Return true to consume a back press instead of popping. */
    open fun onBack(): Boolean = false

    open fun onInsetsChanged(top: Int, bottom: Int, left: Int, right: Int) {
        setPadding(left, top, right, bottom)
    }

    override fun onThemeChanged(theme: Theme) {
        setBackgroundColor(theme.bg)
    }
}
