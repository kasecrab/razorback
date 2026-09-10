package io.github.kasecrab.razorback.provider

import java.io.IOException
import java.net.HttpURLConnection

class StreamCancelled : IOException("cancelled")

/** Lets another thread abort an in-flight stream; aborting also stops billing upstream. */
class StreamHandle {
    @Volatile var connection: HttpURLConnection? = null
    @Volatile var cancelled: Boolean = false
        private set

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }
}
