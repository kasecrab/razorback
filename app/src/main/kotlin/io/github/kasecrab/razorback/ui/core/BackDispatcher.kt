package io.github.kasecrab.razorback.ui.core

import android.app.Activity
import android.os.Build
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Something that can own the back gesture: a sheet, the drawer, a screen, the stack. */
interface BackHandler {
    val backEnabled: Boolean
    fun onBackStarted() {}
    fun onBackProgressed(progress: Float, swipeEdge: Int) {}
    fun onBackInvoked()
    fun onBackCancelled() {}
}

/**
 * One system callback, many handlers. Registered with the platform only while some
 * handler is enabled, so the system exits the app itself when nothing wants back.
 */
class BackDispatcher(private val activity: Activity) {

    private val handlers = ArrayList<Pair<Int, BackHandler>>(4)
    private var active: BackHandler? = null
    private var registered = false

    private val callback: OnBackInvokedCallback =
        if (Build.VERSION.SDK_INT >= 34) animationCallback() else OnBackInvokedCallback { invoke() }

    fun add(handler: BackHandler, priority: Int) {
        handlers.add(priority to handler)
        handlers.sortByDescending { it.first }
        invalidate()
    }

    fun remove(handler: BackHandler) {
        handlers.removeAll { it.second === handler }
        invalidate()
    }

    /** Call after anything that may change a handler's [BackHandler.backEnabled]. */
    fun invalidate() {
        val wanted = handlers.any { it.second.backEnabled }
        if (wanted == registered) return
        val dispatcher = activity.onBackInvokedDispatcher
        if (wanted) {
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        } else {
            dispatcher.unregisterOnBackInvokedCallback(callback)
        }
        registered = wanted
    }

    private fun pick(): BackHandler? = handlers.firstOrNull { it.second.backEnabled }?.second

    private fun invoke() {
        val h = active ?: pick()
        active = null
        h?.onBackInvoked()
        invalidate()
    }

    private fun animationCallback(): OnBackInvokedCallback = object : OnBackAnimationCallback {
        override fun onBackStarted(backEvent: BackEvent) {
            val h = pick()
            active = h
            h?.onBackStarted()
        }

        override fun onBackProgressed(backEvent: BackEvent) {
            active?.onBackProgressed(backEvent.progress, backEvent.swipeEdge)
        }

        override fun onBackInvoked() {
            invoke()
        }

        override fun onBackCancelled() {
            val h = active
            active = null
            h?.onBackCancelled()
        }
    }
}
