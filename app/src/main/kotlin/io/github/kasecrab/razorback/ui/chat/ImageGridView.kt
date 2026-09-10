package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import io.github.kasecrab.razorback.media.Thumbs
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.image.ImageViewerScreen
import io.github.kasecrab.razorback.ui.widget.Shapes

/** One picture full width, more in two columns; tap opens the viewer. */
class ImageGridView(context: Context) : ViewGroup(context), Themed {

    private var paths: List<String> = emptyList()
    private val gap = dp(6)

    fun set(list: List<String>) {
        if (list == paths) return
        paths = list
        removeAllViews()
        for (p in list) {
            val iv = ImageView(context)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.clipToOutline = true
            iv.background = Shapes.rounded(context.appTheme.surface, dp(context.appTheme.radiusM))
            iv.setOnClickListener { context.nav.push(ImageViewerScreen(context, p)) }
            addView(iv)
        }
        visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val n = childCount
        if (n == 0) {
            setMeasuredDimension(w, 0)
            return
        }
        val cols = if (n == 1) 1 else 2
        val cell = (w - gap * (cols - 1)) / cols
        val cellH = if (n == 1) minOf(cell, dp(260)) else cell
        val rows = (n + cols - 1) / cols
        for (i in 0 until n) {
            val child = getChildAt(i)
            child.measure(MeasureSpec.makeMeasureSpec(if (n == 1) w else cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY))
            Thumbs.load(context.uiScope, child as ImageView, paths[i], maxOf(cell, cellH))
        }
        setMeasuredDimension(w, rows * cellH + (rows - 1) * gap)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val n = childCount
        if (n == 0) return
        val cols = if (n == 1) 1 else 2
        for (i in 0 until n) {
            val child = getChildAt(i)
            val col = i % cols
            val row = i / cols
            val x = col * (child.measuredWidth + gap)
            val y = row * (child.measuredHeight + gap)
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    override fun onThemeChanged(theme: Theme) {
        for (i in 0 until childCount) getChildAt(i).background = Shapes.rounded(theme.surface, dp(theme.radiusM))
    }
}
