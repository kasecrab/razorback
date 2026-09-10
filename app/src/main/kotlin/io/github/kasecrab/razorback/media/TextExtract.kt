package io.github.kasecrab.razorback.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException

/** Small text-like files ride along inside the message as a tagged block. */
object TextExtract {

    const val MAX_BYTES = 200 * 1024

    class Loaded(val name: String, val text: String)

    fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }

    @Throws(IOException::class)
    fun load(context: Context, uri: Uri): Loaded {
        val name = displayName(context, uri) ?: "file"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("cannot open $name")
        if (bytes.size > MAX_BYTES) throw IOException("$name is larger than 200 KB")
        val text = String(bytes, Charsets.UTF_8)
        if (text.count { it == '�' } > text.length / 100 + 1) throw IOException("$name is not a text file")
        return Loaded(name, text)
    }

    fun wrap(name: String, text: String): String = "<file name=\"${name.replace("\"", "'")}\">\n$text\n</file>"
}
