package io.github.kasecrab.razorback.chat

import android.os.Handler
import android.os.Looper
import io.github.kasecrab.razorback.core.Ids
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Prefs
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.model.ThinkingLevel
import io.github.kasecrab.razorback.provider.ChatRequest
import io.github.kasecrab.razorback.provider.Provider
import io.github.kasecrab.razorback.provider.StreamHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The conversation on screen and the turn in flight. All state changes happen on the
 * main thread; streamed text is batched and applied at most once per frame.
 */
class ChatEngine(private val prefs: Prefs, private val provider: Provider) {

    interface Listener {
        fun onReset()
        fun onMessageAdded(index: Int)
        fun onMessageChanged(index: Int, streaming: Boolean)
        fun onMessageRemoved(index: Int)
        fun onStreamingChanged(streaming: Boolean)
    }

    val messages = ArrayList<Message>()
    val isStreaming: Boolean get() = handle != null

    var model: String
        get() = prefs[Keys.MODEL]
        set(value) = prefs.set(Keys.MODEL, value)

    var thinking: ThinkingLevel
        get() = prefs[Keys.THINKING]
        set(value) = prefs.set(Keys.THINKING, value)

    private val listeners = ArrayList<Listener>(2)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var handle: StreamHandle? = null
    private var job: Job? = null

    private val lock = Any()
    private val pendingText = StringBuilder()
    private val pendingReasoning = StringBuilder()
    private var pendingFirstToken = 0L
    private var flushScheduled = false

    fun addListener(l: Listener) {
        listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    fun newConversation() {
        stop()
        messages.clear()
        for (l in listeners) l.onReset()
    }

    fun send(text: String, images: List<String> = emptyList()) {
        if (isStreaming) return
        val user = Message(Ids.next(), Role.USER, content = text, images = images)
        messages.add(user)
        for (l in listeners) l.onMessageAdded(messages.size - 1)
        startReply()
    }

    /** Drop the last reply and ask again. */
    fun regenerate() {
        if (isStreaming) return
        val last = messages.lastOrNull() ?: return
        if (last.role == Role.ASSISTANT) {
            messages.removeAt(messages.size - 1)
            for (l in listeners) l.onMessageRemoved(messages.size)
        }
        if (messages.lastOrNull()?.role == Role.USER) startReply()
    }

    fun stop() {
        handle?.cancel()
    }

    private fun startReply() {
        val reply = Message(Ids.next(), Role.ASSISTANT, model = model, status = MessageStatus.STREAMING)
        messages.add(reply)
        val index = messages.size - 1
        for (l in listeners) l.onMessageAdded(index)
        val h = StreamHandle()
        handle = h
        for (l in listeners) l.onStreamingChanged(true)
        val request = ChatRequest(
            model = model,
            messages = messages.subList(0, index).filter { it.status != MessageStatus.ERROR },
            systemPrompt = prefs[Keys.SYSTEM_PROMPT],
            maxTokens = prefs[Keys.MAX_TOKENS],
            thinking = thinking,
        )
        job = scope.launch {
            val runner = TurnRunner(provider, h) { text, reasoning ->
                synchronized(lock) {
                    if (pendingFirstToken == 0L) pendingFirstToken = System.currentTimeMillis()
                    if (text != null) pendingText.append(text)
                    if (reasoning != null) pendingReasoning.append(reasoning)
                }
                scheduleFlush()
            }
            val outcome = runner.run(request)
            main.post { finish(reply, outcome) }
        }
    }

    private fun scheduleFlush() {
        synchronized(lock) {
            if (flushScheduled) return
            flushScheduled = true
        }
        main.postDelayed(flush, FLUSH_MS)
    }

    private val flush = Runnable {
        var text: String? = null
        var reasoning: String? = null
        var first = 0L
        synchronized(lock) {
            flushScheduled = false
            if (pendingText.isNotEmpty()) {
                text = pendingText.toString()
                pendingText.setLength(0)
            }
            if (pendingReasoning.isNotEmpty()) {
                reasoning = pendingReasoning.toString()
                pendingReasoning.setLength(0)
            }
            first = pendingFirstToken
        }
        val index = messages.indexOfLast { it.status == MessageStatus.STREAMING }
        if (index < 0) return@Runnable
        val m = messages[index]
        if (m.firstTokenAt == null && first != 0L) m.firstTokenAt = first
        if (text != null) m.content += text
        if (reasoning != null) m.reasoning = (m.reasoning ?: "") + reasoning
        for (l in listeners) l.onMessageChanged(index, streaming = true)
    }

    private fun finish(reply: Message, outcome: TurnRunner.Outcome) {
        main.removeCallbacks(flush)
        flush.run()
        synchronized(lock) { pendingFirstToken = 0L }
        val acc = outcome.acc
        reply.content = acc.text.toString()
        reply.reasoning = acc.reasoning.toString().ifEmpty { null }
        reply.reasoningDetails = acc.reasoningDetailsJson()
        reply.images = acc.images.toList()
        reply.toolCalls = if (outcome.cancelled || outcome.error != null) emptyList() else acc.toolCalls()
        reply.usage = acc.usage
        reply.finishedAt = System.currentTimeMillis()
        reply.status = when {
            outcome.cancelled -> MessageStatus.CUT
            outcome.error != null && reply.content.isEmpty() && reply.images.isEmpty() -> MessageStatus.ERROR
            outcome.error != null -> MessageStatus.CUT
            acc.finishReason == "length" -> MessageStatus.CUT
            else -> MessageStatus.COMPLETE
        }
        reply.error = outcome.error
        handle = null
        job = null
        val index = messages.indexOf(reply)
        if (index >= 0) for (l in listeners) l.onMessageChanged(index, streaming = false)
        for (l in listeners) l.onStreamingChanged(false)
    }

    private companion object {
        const val FLUSH_MS = 33L
    }
}
