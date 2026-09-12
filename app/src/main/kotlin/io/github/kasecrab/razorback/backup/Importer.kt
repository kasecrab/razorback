package io.github.kasecrab.razorback.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.media.ImagePrep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Reads a backup zip (or a settings file) in one pass and puts it into the database. */
object Importer {

    enum class Mode { REPLACE, MERGE }

    class Result(val tables: Map<String, Int>, val files: Int, val settings: Boolean)

    suspend fun import(context: Context, uri: Uri, mode: Mode): Result = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open backup")
        val head = ByteArray(2)
        val stream = input.buffered()
        stream.mark(4)
        val n = stream.read(head)
        stream.reset()
        if (n == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) importZip(context, stream, mode) else importSettingsStream(stream)
    }

    private fun importZip(context: Context, stream: InputStream, mode: Mode): Result {
        val app = App.instance
        val db = app.db.writableDatabase
        val attachmentsDir = ImagePrep.dir(context)
        val counts = HashMap<String, Int>()
        var files = 0
        var settings = false
        var manifestSeen = false
        var wiped = false
        ZipInputStream(stream).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (name.contains("..") || name.startsWith("/")) throw IOException("unsafe entry $name")
                when {
                    name == "manifest.json" -> {
                        Manifest.parse(JSONObject(bounded(zip, MAX_JSON_BYTES).readBytes().toString(Charsets.UTF_8)))
                        manifestSeen = true
                    }
                    name == "settings.json" -> {
                        applySettings(JSONObject(bounded(zip, MAX_JSON_BYTES).readBytes().toString(Charsets.UTF_8)))
                        settings = true
                    }
                    name.startsWith("db/") && name.endsWith(".jsonl") -> {
                        if (!manifestSeen) throw IOException("manifest must come first")
                        val table = name.removePrefix("db/").removeSuffix(".jsonl")
                        if (table !in Rows.TABLES) continue
                        if (mode == Mode.REPLACE && !wiped) {
                            wipe(db, attachmentsDir)
                            wiped = true
                        }
                        counts[table] = importTable(db, table, bounded(zip, MAX_TABLE_BYTES).bufferedReader(), attachmentsDir)
                    }
                    name.startsWith("attachments/") -> {
                        val file = File(attachmentsDir, name.removePrefix("attachments/"))
                        if (file.name != name.removePrefix("attachments/")) throw IOException("unsafe attachment $name")
                        if (mode == Mode.MERGE && file.exists()) continue
                        file.outputStream().use { bounded(zip, MAX_ATTACHMENT_BYTES).copyTo(it) }
                        files++
                    }
                }
                zip.closeEntry()
            }
        }
        return Result(counts, files, settings)
    }

    private fun importTable(db: SQLiteDatabase, table: String, reader: BufferedReader, attachmentsDir: File): Int {
        val columns = Rows.columns(db, table)
        var count = 0
        var inBatch = 0
        db.beginTransactionNonExclusive()
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val row = JSONObject(line)
                if (table == "messages") absolutise(row, attachmentsDir)
                val id = db.insertWithOnConflict(table, null, Rows.toValues(row, columns), SQLiteDatabase.CONFLICT_IGNORE)
                if (id != -1L) count++
                if (++inBatch >= 500) {
                    db.setTransactionSuccessful()
                    db.endTransaction()
                    db.beginTransactionNonExclusive()
                    inBatch = 0
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    private fun absolutise(row: JSONObject, dir: File) {
        val raw = row.optString("images", "")
        if (raw.isEmpty() || raw == "null") return
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return
        }
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val p = arr.optString(i)
            out.put(if (p.startsWith("attachments/")) File(dir, p.removePrefix("attachments/")).absolutePath else p)
        }
        row.put("images", out.toString())
    }

    private fun wipe(db: SQLiteDatabase, attachmentsDir: File) {
        db.beginTransactionNonExclusive()
        try {
            for (t in Rows.TABLES.reversed()) db.delete(t, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        attachmentsDir.listFiles()?.forEach { it.delete() }
    }

    private fun importSettingsStream(stream: InputStream): Result {
        val json = JSONObject(bounded(stream, MAX_JSON_BYTES).readBytes().toString(Charsets.UTF_8))
        applySettings(json)
        return Result(emptyMap(), 0, true)
    }

    /**
     * A settings file is somebody else's bytes. Only preferences the app knows the shape
     * of are taken, the relay address is held to the same rule as when typed, and keys
     * go only under the four vendor names: nothing in a file can plant a pairing, reach
     * the encrypted store by name, or point the app at a plain-text relay.
     */
    private fun applySettings(json: JSONObject) {
        require(json.optString("app") == "razorback") { "not a Razorback settings file" }
        val app = App.instance
        json.optJSONObject("prefs")?.let { p ->
            for (k in p.keys()) {
                if (!prefAllowed(k)) {
                    Log.w("skipping pref $k")
                    continue
                }
                val v = p.opt(k)
                if (v is String && v.length > MAX_PREF_CHARS) {
                    Log.w("skipping oversized pref $k")
                    continue
                }
                if (k == io.github.kasecrab.razorback.core.Keys.RELAY_URL.name) {
                    if (v is String && (v.isEmpty() || io.github.kasecrab.razorback.remote.RelayUrl.acceptable(v))) app.prefs.putRawString(k, io.github.kasecrab.razorback.remote.RelayUrl.clean(v))
                    continue
                }
                when (v) {
                    is String -> app.prefs.putRawString(k, v)
                    is Boolean -> app.prefs.putRawBoolean(k, v)
                    is Int -> app.prefs.putRawInt(k, v)
                    is Long -> app.prefs.putRawLong(k, v)
                    is Double -> app.prefs.putRawFloat(k, v.toFloat())
                    else -> Log.w("skipping pref $k")
                }
            }
        }
        json.optJSONObject("secrets")?.let { s ->
            for (k in s.keys()) {
                if (k !in IMPORTABLE_SECRETS) {
                    Log.w("skipping secret $k")
                    continue
                }
                val v = s.optString(k)
                if (v.length <= MAX_PREF_CHARS) app.secrets.put(k, v)
            }
        }
    }

    /** The namespaces the app's own preferences live in; a file gets nothing outside them. */
    private fun prefAllowed(name: String): Boolean =
        PREF_NAMESPACES.any { name.startsWith(it) } && !name.startsWith(io.github.kasecrab.razorback.core.Secrets.PREFIX)

    /**
     * A zip says how big an entry is only in a header anyone can write, so every entry is
     * read through a ceiling instead: a file that claims a few bytes and unpacks to
     * gigabytes stops at the ceiling with a plain error rather than filling memory or the disk.
     */
    private fun bounded(stream: InputStream, max: Long): InputStream = object : java.io.FilterInputStream(stream) {
        private var seen = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) count(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) count(n)
            return n
        }

        private fun count(n: Int) {
            seen += n
            if (seen > max) throw IOException("an entry in the backup is larger than ${max / (1024 * 1024)} MB")
        }

        // The zip stream must stay open for the entries after this one.
        override fun close() {}
    }

    private const val MAX_JSON_BYTES = 4L * 1024 * 1024
    private const val MAX_ATTACHMENT_BYTES = 64L * 1024 * 1024
    private const val MAX_TABLE_BYTES = 1024L * 1024 * 1024

    private val PREF_NAMESPACES = listOf("model.", "prompt.", "theme.", "tools.", "ui.", "voice.", "verified.", "relay.url")
    private val IMPORTABLE_SECRETS = setOf(
        io.github.kasecrab.razorback.core.Secrets.OPENROUTER,
        io.github.kasecrab.razorback.core.Secrets.DEEPGRAM,
        io.github.kasecrab.razorback.core.Secrets.BRAVE,
        io.github.kasecrab.razorback.core.Secrets.EXA,
    )
    private const val MAX_PREF_CHARS = 64 * 1024
}
