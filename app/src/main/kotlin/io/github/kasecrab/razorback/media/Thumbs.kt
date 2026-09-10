package io.github.kasecrab.razorback.media

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Small decoded pictures for lists, bounded to a few megabytes. */
object Thumbs {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(scope: CoroutineScope, view: ImageView, path: String, edge: Int) {
        val key = "$path@$edge"
        view.tag = key
        cache.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { ImagePrep.thumbnail(path, edge) } ?: return@launch
            cache.put(key, bmp)
            if (view.tag == key) view.setImageBitmap(bmp)
        }
    }

    fun evict(path: String) {
        for (k in cache.snapshot().keys) if (k.startsWith("$path@")) cache.remove(k)
    }
}
