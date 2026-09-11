package io.github.kasecrab.razorback.data

import android.content.ContentValues
import io.github.kasecrab.razorback.remote.Frames
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * What a paired machine has said, kept so it is still there next time.
 *
 * Scrollback comes from the relay while it has it, but the relay trims itself
 * and re-seals under a new key whenever the machine restarts. What is written
 * here is what survives both.
 */
class RemoteStore(private val db: Db) {

    data class Machine(val hub: String, val name: String, val url: String, val cursor: Long)

    data class Session(
        val hub: String,
        val id: String,
        val title: String,
        val cwd: String,
        val model: String,
        val startedMs: Long,
        val live: Boolean,
    )

    suspend fun machines(): List<Machine> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val out = ArrayList<Machine>()
        db.readableDatabase.rawQuery(
            "SELECT hub, name, url, cursor FROM remote_machines ORDER BY seen_at DESC", null,
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Machine(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)))
            }
        }
        out
    }

    suspend fun rememberMachine(hub: String, name: String, url: String) =
        withContext(db.writer) {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("hub", hub)
                put("name", name)
                put("url", url)
                put("paired_at", now)
                put("seen_at", now)
            }
            db.writableDatabase.insertWithOnConflict(
                "remote_machines", null, values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
            )
            db.writableDatabase.execSQL(
                "UPDATE remote_machines SET name = ?, url = ?, seen_at = ? WHERE hub = ?",
                arrayOf<Any>(name, url, now, hub),
            )
        }

    /** Where this phone got to, so a reconnect asks for the right thing. */
    suspend fun rememberCursor(hub: String, cursor: Long) = withContext(db.writer) {
        db.writableDatabase.execSQL(
            "UPDATE remote_machines SET cursor = ?, seen_at = ? WHERE hub = ?",
            arrayOf<Any>(cursor, System.currentTimeMillis(), hub),
        )
    }

    suspend fun sessions(hub: String): List<Session> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val out = ArrayList<Session>()
            db.readableDatabase.rawQuery(
                "SELECT hub, id, title, cwd, model, started_ms, live FROM remote_sessions " +
                    "WHERE hub = ? ORDER BY live DESC, seen_at DESC",
                arrayOf(hub),
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        Session(
                            c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                            c.getString(4), c.getLong(5), c.getInt(6) != 0,
                        ),
                    )
                }
            }
            out
        }

    /** Replace what is known about a machine's sessions with what it just said. */
    suspend fun rememberSessions(hub: String, list: List<Frames.Session>) =
        withContext(db.writer) {
            db.tx { database ->
                database.delete("remote_sessions", "hub = ?", arrayOf(hub))
                val now = System.currentTimeMillis()
                for (s in list) {
                    database.insertWithOnConflict(
                        "remote_sessions", null,
                        ContentValues().apply {
                            put("hub", hub)
                            put("id", s.id)
                            put("title", s.name ?: s.title)
                            put("cwd", s.cwd)
                            put("model", s.model)
                            put("started_ms", s.startedMs)
                            put("seen_at", now)
                            put("live", if (s.live) 1 else 0)
                        },
                        android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
        }

    /** Everything a session has said, in the order it said it. */
    suspend fun events(session: String): List<JSONObject> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val out = ArrayList<JSONObject>()
            db.readableDatabase.rawQuery(
                "SELECT body FROM remote_events WHERE session_id = ? ORDER BY n, idx",
                arrayOf(session),
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(runCatching { JSONObject(c.getString(0)) }.getOrNull() ?: continue)
                }
            }
            out
        }

    /**
     * Keep what a session said, under the relay's own numbering and the
     * position inside that frame. The relay replays what it kept whenever
     * this phone reconnects, so the same frame arriving twice is ordinary,
     * and keying it this way means it is stored once.
     */
    suspend fun rememberEvents(session: String, n: Long, events: List<JSONObject>) =
        withContext(db.writer) {
            db.tx { database ->
                val now = System.currentTimeMillis()
                for ((idx, e) in events.withIndex()) {
                    database.insertWithOnConflict(
                        "remote_events", null,
                        ContentValues().apply {
                            put("session_id", session)
                            put("n", n)
                            put("idx", idx)
                            put("kind", e.optString("type"))
                            put("body", e.toString())
                            put("at", now)
                        },
                        android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
                    )
                }
            }
        }

    suspend fun forget(hub: String) = withContext(db.writer) {
        db.tx { database ->
            database.delete("remote_events", "session_id IN (SELECT id FROM remote_sessions WHERE hub = ?)", arrayOf(hub))
            database.delete("remote_sessions", "hub = ?", arrayOf(hub))
            database.delete("remote_machines", "hub = ?", arrayOf(hub))
        }
    }
}
