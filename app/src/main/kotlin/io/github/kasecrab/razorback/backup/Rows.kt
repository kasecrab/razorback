package io.github.kasecrab.razorback.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/** Generic table rows as JSON objects, so the backup follows the schema without a second mapping. */
object Rows {

    val TABLES = listOf(
        "conversations", "messages", "prompts", "usage_log", "attachments",
        "remote_machines", "remote_sessions", "remote_events",
    )

    fun toJson(c: Cursor): JSONObject {
        val o = JSONObject()
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> o.put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> o.put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> o.put(name, c.getDouble(i))
                else -> o.put(name, c.getString(i))
            }
        }
        return o
    }

    fun toValues(o: JSONObject, columns: Set<String>): ContentValues {
        val v = ContentValues(o.length())
        for (k in o.keys()) {
            if (k !in columns) continue
            when (val x = o.opt(k)) {
                null, JSONObject.NULL -> v.putNull(k)
                is Int -> v.put(k, x)
                is Long -> v.put(k, x)
                is Double -> v.put(k, x)
                is Boolean -> v.put(k, if (x) 1 else 0)
                else -> v.put(k, x.toString())
            }
        }
        return v
    }

    fun columns(db: SQLiteDatabase, table: String): Set<String> {
        val out = HashSet<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(1))
        }
        return out
    }
}
