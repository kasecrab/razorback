package io.github.kasecrab.razorback.ui.core

import android.content.Intent

/** Launch an intent for a result without the androidx contracts; the activity forwards results here. */
class ActivityResults(private val launch: (Intent, Int) -> Unit) {

    private val pending = HashMap<Int, (Int, Intent?) -> Unit>(2)
    private var next = 100

    fun start(intent: Intent, callback: (resultCode: Int, data: Intent?) -> Unit) {
        val code = next++
        pending[code] = callback
        try {
            launch(intent, code)
        } catch (e: Exception) {
            pending.remove(code)
            callback(android.app.Activity.RESULT_CANCELED, null)
        }
    }

    fun deliver(code: Int, resultCode: Int, data: Intent?): Boolean {
        val cb = pending.remove(code) ?: return false
        cb(resultCode, data)
        return true
    }
}
