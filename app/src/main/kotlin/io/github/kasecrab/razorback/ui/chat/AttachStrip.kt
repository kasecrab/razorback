package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.media.TextExtract
import io.github.kasecrab.razorback.media.Thumbs
import io.github.kasecrab.razorback.model.Attachment
import io.github.kasecrab.razorback.model.AttachmentKind
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.Shapes

/** What is about to be sent: image thumbnails and file chips, each removable. */
class AttachStrip(context: Context) : HorizontalScrollView(context), Themed {

    class Pending(val attachment: Attachment?, val text: TextExtract.Loaded?)

    private val row = LinearLayout(context)
    val items = ArrayList<Pending>()

    init {
        isHorizontalScrollBarEnabled = false
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(8), dp(4), dp(8), dp(4))
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        visibility = View.GONE
    }

    fun add(p: Pending) {
        items.add(p)
        val holder = FrameLayout(context)
        val a = p.attachment
        if (a != null && a.kind == AttachmentKind.IMAGE) {
            val img = ImageView(context)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.clipToOutline = true
            img.background = Shapes.rounded(context.appTheme.surface, dp(context.appTheme.radiusM))
            Thumbs.load(context.uiScope, img, a.path, dp(128))
            holder.addView(img, FrameLayout.LayoutParams(dp(72), dp(72)))
        } else {
            val chip = Chip(context)
            chip.style = Chip.Style.SOFT
            chip.leadingIcon = R.drawable.ic_file
            chip.text = p.text?.name ?: a?.name ?: "file"
            chip.maxWidth = dp(180)
            holder.addView(chip, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36), Gravity.CENTER_VERTICAL))
        }
        val remove = IconButton(context)
        remove.iconRes = R.drawable.ic_close
        remove.filled = true
        remove.tone = IconButton.Tone.PRIMARY
        remove.contentDescription = context.getString(R.string.cd_remove)
        remove.setOnClickListener {
            val i = row.indexOfChild(holder)
            if (i >= 0) {
                row.removeViewAt(i)
                items.removeAt(i)
            }
            visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }
        holder.addView(remove, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END))
        row.addView(holder, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        visibility = View.VISIBLE
    }

    fun clear() {
        items.clear()
        row.removeAllViews()
        visibility = View.GONE
    }

    override fun onThemeChanged(theme: Theme) {}
}
