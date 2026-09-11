package io.github.kasecrab.razorback.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.kasecrab.razorback.ui.core.BackHandler
import io.github.kasecrab.razorback.ui.core.Spring
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.ui
import kotlin.math.abs

/**
 * Bottom sheet over the whole window: scrim, drag handle, spring in and out. Drag the
 * handle or header to dismiss; the content area scrolls on its own.
 */
open class Sheet(context: Context) : FrameLayout(context), Themed, BackHandler {

    val panel = LinearLayout(context)
    val body = LinearLayout(context)
    private val handle = View(context)
    private val spring = Spring(stiffness = 700f, dampingRatio = 1f) { fraction = it }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocity: VelocityTracker? = null
    private var downY = 0f
    private var dragging = false
    private var dragStart = 0f
    private var backStart = 0f
    private var scrimColor = 0
    private var shown = false
    var onDismiss: (() -> Unit)? = null
    /** The panel keeps clear of the navigation bar, and of the keyboard when one comes up under it. */
    private val onInsets: () -> Unit = {
        val ui = context.ui()
        panel.setPadding(0, 0, 0, maxOf(ui.insetBottom, ui.imeBottom) + dp(8))
    }

    private var fraction = 0f
        set(value) {
            field = value
            panel.translationY = panel.height * (1f - value)
            invalidate()
        }

    override val backEnabled: Boolean get() = shown

    init {
        panel.orientation = LinearLayout.VERTICAL
        panel.isClickable = true
        handle.layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(10)
            bottomMargin = dp(6)
        }
        panel.addView(handle)
        body.orientation = LinearLayout.VERTICAL
        panel.addView(body, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setWillNotDraw(false)
        // Subclasses are still being built here, so only the base look is applied now;
        // show() walks the finished tree with the theme.
        paintBase(context.appTheme)
    }

    /** Sheets that carry their own text field keep the keyboard; everything else dismisses it. */
    protected open val wantsKeyboard: Boolean get() = false

    open fun show() {
        if (shown) return
        shown = true
        val ui = context.ui()
        if (!wantsKeyboard) io.github.kasecrab.razorback.ui.core.Keyboard.hideAll(context)
        onInsets()
        ui.watchInsets(onInsets)
        ui.root.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ui.host.apply(this)
        ui.back.add(this, priority = 20)
        panel.visibility = View.INVISIBLE
        post {
            panel.visibility = View.VISIBLE
            fraction = 0f
            if (context.appTheme.reduceMotion) spring.snapTo(1f) else spring.animateTo(1f, 0f)
        }
    }

    fun dismiss() {
        if (!shown) return
        shown = false
        context.ui().back.remove(this)
        context.ui().unwatchInsets(onInsets)
        val done: () -> Unit = {
            (parent as? FrameLayout)?.removeView(this)
            onDismiss?.invoke()
        }
        if (context.appTheme.reduceMotion) {
            spring.snapTo(0f)
            done()
        } else {
            spring.animateTo(0f, spring.velocity, done)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val cap = (MeasureSpec.getSize(heightMeasureSpec) * 0.82f).toInt()
        if (panel.measuredHeight > cap) {
            (panel.layoutParams as LayoutParams).height = cap
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        panel.translationY = panel.height * (1f - fraction)
    }

    override fun onDraw(canvas: Canvas) {
        val a = ((scrimColor ushr 24) * fraction).toInt()
        canvas.drawColor((scrimColor and 0x00FFFFFF) or (a shl 24))
    }

    override fun onThemeChanged(theme: Theme) = paintBase(theme)

    private fun paintBase(theme: Theme) {
        scrimColor = theme.scrim
        panel.background = Shapes.rounded(theme.surfaceElevated, dp(theme.radiusXl)).apply {
            cornerRadii = floatArrayOf(dp(theme.radiusXl), dp(theme.radiusXl), dp(theme.radiusXl), dp(theme.radiusXl), 0f, 0f, 0f, 0f)
        }
        handle.background = Shapes.pill(theme.outline)
        invalidate()
    }

    /** A sheet on its way out is no longer in the way: touches fall through to what is beneath. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!shown) return false
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                dragging = false
                track(ev)
                if (ev.y < panel.top + panel.translationY) return true
                // Only the handle strip starts a drag by itself; lists below keep their scroll.
                if (ev.y < panel.top + panel.translationY + dp(40)) {
                    startDrag()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                if (dragging) return true
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(ev.y - downY) > slop && ev.y > panel.top + panel.translationY) startDrag()
                if (dragging) fraction = (dragStart - (ev.y - downY) / panel.height).coerceIn(0f, 1.05f)
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val vt = velocity!!
                    vt.computeCurrentVelocity(1000)
                    val vy = vt.yVelocity
                    if (vy > 1200f || (fraction < 0.5f && vy > -600f)) dismiss() else spring.animateTo(1f, -vy / panel.height)
                } else if (ev.y < panel.top + panel.translationY) {
                    dismiss()
                }
                release()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) spring.animateTo(1f, 0f)
                release()
            }
        }
        return true
    }

    private fun startDrag() {
        dragging = true
        dragStart = fraction
        spring.cancel()
    }

    private fun track(ev: MotionEvent) {
        (velocity ?: VelocityTracker.obtain().also { velocity = it }).addMovement(ev)
    }

    private fun release() {
        dragging = false
        velocity?.recycle()
        velocity = null
    }

    override fun onBackStarted() {
        spring.cancel()
        backStart = fraction
    }

    override fun onBackProgressed(progress: Float, swipeEdge: Int) {
        fraction = backStart * (1f - 0.3f * progress)
    }

    override fun onBackInvoked() = dismiss()

    override fun onBackCancelled() = spring.animateTo(1f, 0f)
}
