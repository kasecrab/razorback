package io.github.kasecrab.razorback.media

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import io.github.kasecrab.razorback.core.Ids

/** The three ways a file gets into a message. */
object Pick {

    fun photos(max: Int = 4): Intent = Intent(MediaStore.ACTION_PICK_IMAGES).apply {
        type = "image/*"
        if (max > 1) putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, max)
    }

    fun files(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "text/*", "application/json", "application/pdf"))
    }

    class Capture(val intent: Intent, val fileName: String)

    fun camera(context: Context): Capture {
        val name = Ids.next() + ".jpg"
        CameraProvider.fileFor(context, name).parentFile?.mkdirs()
        val uri = CameraProvider.uriFor(name)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Capture(intent, name)
    }
}
