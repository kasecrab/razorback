package io.github.kasecrab.razorback.backup

import android.content.Context
import android.net.Uri
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.BuildConfig
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.core.jsonObject
import io.github.kasecrab.razorback.media.ImagePrep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes the whole app, or just its settings, to a document the person chose. */
object Exporter {

    suspend fun exportFull(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val app = App.instance
        val db = app.db.readableDatabase
        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("cannot open destination")
        ZipOutputStream(out.buffered()).use { zip ->
            val counts = HashMap<String, Int>()
            for (t in Rows.TABLES) db.rawQuery("SELECT COUNT(*) FROM $t", null).use { c -> c.moveToFirst(); counts[t] = c.getInt(0) }
            entry(zip, "manifest.json", Manifest(Manifest.FORMAT, BuildConfig.VERSION_NAME, System.currentTimeMillis(), counts).toJson().toString(2))
            entry(zip, "settings.json", settingsJson(app, includeKeys = false).toString(2))
            val attachmentsDir = ImagePrep.dir(context).absolutePath
            val files = HashSet<String>()
            for (t in Rows.TABLES) {
                zip.putNextEntry(ZipEntry("db/$t.jsonl"))
                db.rawQuery("SELECT * FROM $t", null).use { c ->
                    while (c.moveToNext()) {
                        val row = Rows.toJson(c)
                        if (t == "messages") relativise(row, attachmentsDir, files)
                        zip.write(row.toString().toByteArray())
                        zip.write('\n'.code)
                    }
                }
                zip.closeEntry()
            }
            for (name in files) {
                val f = File(attachmentsDir, name)
                if (!f.isFile) continue
                zip.putNextEntry(ZipEntry("attachments/$name"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun exportSettings(context: Context, uri: Uri, includeKeys: Boolean) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("cannot open destination")
        out.bufferedWriter().use { it.write(settingsJson(App.instance, includeKeys).toString(2)) }
    }

    /** Image paths become `attachments/<name>` so the backup restores anywhere. */
    private fun relativise(row: JSONObject, dir: String, files: MutableSet<String>) {
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
            if (p.startsWith(dir)) {
                val name = p.substring(dir.length).trimStart('/')
                files.add(name)
                out.put("attachments/$name")
            } else {
                out.put(p)
            }
        }
        row.put("images", out.toString())
    }

    fun settingsJson(app: App, includeKeys: Boolean): JSONObject = jsonObject {
        put("app", "razorback")
        put("format", Manifest.FORMAT)
        put("prefs", jsonObject { for ((k, v) in app.prefs.snapshot()) put(k, v) })
        if (includeKeys) {
            put("secrets", jsonObject {
                for (name in listOf(Secrets.OPENROUTER, Secrets.DEEPGRAM, Secrets.BRAVE, Secrets.EXA)) app.secrets.get(name)?.let { put(name, it) }
            })
        }
    }

    private fun entry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray())
        zip.closeEntry()
    }
}
