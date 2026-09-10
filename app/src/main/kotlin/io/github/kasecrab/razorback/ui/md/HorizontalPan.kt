package io.github.kasecrab.razorback.ui.md

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs

/**
 * Horizontal drag and fling for a view wider than its frame, living inside a vertical
 * list. Vertical strokes are left to the parent; only a clearly horizontal one is taken.
 */
class HorizontalPan(private val view: View) {

    var scrollX = 0
        private set
    var maxScroll = 0
        set(value) {
            field = maxOf(0, value)
            if (scrollX > field) scrollX = field
        }

    private val scroller = OverScroller(view.context)
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(view.context).scaledMinimumFlingVelocity
    private val maxFling = ViewConfiguration.get(view.context).scaledMaximumFlingVelocity
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false

    /** Returns true when the event was consumed. */
    fun onTouchEvent(ev: MotionEvent): Boolean {
        if (maxScroll == 0) return false
        (velocity ?: VelocityTracker.obtain().also { velocity = it }).addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) scroller.abortAnimation()
                downX = ev.x
                downY = ev.y
                lastX = ev.x
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dx) > slop && abs(dx) > abs(dy)) {
                        dragging = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        lastX = ev.x
                    } else if (abs(dy) > slop) {
                        return false
                    }
                }
                if (dragging) {
                    scrollTo(scrollX - (ev.x - lastX).toInt())
                    lastX = ev.x
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val consumed = dragging
                if (dragging) {
                    val vt = velocity!!
                    vt.computeCurrentVelocity(1000, maxFling.toFloat())
                    val vx = vt.xVelocity
                    if (abs(vx) > minFling) {
                        scroller.fling(scrollX, 0, -vx.toInt(), 0, 0, maxScroll, 0, 0)
                        view.postInvalidateOnAnimation()
                    }
                }
                release()
                return consumed
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return dragging
    }

    /** Call from View.computeScroll. */
    fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX)
            view.postInvalidateOnAnimation()
        }
    }

    private fun scrollTo(x: Int) {
        val clamped = x.coerceIn(0, maxScroll)
        if (clamped != scrollX) {
            scrollX = clamped
            view.invalidate()
        }
    }

    private fun release() {
        dragging = false
        velocity?.recycle()
        velocity = null
    }
}
