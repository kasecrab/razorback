package io.github.kasecrab.razorback.ui.image

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Log
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.IconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A picture on black with close and save; tap toggles the buttons. */
class ImageViewerScreen(context: Context, private val path: String) : Screen(context) {

    private val zoom = ZoomImageView(context)
    private val close = IconButton(context)
    private val save = IconButton(context)
    private var insetTop = 0

    init {
        addView(zoom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        close.iconRes = R.drawable.ic_close
        close.filled = true
        close.tone = IconButton.Tone.PRIMARY
        close.contentDescription = context.getString(R.string.cd_back)
        close.setOnClickListener { context.nav.pop() }
        addView(close, LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(12), 0, 0) })
        save.iconRes = R.drawable.ic_download
        save.filled = true
        save.tone = IconButton.Tone.PRIMARY
        save.contentDescription = context.getString(R.string.cd_save_image)
        save.setOnClickListener { saveToPictures() }
        addView(save, LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(12), dp(12), 0) })
        zoom.onTap = {
            val show = close.visibility != View.VISIBLE
            close.visibility = if (show) View.VISIBLE else View.INVISIBLE
            save.visibility = close.visibility
        }
    }

    override fun onEnter() {
        context.uiScope.launch {
            val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) } ?: return@launch
            zoom.setBitmap(bmp)
        }
    }

    override fun onInsetsChanged(top: Int, bottom: Int, left: Int, right: Int) {
        insetTop = top
        (close.layoutParams as LayoutParams).topMargin = top + dp(12)
        (save.layoutParams as LayoutParams).topMargin = top + dp(12)
        requestLayout()
    }

    override fun onThemeChanged(theme: Theme) {
        setBackgroundColor(0xFF000000.toInt())
    }

    private fun saveToPictures() {
        context.uiScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "razorback-${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Razorback")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                    resolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    true
                } catch (e: Exception) {
                    Log.w("save image failed", e)
                    false
                }
            }
            if (ok) {
                io.github.kasecrab.razorback.ui.core.Haptics.confirm()
                Toast.makeText(context, R.string.image_saved, Toast.LENGTH_SHORT).show()
            } else {
                io.github.kasecrab.razorback.ui.core.Haptics.reject()
            }
        }
    }
}
