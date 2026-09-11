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

    /** Bumped on every switch, so views that were off screen at the time can catch up. */
    var version: Int = 1
        private set

    private val listeners = ArrayList<(Theme) -> Unit>(2)

    fun onChange(listener: (Theme) -> Unit) {
        listeners.add(listener)
    }

    fun set(root: View, next: Theme) {
        theme = next
        version++
        apply(root, next)
        for (l in listeners) l(next)
    }

    /**
     * Recycled list rows live outside the tree while a theme changes. Call this when a row
     * is bound: it re-themes the row once per switch and is free otherwise.
     */
    fun refresh(view: View) {
        val seen = view.getTag(io.github.kasecrab.razorback.R.id.theme_version) as? Int
        if (seen == version) return
        apply(view, theme)
        view.setTag(io.github.kasecrab.razorback.R.id.theme_version, version)
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
    lateinit var results: ActivityResults
    lateinit var permissions: PermissionRequests
    var insetTop = 0
    var insetBottom = 0
    /** How far the keyboard reaches up from the bottom edge right now; zero while it is away. */
    var imeBottom = 0
    private val insetWatchers = ArrayList<() -> Unit>(2)

    /** Views outside the screen stack, such as sheets, follow the bars and the keyboard through this. */
    fun watchInsets(watcher: () -> Unit) {
        insetWatchers.add(watcher)
    }

    fun unwatchInsets(watcher: () -> Unit) {
        insetWatchers.remove(watcher)
    }

    fun insetsChanged() {
        for (w in insetWatchers.toList()) w()
    }
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
