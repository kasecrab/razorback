package io.github.kasecrab.razorback.ui.drawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import io.github.kasecrab.razorback.ui.core.BackHandler
import io.github.kasecrab.razorback.ui.core.Spring
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import kotlin.math.abs

/**
 * Two children: content underneath, panel sliding in from the start edge over a scrim.
 * Drag from the edge or anywhere with a mostly-horizontal stroke; fling or halfway decides.
 */
class DrawerHost(context: Context) : ViewGroup(context), Themed, BackHandler {

    lateinit var content: View
    lateinit var panel: View

    var onOpenChanged: ((Boolean) -> Unit)? = null

    /** 0 closed, 1 open. */
    var fraction = 0f
        private set(value) {
            if (field == value) return
            field = value
            panel.translationX = -panelWidth * (1f - value)
            panel.visibility = if (value > 0f) View.VISIBLE else View.INVISIBLE
            invalidate()
        }

    val isOpen: Boolean get() = fraction >= 0.999f
    override val backEnabled: Boolean get() = fraction > 0f

    private val spring = Spring(stiffness = 600f, dampingRatio = 1f) { fraction = it }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgeWidth = dp(24)
    private var panelWidth = 0
    private var scrimColor = 0
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragStartFraction = 0f
    private var backStartFraction = 0f
    private val exclusion = ArrayList<Rect>(1)

    init {
        onThemeChanged(context.appTheme)
    }

    fun open() {
        io.github.kasecrab.razorback.ui.core.Keyboard.hide(this)
        settle(1f, 0f)
    }

    fun close() = settle(0f, 0f)

    fun toggle() = if (fraction > 0.5f) close() else open()

    override fun onThemeChanged(theme: Theme) {
        scrimColor = theme.scrim
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        content.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        panelWidth = minOf(dp(320), (w * 0.85f).toInt())
        panel.measure(MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        content.layout(0, 0, r - l, b - t)
        panel.layout(0, 0, panelWidth, b - t)
        panel.translationX = -panelWidth * (1f - fraction)
        panel.visibility = if (fraction > 0f) View.VISIBLE else View.INVISIBLE
        val h = b - t
        val strip = dp(200)
        exclusion.clear()
        exclusion.add(Rect(0, (h - strip) / 2, edgeWidth, (h + strip) / 2))
        systemGestureExclusionRects = exclusion
    }

    override fun dispatchDraw(canvas: Canvas) {
        val time = drawingTime
        drawChild(canvas, content, time)
        if (fraction > 0f) {
            val alpha = ((scrimColor ushr 24) * fraction).toInt()
            canvas.drawColor((scrimColor and 0x00FFFFFF) or (alpha shl 24))
            drawChild(canvas, panel, time)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                track(ev)
                if (spring.isRunning) {
                    spring.cancel()
                    startDrag()
                    return true
                }
                // A tap on the scrim closes; grab it here so the content never sees it.
                if (fraction > 0f && ev.x > panelWidth) return true
            }
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                if (dragging) return true
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) < touchSlop || abs(dx) < abs(dy) * 2f) return false
                val opening = fraction == 0f && dx > 0 && (downX < edgeWidth || allowContentDrag)
                val closing = fraction > 0f && dx < 0
                if (opening || closing) {
                    startDrag()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release()
        }
        return false
    }

    /**
     * Whether a horizontal stroke anywhere on the content opens the drawer. Off by default:
     * code blocks and tables pan sideways, and a parent that grabs those strokes first would
     * steal them. The edge strip and the menu button always work.
     */
    var allowContentDrag = false

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = ev.x - downX
                    if (abs(dx) > touchSlop) startDrag()
                }
                if (dragging) {
                    val dx = ev.x - downX
                    fraction = (dragStartFraction + dx / panelWidth).coerceIn(0f, 1f)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val v = velocity?.let { it.computeCurrentVelocity(1000); it.xVelocity } ?: 0f
                    val target = when {
                        abs(v) > FLING_PX_PER_S -> if (v > 0) 1f else 0f
                        else -> if (fraction > 0.5f) 1f else 0f
                    }
                    settle(target, v / panelWidth)
                } else if (fraction > 0f && ev.x > panelWidth) {
                    close()
                }
                release()
            }
            MotionEvent.ACTION_CANCEL -> {
                settle(if (fraction > 0.5f) 1f else 0f, 0f)
                release()
            }
        }
        return true
    }

    private fun startDrag() {
        dragging = true
        dragStartFraction = fraction
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

    private fun settle(target: Float, v: Float) {
        val wasOpen = isOpen
        if (context.appTheme.reduceMotion) {
            spring.snapTo(target)
            if (wasOpen != isOpen) onOpenChanged?.invoke(isOpen)
            return
        }
        spring.animateTo(target, v) { if (wasOpen != isOpen) onOpenChanged?.invoke(isOpen) }
        // Report the opening intent up front so the back handler is registered immediately.
        if (target == 1f && !wasOpen) onOpenChanged?.invoke(true)
    }

    // Back gesture: scrub the panel with the swipe, close on commit.

    override fun onBackStarted() {
        spring.cancel()
        backStartFraction = fraction
    }

    override fun onBackProgressed(progress: Float, swipeEdge: Int) {
        fraction = backStartFraction * (1f - 0.35f * progress)
    }

    override fun onBackInvoked() = close()

    override fun onBackCancelled() = settle(1f, 0f)

    private companion object {
        const val FLING_PX_PER_S = 1200f
    }
}
