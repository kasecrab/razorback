package io.github.kasecrab.razorback.ui.core

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup

/**
 * Owns the current [Theme]. Views find it through their context (see [ThemedContext])
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

    companion object {
        fun of(context: Context): ThemeHost {
            var c: Context? = context
            while (c != null) {
                if (c is ThemedContext) return c.host
                c = (c as? ContextWrapper)?.baseContext
            }
            throw IllegalStateException("view built with a context that carries no ThemeHost")
        }
    }
}

class ThemedContext(base: Context, val host: ThemeHost) : ContextWrapper(base)

val Context.appTheme: Theme get() = ThemeHost.of(this).theme
