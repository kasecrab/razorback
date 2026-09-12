package io.github.kasecrab.razorback.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import io.github.kasecrab.razorback.core.Ids
import io.github.kasecrab.razorback.model.Attachment
import io.github.kasecrab.razorback.model.AttachmentKind
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Pictures go in as whatever the picker gives us and come out as a JPEG no longer than
 * 1568 px on its long edge under filesDir/attachments. Orientation is applied on decode.
 */
object ImagePrep {

    private const val MAX_EDGE = 1568
    /** About 18 MB of picture, well past anything a model returns. */
    private const val MAX_DATA_URL_CHARS = 24 * 1024 * 1024
    private const val QUALITY = 85
    const val DIR = "attachments"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    @Throws(IOException::class)
    fun importUri(context: Context, uri: Uri, name: String?): Attachment {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return save(context, decode(source), name ?: "image")
    }

    @Throws(IOException::class)
    fun importFile(context: Context, file: File, name: String?): Attachment {
        val source = ImageDecoder.createSource(file)
        return save(context, decode(source), name ?: file.name)
    }

    /** A picture the model produced, arriving as a data URL. */
    @Throws(IOException::class)
    fun importDataUrl(context: Context, dataUrl: String): Attachment {
        val comma = dataUrl.indexOf(',')
        if (!dataUrl.startsWith("data:") || comma < 0) throw IOException("not a data url")
        // Whatever the model sends is decoded into memory whole; past this it is refused, not attempted.
        if (dataUrl.length - comma > MAX_DATA_URL_CHARS) throw IOException("generated image is too large")
        val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
        return save(context, decode(source), "generated")
    }

    private fun decode(source: ImageDecoder.Source): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val long = maxOf(info.size.width, info.size.height)
            if (long > MAX_EDGE) decoder.setTargetSampleSize(maxOf(1, long / MAX_EDGE))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }

    private fun save(context: Context, decoded: Bitmap, name: String): Attachment {
        var bmp = decoded
        val long = maxOf(bmp.width, bmp.height)
        if (long > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / long
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            bmp.recycle()
            bmp = scaled
        }
        val id = Ids.next()
        val file = File(dir(context), "$id.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        val a = Attachment(id, AttachmentKind.IMAGE, "image/jpeg", file.absolutePath, name, bmp.width, bmp.height, file.length())
        bmp.recycle()
        return a
    }

    /** Small bitmap for thumbnails; [edge] is the longest side in pixels. */
    fun thumbnail(path: String, edge: Int): Bitmap? {
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= edge) sample *= 2
        val real = BitmapFactory.Options()
        real.inSampleSize = sample
        real.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(path, real)
    }

    /** Base64 data URL for the request body, built only when a request goes out. */
    fun dataUrl(path: String): String {
        val bytes = File(path).readBytes()
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
