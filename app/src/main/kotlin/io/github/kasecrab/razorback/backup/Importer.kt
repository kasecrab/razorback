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
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Reads a backup zip (or a settings file) and puts it into the database.
 *
 * Nothing is written until the whole of it has been read and understood. Replacing throws
 * away every chat, every attachment and every remote row, and doing that on the way past
 * the first table meant an archive that was truncated, or a line that stopped making
 * sense half-way down, took the data with it and left an error message where it had been.
 *
 * So an archive is spooled once into a file of our own and read through with nothing
 * touched — every entry parsed, every attachment name and size checked, the rows counted
 * against what the manifest claims — and only then is anything wiped or inserted. A
 * settings file is read the same way: every preference and key it would set is worked out
 * first and written afterwards, so it cannot land half applied.
 */
object Importer {

    enum class Mode { REPLACE, MERGE }

    class Result(val tables: Map<String, Int>, val files: Int, val settings: Boolean)

    suspend fun import(context: Context, uri: Uri, mode: Mode): Result = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open backup")
        input.buffered().use { stream ->
            val head = ByteArray(2)
            stream.mark(4)
            val n = stream.read(head)
            stream.reset()
            if (n == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) importZip(context, stream, mode) else importSettingsStream(stream)
        }
    }

    private fun importZip(context: Context, stream: InputStream, mode: Mode): Result {
        // Kept in a file of our own rather than read twice from the person's chosen
        // document: the pass that checks and the pass that writes have to be looking at
        // the same bytes, and what is on the other end of that document is somebody
        // else's to answer twice.
        val spooled = File.createTempFile("import", ".zip", context.cacheDir)
        try {
            spooled.outputStream().use { stream.copyTo(it) }
            return applyZip(context, spooled, readZip(context, spooled), mode)
        } finally {
            spooled.delete()
        }
    }

    /** What an archive turned out to hold, once every entry in it had been read through. */
    private class Plan(val rows: Map<String, Int>, val settings: Settings?)

    /**
     * The pass that touches nothing.
     *
     * Every entry is read to its end, so a line that is not a row, a table that runs past
     * its ceiling, an attachment with a path in its name and an archive cut short all stop
     * here, while the database and the attachments are still whole.
     */
    private fun readZip(context: Context, file: File): Plan {
        val attachmentsDir = ImagePrep.dir(context)
        var manifest: Manifest? = null
        var settings: Settings? = null
        val rows = HashMap<String, Int>()
        walk(file) { name, entry ->
            when {
                name == "manifest.json" -> manifest = Manifest.parse(JSONObject(text(entry)))
                name == "settings.json" -> settings = planSettings(JSONObject(text(entry)))
                name.startsWith("db/") && name.endsWith(".jsonl") -> {
                    if (manifest == null) throw IOException("manifest must come first")
                    val table = name.removePrefix("db/").removeSuffix(".jsonl")
                    if (table in Rows.TABLES) rows[table] = countRows(bounded(entry, MAX_TABLE_BYTES).bufferedReader())
                }
                name.startsWith("attachments/") -> {
                    attachmentFile(attachmentsDir, name)
                    bounded(entry, MAX_ATTACHMENT_BYTES).copyTo(OutputStream.nullOutputStream())
                }
            }
        }
        manifest?.let { m -> miscount(m.counts, rows)?.let { throw IOException(it) } }
        return Plan(rows, settings)
    }

    /**
     * What the manifest says is there against what was found, or null if they agree.
     *
     * A zip cut short still unpacks: the entries before the cut are whole and the ones
     * after are simply absent, which is exactly the archive that used to wipe everything
     * and then stop.
     *
     * Every table's count is its own, except attachments: the exporter writes the number
     * of attachment files under that name, over the count of the table of the same name,
     * so that one number says two things and neither can be relied on. A name this build
     * has never heard of is not this build's to count either.
     */
    internal fun miscount(claimed: Map<String, Int>, found: Map<String, Int>): String? {
        for ((table, n) in claimed) {
            if (table !in Rows.TABLES || table == "attachments") continue
            val there = found[table] ?: 0
            if (there != n) return "this backup says $table holds $n rows and it holds $there"
        }
        return null
    }

    /** The pass that writes. Everything here was read once already and made sense then. */
    private fun applyZip(context: Context, file: File, plan: Plan, mode: Mode): Result {
        val db = App.instance.db.writableDatabase
        val attachmentsDir = ImagePrep.dir(context)
        val counts = HashMap<String, Int>()
        var files = 0
        // The one step that cannot be taken back, taken on its own and in front of
        // everything rather than in the middle of a stream whose end is still unknown.
        if (mode == Mode.REPLACE) wipe(db, attachmentsDir)
        walk(file) { name, entry ->
            when {
                name.startsWith("db/") && name.endsWith(".jsonl") -> {
                    val table = name.removePrefix("db/").removeSuffix(".jsonl")
                    if (table in Rows.TABLES) {
                        counts[table] = importTable(db, table, bounded(entry, MAX_TABLE_BYTES).bufferedReader(), attachmentsDir)
                    }
                }
                name.startsWith("attachments/") -> {
                    val out = attachmentFile(attachmentsDir, name)
                    if (mode == Mode.MERGE && out.exists()) return@walk
                    out.outputStream().use { bounded(entry, MAX_ATTACHMENT_BYTES).copyTo(it) }
                    files++
                }
            }
        }
        plan.settings?.let { applySettings(it) }
        return Result(counts, files, plan.settings != null)
    }

    /** Every entry of an archive in turn, with the names that could climb out of it refused. */
    private fun walk(file: File, entry: (String, InputStream) -> Unit) {
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (name.contains("..") || name.startsWith("/")) throw IOException("unsafe entry $name")
                entry(name, zip)
                zip.closeEntry()
            }
        }
    }

    /** Where an attachment entry would land; a name with a path in it lands nowhere. */
    private fun attachmentFile(dir: File, entry: String): File {
        val name = entry.removePrefix("attachments/")
        val file = File(dir, name)
        if (file.name != name) throw IOException("unsafe attachment $entry")
        return file
    }

    private fun text(entry: InputStream): String =
        bounded(entry, MAX_JSON_BYTES).readBytes().toString(Charsets.UTF_8)

    /** How many rows a table entry holds, and that every one of them is a row. */
    private fun countRows(reader: BufferedReader): Int {
        var count = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            JSONObject(line)
            count++
        }
        return count
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
        applySettings(planSettings(JSONObject(text(stream))))
        return Result(emptyMap(), 0, true)
    }

    /** A settings file read but not yet written: everything it would set, in the order it set it. */
    private class Settings(val prefs: List<Pair<String, Any>>, val secrets: List<Pair<String, String>>)

    /**
     * A settings file is somebody else's bytes. Only preferences the app knows the shape
     * of are taken, the relay address is held to the same rule as when typed, and keys
     * go only under the four vendor names: nothing in a file can plant a pairing, reach
     * the encrypted store by name, or point the app at a plain-text relay.
     *
     * All the reading happens here and all the writing in [applySettings], so a file that
     * stops making sense on its tenth line leaves the settings as they were rather than
     * nine of them from the file and the rest from before it.
     */
    private fun planSettings(json: JSONObject): Settings {
        require(json.optString("app") == "razorback") { "not a Razorback settings file" }
        val prefs = ArrayList<Pair<String, Any>>()
        val secrets = ArrayList<Pair<String, String>>()
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
                    if (v is String && (v.isEmpty() || io.github.kasecrab.razorback.remote.RelayUrl.acceptable(v))) prefs.add(k to io.github.kasecrab.razorback.remote.RelayUrl.clean(v))
                    continue
                }
                when (v) {
                    is String -> prefs.add(k to v)
                    is Boolean -> prefs.add(k to v)
                    is Int -> prefs.add(k to v)
                    is Long -> prefs.add(k to v)
                    is Double -> prefs.add(k to v.toFloat())
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
                if (v.length <= MAX_PREF_CHARS) secrets.add(k to v)
            }
        }
        return Settings(prefs, secrets)
    }

    private fun applySettings(settings: Settings) {
        val app = App.instance
        for ((k, v) in settings.prefs) {
            when (v) {
                is String -> app.prefs.putRawString(k, v)
                is Boolean -> app.prefs.putRawBoolean(k, v)
                is Int -> app.prefs.putRawInt(k, v)
                is Long -> app.prefs.putRawLong(k, v)
                is Float -> app.prefs.putRawFloat(k, v)
                else -> Log.w("skipping pref $k")
            }
        }
        for ((k, v) in settings.secrets) app.secrets.put(k, v)
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
