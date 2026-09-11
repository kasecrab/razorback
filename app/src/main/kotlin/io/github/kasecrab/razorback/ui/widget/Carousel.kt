package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import io.github.kasecrab.razorback.ui.core.Spring
import io.github.kasecrab.razorback.ui.core.dp
import kotlin.math.abs

/**
 * One card at a time, neighbours peeking at the sides, a spring carrying each swipe to the
 * next page. Only five card views exist; they are rebound as the position moves, so a
 * hundred pages cost the same as five.
 */
class Carousel(context: Context) : ViewGroup(context) {

    var count = 0
        set(value) {
            field = value
            page = page.coerceIn(0, maxOf(0, value - 1))
            rebind()
        }

    /** Builds one reusable card; [bind] fills it for a page. */
    var create: (() -> View)? = null
    var bind: ((Int, View) -> Unit)? = null

    /** A new page was chosen, by release or tap, while the slide is still under way. */
    var onPage: ((Int) -> Unit)? = null

    var page = 0
        private set

    private var pos = 0f
    private val spring = Spring(stiffness = 260f, dampingRatio = 1f) { place(it) }
    private val slots = arrayOfNulls<View>(SLOTS)
    private val bound = IntArray(SLOTS) { -1 }
    private var cardWidth = 0
    private var step = 1
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragStart = 0f

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun jumpTo(index: Int) {
        page = index.coerceIn(0, maxOf(0, count - 1))
        spring.snapTo(page.toFloat())
    }

    fun scrollTo(index: Int, velocityPages: Float = 0f) {
        val target = index.coerceIn(0, maxOf(0, count - 1))
        val changed = target != page
        page = target
        spring.animateTo(target.toFloat(), velocityPages)
        if (changed) onPage?.invoke(target)
    }

    /** Every card now showing a page, with that page. */
    fun forEachCard(action: (Int, View) -> Unit) {
        for (k in 0 until SLOTS) {
            val v = slots[k] ?: continue
            if (bound[k] >= 0) action(bound[k], v)
        }
    }

    fun rebind() {
        for (k in 0 until SLOTS) bound[k] = -1
        place(pos)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cardWidth = (width * CARD_FRACTION).toInt()
        step = cardWidth + dp(12)
        ensureSlots()
        val cardSpec = MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY)
        val heightSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) heightMeasureSpec else MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.AT_MOST)
        var tallest = 0
        for (v in slots) {
            v!!.measure(cardSpec, heightSpec)
            if (v.measuredHeight > tallest) tallest = v.measuredHeight
        }
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else tallest
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = (width - cardWidth) / 2
        for (v in slots) {
            v!!.layout(left, 0, left + cardWidth, v.measuredHeight)
        }
        place(pos)
    }

    private fun ensureSlots() {
        if (slots[0] != null) return
        val make = create ?: return
        for (k in 0 until SLOTS) {
            val v = make()
            slots[k] = v
            addView(v)
        }
        rebind()
    }

    private fun slot(index: Int) = ((index % SLOTS) + SLOTS) % SLOTS

    private fun place(p: Float) {
        pos = p
        if (slots[0] == null) return
        val base = Math.round(p)
        for (i in base - 2..base + 2) {
            val k = slot(i)
            val v = slots[k] ?: continue
            if (i < 0 || i >= count) {
                v.visibility = INVISIBLE
                bound[k] = -1
                continue
            }
            if (bound[k] != i) {
                bound[k] = i
                bind?.invoke(i, v)
                v.visibility = VISIBLE
            }
            val d = i - p
            val away = abs(d)
            v.translationX = d * step
            val scale = 1f - 0.1f * minOf(away, 2f)
            v.scaleX = scale
            v.scaleY = scale
            v.alpha = 1f - 0.5f * minOf(away, 1f)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                track(ev)
                // Catching a moving carousel hands the slide to the finger at once.
                if (spring.isRunning) startDrag()
            }
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                if (!dragging) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) > slop && abs(dx) > abs(dy)) {
                        downX = ev.x
                        startDrag()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) release()
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                if (spring.isRunning) startDrag()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = ev.x - downX
                    if (abs(dx) > slop && abs(dx) > abs(ev.y - downY)) {
                        downX = ev.x
                        startDrag()
                    }
                }
                if (dragging) {
                    var p = dragStart - (ev.x - downX) / step
                    val last = (count - 1).toFloat()
                    if (p < 0f) p *= RUBBER
                    if (p > last) p = last + (p - last) * RUBBER
                    place(p)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val vt = velocity!!
                    vt.computeCurrentVelocity(1000)
                    val pagesPerSecond = -vt.xVelocity / step
                    scrollTo(Math.round(pos + pagesPerSecond * PROJECT_S), pagesPerSecond)
                }
                release()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) spring.animateTo(page.toFloat(), 0f)
                release()
            }
        }
        return true
    }

    private fun startDrag() {
        dragging = true
        dragStart = pos
        spring.cancel()
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun track(ev: MotionEvent) {
        (velocity ?: VelocityTracker.obtain().also { velocity = it }).addMovement(ev)
    }

    private fun release() {
        dragging = false
        velocity?.recycle()
        velocity = null
    }

    private companion object {
        const val SLOTS = 5
        const val CARD_FRACTION = 0.74f
        const val RUBBER = 0.35f
        /** How far ahead a fling is read, in seconds of its release speed. */
        const val PROJECT_S = 0.15f
    }
}
