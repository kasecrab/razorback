package io.github.kasecrab.razorback.ui.core

import android.app.Activity
import android.content.pm.PackageManager

/** Runtime permission prompts without the androidx contracts. */
class PermissionRequests(private val activity: Activity) {

    private val pending = HashMap<Int, (Boolean) -> Unit>(2)
    private var next = 500

    fun request(permission: String, callback: (Boolean) -> Unit) {
        if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            callback(true)
            return
        }
        val code = next++
        pending[code] = callback
        activity.requestPermissions(arrayOf(permission), code)
    }

    fun deliver(code: Int, results: IntArray): Boolean {
        val cb = pending.remove(code) ?: return false
        cb(results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED)
        return true
    }
}
