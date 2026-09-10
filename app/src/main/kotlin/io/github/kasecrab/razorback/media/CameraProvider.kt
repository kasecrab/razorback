package io.github.kasecrab.razorback.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the camera app a writable file in our cache. Only names of the form
 * `<uuid>.jpg` under cache/camera are reachable, nothing else on disk.
 */
class CameraProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!NAME.matches(name)) throw FileNotFoundException(name)
        val dir = File(context!!.cacheDir, DIR).apply { mkdirs() }
        val flags = if (mode.contains('w')) ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(File(dir, name), flags)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = 0

    companion object {
        const val AUTHORITY = "io.github.kasecrab.razorback.camera"
        const val DIR = "camera"
        private val NAME = Regex("^[0-9a-f]{20}\\.jpg$")

        fun uriFor(name: String): Uri = Uri.parse("content://$AUTHORITY/$name")

        fun fileFor(context: android.content.Context, name: String): File = File(File(context.cacheDir, DIR), name)
    }
}
