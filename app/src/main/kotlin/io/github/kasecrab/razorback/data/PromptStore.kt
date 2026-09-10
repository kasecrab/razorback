package io.github.kasecrab.razorback.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import io.github.kasecrab.razorback.model.Prompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PromptStore(private val db: Db) {

    suspend fun list(): List<Prompt> = withContext(Dispatchers.IO) {
        val out = ArrayList<Prompt>()
        db.readableDatabase.rawQuery("SELECT id,name,text,created_at,updated_at FROM prompts ORDER BY updated_at DESC", null).use { c ->
            while (c.moveToNext()) out.add(Prompt(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4)))
        }
        out
    }

    suspend fun save(p: Prompt) = withContext(db.writer) {
        val v = ContentValues(5)
        v.put("id", p.id)
        v.put("name", p.name)
        v.put("text", p.text)
        v.put("created_at", p.createdAt)
        v.put("updated_at", p.updatedAt)
        db.writableDatabase.insertWithOnConflict("prompts", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun delete(id: String) = withContext(db.writer) {
        db.writableDatabase.delete("prompts", "id=?", arrayOf(id))
    }
}
