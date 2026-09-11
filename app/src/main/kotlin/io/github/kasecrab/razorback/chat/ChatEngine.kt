package io.github.kasecrab.razorback.chat

import android.content.Context
import android.os.Handler
import io.github.kasecrab.razorback.media.ImagePrep
import io.github.kasecrab.razorback.provider.ModelCatalog
import io.github.kasecrab.razorback.tools.ToolRegistry
import android.os.Looper
import io.github.kasecrab.razorback.core.Ids
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.core.Net
import io.github.kasecrab.razorback.core.Prefs
import io.github.kasecrab.razorback.data.ChatStore
import io.github.kasecrab.razorback.model.Conversation
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.model.Reasoning
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.model.ThinkingLevel
import io.github.kasecrab.razorback.provider.ChatRequest
import io.github.kasecrab.razorback.provider.Provider
import io.github.kasecrab.razorback.provider.StreamHandle
import io.github.kasecrab.razorback.voice.VoicePrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The conversation on screen and the turn in flight. All state changes happen on the
 * main thread; streamed text is batched and applied at most once per frame, and written
 * to disk about once a second unless the chat is temporary.
 */
class ChatEngine(
    private val context: Context,
    private val prefs: Prefs,
    private val provider: Provider,
    private val store: ChatStore,
    private val catalog: ModelCatalog,
    private val tools: ToolRegistry,
    private val namer: Namer,
) {

    interface Listener {
        fun onReset()
        fun onMessageAdded(index: Int)
        fun onMessageChanged(index: Int, streaming: Boolean)
        fun onMessageRemoved(index: Int)
        fun onStreamingChanged(streaming: Boolean)
        fun onConversationChanged() {}
        fun onConversationsChanged() {}
    }

    val messages = ArrayList<Message>()
    val isStreaming: Boolean get() = handle != null

    var conversation: Conversation? = null
        private set

    /** Temporary chats live only in memory and vanish when a new chat starts. */
    var temporary: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            for (l in listeners) l.onConversationChanged()
        }

    /**
     * Switching models carries the thinking level with it: the level last used with the
     * new model comes back, or the current one is fitted to what the model accepts, so
     * the chip and the sheet always show a choice the model can actually take.
     */
    var model: String
        get() = prefs[Keys.MODEL]
        set(value) {
            val old = prefs[Keys.MODEL]
            if (old == value) return
            prefs.putRawString(THINKING_FOR + old, thinking.name)
            prefs[Keys.MODEL] = value
            val remembered = prefs.rawString(THINKING_FOR + value)?.let { ThinkingLevel.fromName(it) }
            thinking = remembered ?: thinking
            fitThinking()
        }

    var thinking: ThinkingLevel
        get() = prefs[Keys.THINKING]
        set(value) = prefs.set(Keys.THINKING, value)

    /** Snap the level to one the current model supports; a no-op until the catalogue knows the model. */
    fun fitThinking() {
        val info = catalog.find(model) ?: return
        val fitted = Reasoning.effective(thinking, info) ?: ThinkingLevel.OFF
        if (fitted != thinking) thinking = fitted
    }

    private val listeners = ArrayList<Listener>(2)
    private val main = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Disk writes run on the store's single writer thread in the order they were launched,
     * so a message can never reach the database before its conversation. A failed write is
     * logged, never fatal.
     */
    private val disk = CoroutineScope(SupervisorJob() + store.writer)

    private fun persist(what: String, block: suspend () -> Unit) {
        disk.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e("could not save $what", e)
            }
        }
    }
    private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var handle: StreamHandle? = null
    private var job: Job? = null
    private var loadJob: Job? = null

    private val lock = Any()
    private val pendingText = StringBuilder()
    private val pendingReasoning = StringBuilder()
    private var pendingFirstToken = 0L
    private var flushScheduled = false
    private var lastPersist = 0L
    private var persistedLength = 0

    private val persist: Boolean get() = !temporary

    fun addListener(l: Listener) {
        listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    fun newConversation() {
        stop()
        loadJob?.cancel()
        conversation = null
        messages.clear()
        for (l in listeners) {
            l.onReset()
            l.onConversationChanged()
        }
    }

    fun open(conv: Conversation) {
        if (conv.id == conversation?.id) return
        stop()
        loadJob?.cancel()
        temporary = false
        conversation = conv
        messages.clear()
        for (l in listeners) {
            l.onReset()
            l.onConversationChanged()
        }
        loadJob = ui.launch {
            val loaded = store.loadMessages(conv.id)
            if (conversation?.id != conv.id) return@launch
            messages.clear()
            messages.addAll(loaded)
            for (l in listeners) l.onReset()
        }
    }

    /**
     * [spoken] turns are for voice mode: the reply is routed for latency and the model is
     * told it is talking out loud, so it answers briefly in plain prose.
     */
    fun send(text: String, images: List<String> = emptyList(), thinking: ThinkingLevel = this.thinking, model: String = this.model, spoken: Boolean = false) {
        if (isStreaming) return
        val user = Message(Ids.next(), Role.USER, content = text, images = images)
        var conv = conversation
        if (conv == null) {
            val now = System.currentTimeMillis()
            conv = Conversation(Ids.next(), UNTITLED, model, now, now)
            conversation = conv
            if (persist) persist("conversation") { store.insertConversation(conv) }
            for (l in listeners) l.onConversationChanged()
            // A temporary chat is never named: nothing about it leaves the phone beyond the reply itself.
            if (persist) nameInBackground(conv, text)
        }
        messages.add(user)
        val index = messages.size - 1
        if (persist) persist("message") { store.insertMessage(conv.id, index, user) }
        for (l in listeners) l.onMessageAdded(index)
        startReply(thinking = thinking, model = model, spoken = spoken)
    }

    /** Drop the last reply and ask again. */
    fun regenerate() {
        if (isStreaming) return
        val last = messages.lastOrNull() ?: return
        if (last.role == Role.ASSISTANT) delete(messages.size - 1)
        if (messages.lastOrNull()?.role == Role.USER) startReply()
    }

    fun stop() {
        handle?.cancel()
    }

    fun delete(index: Int) {
        if (isStreaming || index !in messages.indices) return
        val m = messages.removeAt(index)
        if (persist) persist("delete") { store.deleteMessage(m.id) }
        for (l in listeners) l.onMessageRemoved(index)
    }

    /** Remove [index] and everything after it; the caller usually puts the text back in the composer. */
    fun truncateFrom(index: Int) {
        if (isStreaming || index !in messages.indices) return
        val conv = conversation
        if (persist && conv != null) persist("truncate") { store.deleteMessagesFrom(conv.id, index) }
        while (messages.size > index) {
            val last = messages.size - 1
            messages.removeAt(last)
            for (l in listeners) l.onMessageRemoved(last)
        }
    }

    fun rename(conv: Conversation, title: String) {
        conv.title = title
        persist("rename") { store.updateConversation(conv) }
        for (l in listeners) {
            l.onConversationsChanged()
            if (conv.id == conversation?.id) l.onConversationChanged()
        }
    }

    fun setPinned(conv: Conversation, pinned: Boolean) {
        conv.pinned = pinned
        persist("pin") { store.updateConversation(conv) }
        for (l in listeners) l.onConversationsChanged()
    }

    fun deleteConversation(conv: Conversation) {
        if (conv.id == conversation?.id) newConversation()
        persist("delete conversation") { store.deleteConversation(conv.id) }
        for (l in listeners) l.onConversationsChanged()
    }

    /** A small model names the chat from what was first said; the first words stand in only if it cannot. */
    private fun nameInBackground(conv: Conversation, text: String) {
        io.launch {
            val title = namer.name(text) ?: titleFrom(text)
            main.post { if (conv.title == UNTITLED) rename(conv, title) }
        }
    }

    private fun titleFrom(text: String): String {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "New chat"
        return if (line.length > 60) line.take(57).trimEnd() + "…" else line
    }

    private fun startReply(round: Int = 0, thinking: ThinkingLevel = this.thinking, model: String = this.model, spoken: Boolean = false) {
        val conv = conversation ?: return
        val reply = Message(Ids.next(), Role.ASSISTANT, model = model, status = MessageStatus.STREAMING)
        messages.add(reply)
        val index = messages.size - 1
        if (persist) persist("reply row") { store.insertMessage(conv.id, index, reply) }
        for (l in listeners) l.onMessageAdded(index)
        val h = StreamHandle()
        handle = h
        lastPersist = System.currentTimeMillis()
        persistedLength = 0
        for (l in listeners) l.onStreamingChanged(true)
        val info = catalog.find(model)
        val history = messages.subList(0, index).filter { it.status != MessageStatus.ERROR || it.role == Role.TOOL }
        val offered = if (round < MAX_TOOL_ROUNDS && (info == null || info.supportsTools)) tools.enabled() else emptyList()
        val started = System.currentTimeMillis()
        job = io.launch {
            val request = ChatRequest(
                model = model,
                messages = hydrate(history),
                systemPrompt = if (spoken) VoicePrompt.compose(prefs[Keys.SYSTEM_PROMPT], prefs[Keys.VOICE_PROMPT]) else prefs[Keys.SYSTEM_PROMPT],
                maxTokens = prefs[Keys.MAX_TOKENS],
                thinking = thinking,
                modelInfo = info,
                tools = offered.map { it.spec },
                imageOutput = info?.producesImages == true,
                preferLatency = spoken,
            )
            val runner = TurnRunner(provider, h, online = { Net.online(context) }) { text, reasoning ->
                synchronized(lock) {
                    if (pendingFirstToken == 0L) pendingFirstToken = System.currentTimeMillis()
                    if (text != null) pendingText.append(text)
                    if (reasoning != null) pendingReasoning.append(reasoning)
                }
                scheduleFlush()
            }
            val outcome = runner.run(request)
            val saved = outcome.acc.images.mapNotNull { url ->
                try {
                    ImagePrep.importDataUrl(context, url).path
                } catch (e: Exception) {
                    Log.w("could not keep generated image", e)
                    null
                }
            }
            main.post { finish(conv, reply, outcome, started, saved, round, thinking, model, spoken) }
        }
    }

    /**
     * Pictures are kept as files; the request wants data URLs. Only the newest few are
     * sent so a long conversation does not carry megabytes of base64 every turn.
     */
    private fun hydrate(history: List<Message>): List<Message> {
        var budget = MAX_IMAGES_SENT
        val out = ArrayList<Message>(history.size)
        for (i in history.indices.reversed()) {
            val m = history[i]
            if (m.images.isEmpty()) {
                out.add(m)
                continue
            }
            val urls = ArrayList<String>(m.images.size)
            for (img in m.images) {
                if (budget <= 0) break
                val url = if (img.startsWith("data:")) img else try {
                    ImagePrep.dataUrl(img)
                } catch (e: Exception) {
                    continue
                }
                urls.add(url)
                budget--
            }
            out.add(Message(m.id, m.role, m.content, m.reasoning, m.reasoningDetails, m.toolCalls, m.toolCallId, urls, m.model, m.status))
        }
        out.reverse()
        return out
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
        if (text != null) {
            if (m.content.isEmpty() && m.reasoning != null && m.reasoningEndedAt == null) m.reasoningEndedAt = System.currentTimeMillis()
            m.content += text
        }
        if (reasoning != null) m.reasoning = (m.reasoning ?: "") + reasoning
        for (l in listeners) l.onMessageChanged(index, streaming = true)
        val now = System.currentTimeMillis()
        val grown = m.content.length + (m.reasoning?.length ?: 0) - persistedLength
        if (persist && (now - lastPersist > PERSIST_MS || grown > PERSIST_CHARS)) {
            lastPersist = now
            persistedLength += grown
            val snapshot = Message(m.id, m.role, m.content, m.reasoning)
            persist("partial reply") { store.updateContent(snapshot) }
        }
    }

    private fun finish(
        conv: Conversation,
        reply: Message,
        outcome: TurnRunner.Outcome,
        started: Long,
        images: List<String>,
        round: Int,
        thinking: ThinkingLevel,
        model: String,
        spoken: Boolean,
    ) {
        main.removeCallbacks(flush)
        flush.run()
        synchronized(lock) { pendingFirstToken = 0L }
        val acc = outcome.acc
        reply.content = acc.text.toString()
        reply.reasoning = acc.reasoning.toString().ifEmpty { null }
        reply.reasoningDetails = acc.reasoningDetailsJson()
        reply.images = images
        reply.toolCalls = if (outcome.cancelled || outcome.error != null) emptyList() else acc.toolCalls()
        reply.usage = acc.usage
        reply.finishedAt = System.currentTimeMillis()
        if (reply.reasoning != null && reply.reasoningEndedAt == null) reply.reasoningEndedAt = reply.finishedAt
        reply.status = when {
            outcome.cancelled -> MessageStatus.CUT
            outcome.error != null && reply.content.isEmpty() && reply.images.isEmpty() -> MessageStatus.ERROR
            outcome.error != null -> MessageStatus.CUT
            acc.finishReason == "length" -> MessageStatus.CUT
            else -> MessageStatus.COMPLETE
        }
        reply.error = outcome.error
        val index = messages.indexOf(reply)
        if (index >= 0) for (l in listeners) l.onMessageChanged(index, streaming = false)
        val calls = reply.toolCalls
        val truncatedCall = acc.finishReason == "length" && acc.hasToolCalls
        if (reply.status == MessageStatus.COMPLETE && calls.isNotEmpty() && !truncatedCall) {
            // Tool round: run every call, append results, ask again. The stream handle stays
            // non-null so the UI keeps showing stop until the final answer lands.
            if (persist) persist("tool call") { store.updateMessage(reply) }
            job = io.launch {
                val results = calls.map { c -> tools.run(c.name, c.arguments) }
                main.post {
                    for ((c, r) in calls.zip(results)) {
                        val tm = Message(Ids.next(), Role.TOOL, content = r.output, toolCallId = c.id, status = if (r.isError) MessageStatus.ERROR else MessageStatus.COMPLETE)
                        messages.add(tm)
                        val ti = messages.size - 1
                        if (persist) persist("tool result") { store.insertMessage(conv.id, ti, tm) }
                        for (l in listeners) l.onMessageAdded(ti)
                    }
                    handle = null
                    startReply(round + 1, thinking, model, spoken)
                }
            }
            return
        }
        handle = null
        job = null
        for (l in listeners) l.onStreamingChanged(false)
        if (persist) {
            conv.updatedAt = reply.finishedAt!!
            conv.model = reply.model
            val latency = reply.finishedAt!! - started
            val ok = reply.status != MessageStatus.ERROR
            persist("reply") {
                store.updateMessage(reply)
                store.updateConversation(conv)
                if (reply.usage != null || !ok) store.logUsage(conv.id, reply, latency, ok, acc.generationId, round)
            }
            for (l in listeners) l.onConversationsChanged()
        }
    }

    companion object {
        /** The name a chat carries until its own arrives. */
        const val UNTITLED = "New chat"
        private const val THINKING_FOR = "model.thinking."
        const val FLUSH_MS = 33L
        const val PERSIST_MS = 1000L
        const val PERSIST_CHARS = 2048
        const val MAX_IMAGES_SENT = 4
        const val MAX_TOOL_ROUNDS = 6
    }
}
