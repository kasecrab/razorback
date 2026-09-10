package io.github.kasecrab.razorback.data

import android.content.ContentValues
import android.database.Cursor
import io.github.kasecrab.razorback.core.forEachObject
import io.github.kasecrab.razorback.core.jsonObject
import io.github.kasecrab.razorback.core.str
import io.github.kasecrab.razorback.core.strings
import io.github.kasecrab.razorback.model.Conversation
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.model.ToolCall
import io.github.kasecrab.razorback.model.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/** Conversations and messages on disk. Suspend functions hop to the right dispatcher themselves. */
class ChatStore(private val db: Db) {

    suspend fun listConversations(query: String = ""): List<Conversation> = withContext(Dispatchers.IO) {
        val out = ArrayList<Conversation>()
        val q = query.trim()
        val c: Cursor = if (q.isEmpty()) {
            db.readableDatabase.rawQuery(
                "SELECT id,title,model,created_at,updated_at,pinned,archived FROM conversations WHERE archived=0 ORDER BY pinned DESC, updated_at DESC",
                null,
            )
        } else {
            val like = "%" + q.replace("%", "\\%").replace("_", "\\_") + "%"
            db.readableDatabase.rawQuery(
                """SELECT DISTINCT c.id,c.title,c.model,c.created_at,c.updated_at,c.pinned,c.archived FROM conversations c
                   LEFT JOIN messages m ON m.conv_id=c.id
                   WHERE c.archived=0 AND (c.title LIKE ? ESCAPE '\' OR m.content LIKE ? ESCAPE '\')
                   ORDER BY c.pinned DESC, c.updated_at DESC""",
                arrayOf(like, like),
            )
        }
        c.use {
            while (it.moveToNext()) {
                out.add(Conversation(it.getString(0), it.getString(1), it.getString(2), it.getLong(3), it.getLong(4), it.getInt(5) != 0, it.getInt(6) != 0))
            }
        }
        out
    }

    suspend fun loadMessages(convId: String): List<Message> = withContext(Dispatchers.IO) {
        val out = ArrayList<Message>()
        db.readableDatabase.rawQuery(
            """SELECT id,role,content,reasoning,reasoning_details,tool_calls,tool_call_id,images,model,status,error,
               prompt_tokens,completion_tokens,reasoning_tokens,cached_tokens,cost,created_at,finished_at,first_token_at,reasoning_ended_at
               FROM messages WHERE conv_id=? ORDER BY seq""",
            arrayOf(convId),
        ).use { c ->
            while (c.moveToNext()) {
                val usage = if (c.isNull(11)) null else Usage(c.getInt(11), c.getInt(12), c.getInt(13), c.getInt(14), c.getDouble(15))
                out.add(
                    Message(
                        id = c.getString(0),
                        role = Role.fromWire(c.getString(1)),
                        content = c.getString(2),
                        reasoning = c.getString(3),
                        reasoningDetails = c.getString(4),
                        toolCalls = parseToolCalls(c.getString(5)),
                        toolCallId = c.getString(6),
                        images = parseStrings(c.getString(7)),
                        model = c.getString(8),
                        status = MessageStatus.entries.firstOrNull { it.name == c.getString(9) } ?: MessageStatus.COMPLETE,
                        error = c.getString(10),
                        usage = usage,
                        createdAt = c.getLong(16),
                        finishedAt = if (c.isNull(17)) null else c.getLong(17),
                        firstTokenAt = if (c.isNull(18)) null else c.getLong(18),
                        reasoningEndedAt = if (c.isNull(19)) null else c.getLong(19),
                    ),
                )
            }
        }
        out
    }

    suspend fun insertConversation(conv: Conversation) = withContext(db.writer) {
        db.writableDatabase.insertOrThrow("conversations", null, values(conv))
    }

    suspend fun updateConversation(conv: Conversation) = withContext(db.writer) {
        db.writableDatabase.update("conversations", values(conv), "id=?", arrayOf(conv.id))
    }

    suspend fun deleteConversation(id: String) = withContext(db.writer) {
        db.writableDatabase.delete("conversations", "id=?", arrayOf(id))
    }

