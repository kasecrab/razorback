package io.github.kasecrab.razorback.ui.core

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope

/**
 * Owns the current [Theme]. Views find it through their context (see [UiContext])
 * and are walked in place when it changes; nothing is recreated.
 */
class ThemeHost(initial: Theme) {

    var theme: Theme = initial
        private set

    private val listeners = ArrayList<(Theme) -> Unit>(2)

    fun onChange(listener: (Theme) -> Unit) {
        listeners.add(listener)
    }

    fun set(root: View, next: Theme) {
        theme = next
        apply(root, next)
        for (l in listeners) l(next)
    }

    fun apply(view: View, theme: Theme = this.theme) {
        if (view is Themed) view.onThemeChanged(theme)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) apply(view.getChildAt(i), theme)
        }
    }
}

/** The context every view is built with: theme, navigation, back gesture, UI coroutine scope. */
class UiContext(
    base: Context,
    val host: ThemeHost,
    val back: BackDispatcher,
    val scope: CoroutineScope,
) : ContextWrapper(base) {
    lateinit var nav: ScreenStack
    lateinit var root: FrameLayout
    var insetTop = 0
    var insetBottom = 0
}

fun Context.ui(): UiContext {
    var c: Context? = this
    while (c != null) {
        if (c is UiContext) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    throw IllegalStateException("view built with a context that is not a UiContext")
}

val Context.appTheme: Theme get() = ui().host.theme
val Context.nav: ScreenStack get() = ui().nav
val Context.uiScope: CoroutineScope get() = ui().scope
