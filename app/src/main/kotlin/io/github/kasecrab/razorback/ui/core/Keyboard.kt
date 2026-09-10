package io.github.kasecrab.razorback.ui.core

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

object Keyboard {
    fun hide(view: View) {
        view.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
        view.findFocus()?.clearFocus()
    }

    fun show(view: View) {
        view.requestFocus()
        view.context.getSystemService(InputMethodManager::class.java)?.showSoftInput(view, 0)
    }

    fun hideAll(context: Context) {
        val root = context.ui().root
        root.findFocus()?.let { hide(it) } ?: hide(root)
    }
}