    suspend fun insertMessage(convId: String, seq: Int, m: Message) = withContext(db.writer) {
        val v = values(m)
        v.put("conv_id", convId)
        v.put("seq", seq)
        db.writableDatabase.insertWithOnConflict("messages", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun updateMessage(m: Message) = withContext(db.writer) {
        db.writableDatabase.update("messages", values(m), "id=?", arrayOf(m.id))
    }

    /** Cheap streaming checkpoint: text only. */
    suspend fun updateContent(m: Message) = withContext(db.writer) {
        val v = ContentValues(2)
        v.put("content", m.content)
        v.put("reasoning", m.reasoning)
        db.writableDatabase.update("messages", v, "id=?", arrayOf(m.id))
    }

    suspend fun deleteMessage(id: String) = withContext(db.writer) {
        db.writableDatabase.delete("messages", "id=?", arrayOf(id))
    }

    suspend fun deleteMessagesFrom(convId: String, seq: Int) = withContext(db.writer) {
        db.writableDatabase.delete("messages", "conv_id=? AND seq>=?", arrayOf(convId, seq.toString()))
    }

    suspend fun logUsage(convId: String?, m: Message, latencyMs: Long, ok: Boolean, generationId: String?, toolRound: Int) = withContext(db.writer) {
        val u = m.usage ?: Usage()
        val v = ContentValues(16)
        v.put("ts", m.finishedAt ?: System.currentTimeMillis())
        v.put("conv_id", convId)
        v.put("message_id", m.id)
        v.put("model", m.model ?: "")
        v.put("prompt_tokens", u.promptTokens)
        v.put("completion_tokens", u.completionTokens)
        v.put("reasoning_tokens", u.reasoningTokens)
        v.put("cached_tokens", u.cachedTokens)
        v.put("cost", u.cost)
        v.put("latency_ms", latencyMs)
        v.put("ttft_ms", m.firstTokenAt?.let { it - m.createdAt })
        v.put("ok", if (ok) 1 else 0)
        v.put("error", m.error)
        v.put("generation_id", generationId)
        v.put("tool_round", toolRound)
        db.writableDatabase.insert("usage_log", null, v)
    }

    /** Replies interrupted by a crash or kill are marked as cut so they never look live again. */
    suspend fun repairStreaming() = withContext(db.writer) {
        val v = ContentValues(1)
        v.put("status", MessageStatus.CUT.name)
        db.writableDatabase.update("messages", v, "status=?", arrayOf(MessageStatus.STREAMING.name))
    }

    private fun values(c: Conversation): ContentValues = ContentValues(7).apply {
        put("id", c.id)
        put("title", c.title)
        put("model", c.model)
        put("created_at", c.createdAt)
        put("updated_at", c.updatedAt)
        put("pinned", if (c.pinned) 1 else 0)
        put("archived", if (c.archived) 1 else 0)
    }

    private fun values(m: Message): ContentValues = ContentValues(20).apply {
        put("id", m.id)
        put("role", m.role.wire)
        put("content", m.content)
        put("reasoning", m.reasoning)
        put("reasoning_details", m.reasoningDetails)
        put("tool_calls", if (m.toolCalls.isEmpty()) null else toolCallsJson(m.toolCalls))
        put("tool_call_id", m.toolCallId)
        put("images", if (m.images.isEmpty()) null else JSONArray(m.images).toString())
        put("model", m.model)
        put("status", m.status.name)
        put("error", m.error)
        val u = m.usage
        if (u != null) {
            put("prompt_tokens", u.promptTokens)
            put("completion_tokens", u.completionTokens)
            put("reasoning_tokens", u.reasoningTokens)
            put("cached_tokens", u.cachedTokens)
            put("cost", u.cost)
        }
        put("created_at", m.createdAt)
        put("finished_at", m.finishedAt)
        put("first_token_at", m.firstTokenAt)
        put("reasoning_ended_at", m.reasoningEndedAt)
    }

    private fun toolCallsJson(calls: List<ToolCall>): String {
        val arr = JSONArray()
        for (c in calls) arr.put(jsonObject { put("id", c.id); put("name", c.name); put("arguments", c.arguments) })
        return arr.toString()
    }

    private fun parseToolCalls(json: String?): List<ToolCall> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val out = ArrayList<ToolCall>()
            JSONArray(json).forEachObject { out.add(ToolCall(it.str("id") ?: "", it.str("name") ?: "", it.str("arguments") ?: "{}")) }
            out
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun parseStrings(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            JSONArray(json).strings()
        } catch (_: JSONException) {
            emptyList()
        }
    }
}
