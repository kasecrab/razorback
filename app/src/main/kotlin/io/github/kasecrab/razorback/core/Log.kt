package io.github.kasecrab.razorback.core

import io.github.kasecrab.razorback.BuildConfig

/** Debug logging is skipped in release before the message lambda is ever built. */
object Log {
    @JvmField val ON: Boolean = BuildConfig.DEBUG
    const val TAG = "Razorback"

    inline fun d(msg: () -> String) {
        if (ON) android.util.Log.d(TAG, msg())
    }

    fun w(msg: String, t: Throwable? = null) {
        android.util.Log.w(TAG, msg, t)
    }

    fun e(msg: String, t: Throwable? = null) {
        android.util.Log.e(TAG, msg, t)
    }
}
