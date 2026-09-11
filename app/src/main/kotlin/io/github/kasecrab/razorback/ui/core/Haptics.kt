package io.github.kasecrab.razorback.ui.core

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.core.Keys

/**
 * Every touch the app answers with a vibration goes through here, so the feel is one
 * vocabulary and one switch turns it all off. Tappable widgets give a light tick on their
 * own; a screen that has something more to say about a tap, a send, a save, a refusal,
 * says it first, and the widget's tick then stays quiet rather than doubling it.
 */
object Haptics {

    private var lastView: View? = null
    private var lastAt = 0L

    /** The lightest touch: a tap on something, a step, a choice among equals. */
    fun tick(view: View) = play(view, HapticFeedbackConstants.CLOCK_TICK, weak = true)

    /** A tap that does something. */
    fun tap(view: View) = play(view, HapticFeedbackConstants.CONTEXT_CLICK)

    /** Something landed: sent, saved, chosen, kept. */
    fun confirm(view: View) = play(view, HapticFeedbackConstants.CONFIRM)

    /** Something was refused or went wrong. */
    fun reject(view: View) = play(view, HapticFeedbackConstants.REJECT)

    /** A switch or star flipped. */
    fun toggle(view: View, on: Boolean) {
        val constant = if (Build.VERSION.SDK_INT >= 34) {
            if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        } else {
            if (on) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK
        }
        play(view, constant)
    }

    /** Something that cannot be taken back: a delete, a hang-up. */
    fun heavy(view: View) = play(view, HapticFeedbackConstants.LONG_PRESS)

    /** A gesture reached its end: the sidebar snapped, a sheet was flicked away. */
    fun settle(view: View) = play(view, HapticFeedbackConstants.GESTURE_END)

    private fun play(view: View, constant: Int, weak: Boolean = false) {
        if (!App.instance.prefs[Keys.HAPTICS]) return
        val now = SystemClock.uptimeMillis()
        // A widget's own tick right after a screen already answered the same touch is noise.
        if (weak && view === lastView && now - lastAt < ECHO_MS) return
        lastView = view
        lastAt = now
        view.performHapticFeedback(constant)
    }

    private const val ECHO_MS = 120L
}
