package io.github.kasecrab.razorback.chat

import io.github.kasecrab.razorback.core.HttpException
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.Net
import io.github.kasecrab.razorback.model.Usage
import io.github.kasecrab.razorback.provider.Accumulator
import io.github.kasecrab.razorback.provider.ChatEvent
import io.github.kasecrab.razorback.provider.ChatRequest
import io.github.kasecrab.razorback.provider.Provider
import io.github.kasecrab.razorback.provider.StreamCancelled
import io.github.kasecrab.razorback.provider.StreamHandle

/**
 * One request to the provider with retries. Blocking; run it on an IO thread and
 * cancel through [handle]. Deltas go to [onDelta] on the provider's thread.
 */
class TurnRunner(
    private val provider: Provider,
    private val handle: StreamHandle,
    /** Whether the phone has a network at all; without one there is nothing to retry. */
    private val online: () -> Boolean = { true },
    private val onDelta: (text: String?, reasoning: String?) -> Unit,
) {
    class Outcome(val acc: Accumulator, val error: String?, val cancelled: Boolean)

    companion object {
        const val KEY_REFUSED = "OpenRouter refused the key. Check it under Settings › Providers."
    }

    fun run(request: ChatRequest): Outcome {
        val acc = Accumulator()
        var attempt = 0
        while (true) {
            if (!online()) return Outcome(acc, Net.OFFLINE, cancelled = false)
            var gotAny = false
            var failure: ChatEvent.Failure? = null
            try {
                provider.streamChat(request, handle) { e ->
                    when (e) {
                        is ChatEvent.Text -> {
                            gotAny = true
                            onDelta(e.text, null)
                        }
                        is ChatEvent.Reasoning -> {
                            gotAny = true
                            onDelta(null, e.text)
                        }
                        is ChatEvent.ToolCallDelta, is ChatEvent.Image -> gotAny = true
                        is ChatEvent.Failure -> failure = e
                        else -> {}
                    }
                    acc.apply(e)
                }
            } catch (e: StreamCancelled) {
                return Outcome(acc, null, cancelled = true)
            } catch (e: Exception) {
                if (handle.cancelled) return Outcome(acc, null, cancelled = true)
                if (!online()) return Outcome(acc, Net.OFFLINE, cancelled = false)
                if (e is HttpException && (e.status == 401 || e.status == 403)) return Outcome(acc, KEY_REFUSED, cancelled = false)
                if (!gotAny && attempt < RetryPolicy.MAX_ATTEMPTS - 1 && RetryPolicy.isTransient(e)) {
                    val wait = RetryPolicy.backoffMs(attempt)
                    Log.w("request failed, retrying in ${wait}ms: ${e.message}")
                    if (!sleepUnlessCancelled(wait)) return Outcome(acc, null, cancelled = true)
                    attempt++
                    continue
                }
                return Outcome(acc, e.message ?: e.javaClass.simpleName, cancelled = false)
            }
            val f = failure
            if (f != null && !gotAny && attempt < RetryPolicy.MAX_ATTEMPTS - 1 && f.status >= 500) {
                attempt++
                if (!sleepUnlessCancelled(RetryPolicy.backoffMs(attempt - 1))) return Outcome(acc, null, cancelled = true)
                continue
            }
            return Outcome(acc, f?.message, cancelled = false)
        }
    }

    private fun sleepUnlessCancelled(ms: Long): Boolean {
        var left = ms
        while (left > 0) {
            if (handle.cancelled) return false
            val step = minOf(50L, left)
            Thread.sleep(step)
            left -= step
        }
        return !handle.cancelled
    }
}

fun Usage?.orZero(): Usage = this ?: Usage()
