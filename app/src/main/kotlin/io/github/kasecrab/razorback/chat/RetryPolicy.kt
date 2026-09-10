package io.github.kasecrab.razorback.chat

import io.github.kasecrab.razorback.core.HttpException
import io.github.kasecrab.razorback.provider.StreamCancelled
import java.io.IOException

/** Retry only what might succeed a second time, and only before any tokens were paid for. */
object RetryPolicy {
    const val MAX_ATTEMPTS = 3

    fun isTransient(t: Throwable): Boolean = when (t) {
        is StreamCancelled -> false
        is HttpException -> t.status == 408 || t.status == 425 || t.status == 429 || t.status >= 500
        is IOException -> true
        else -> false
    }

    /** 1 s, 2 s, 4 s. */
    fun backoffMs(attempt: Int): Long = 500L shl (attempt + 1)
}
