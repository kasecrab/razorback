package io.github.kasecrab.razorback.ui.core

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

/**
 * Navigation without Fragments: screens are views layered in one FrameLayout. Push slides
 * the new screen in from the trailing edge; predictive back scrubs the same motion.
 */
class ScreenStack(
    private val root: FrameLayout,
    private val back: BackDispatcher,
) : BackHandler {

    private val screens = ArrayList<Screen>(4)
    private var anim: ValueAnimator? = null
    private var predictiveProgress = -1f
    private var insetTop = 0
    private var insetBottom = 0
    private var insetLeft = 0
    private var insetRight = 0

    val top: Screen? get() = screens.lastOrNull()
    val size: Int get() = screens.size
    override val backEnabled: Boolean get() = screens.size > 1

    fun replaceRoot(screen: Screen) {
        anim?.end()
        for (s in screens) {
            s.onExit()
            root.removeView(s)
        }
        screens.clear()
        attach(screen)
        screen.onEnter()
        back.invalidate()
    }

    fun push(screen: Screen, animated: Boolean = true) {
        anim?.end()
        val under = top
        attach(screen)
        under?.onPause()
        screen.onEnter()
        if (under != null && animated && !screen.context.appTheme.reduceMotion) {
            run(from = 1f, to = 0f, top = screen, under = under) { under.visibility = View.INVISIBLE }
        } else {
            under?.visibility = View.INVISIBLE
        }
        back.invalidate()
    }

    fun pop(animated: Boolean = true): Boolean {
        if (screens.size <= 1) return false
        anim?.end()
        val leaving = screens.removeAt(screens.size - 1)
        val under = screens.last()
        under.visibility = View.VISIBLE
        apply(under = under, top = leaving, progress = 0f)
        under.onResume()
        leaving.onExit()
        if (animated && !leaving.context.appTheme.reduceMotion) {
            run(from = 0f, to = 1f, top = leaving, under = under) { root.removeView(leaving) }
        } else {
            root.removeView(leaving)
        }
        back.invalidate()
        return true
    }

    fun onInsetsChanged(top: Int, bottom: Int, left: Int, right: Int) {
        insetTop = top
        insetBottom = bottom
        insetLeft = left
        insetRight = right
        for (s in screens) s.onInsetsChanged(top, bottom, left, right)
    }

    private fun attach(screen: Screen) {
        screens.add(screen)
        root.addView(screen, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        root.context.ui().host.apply(screen)
        screen.onInsetsChanged(insetTop, insetBottom, insetLeft, insetRight)
    }

    /** progress 0 = top screen fully shown, 1 = top screen gone. */
    private fun apply(under: Screen, top: Screen, progress: Float) {
        val w = if (root.width > 0) root.width else root.resources.displayMetrics.widthPixels
        top.translationX = w * 0.12f * progress
        top.alpha = 1f - progress
        val s = 0.94f + 0.06f * progress
        under.scaleX = s
        under.scaleY = s
        under.alpha = 0.6f + 0.4f * progress
    }

    private fun run(from: Float, to: Float, top: Screen, under: Screen, onEnd: () -> Unit) {
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = top.context.appTheme.durLong
        a.interpolator = EASE
        a.addUpdateListener { apply(under, top, it.animatedValue as Float) }
        a.doOnEnd {
            anim = null
            reset(under)
            reset(top)
            onEnd()
        }
        anim = a
        apply(under, top, from)
        a.start()
    }

    private fun reset(v: View) {
        v.translationX = 0f
        v.alpha = 1f
        v.scaleX = 1f
        v.scaleY = 1f
    }

    // Predictive back

    override fun onBackStarted() {
        if (screens.size < 2) return
        anim?.end()
        val under = screens[screens.size - 2]
        under.visibility = View.VISIBLE
        predictiveProgress = 0f
        apply(under, screens.last(), 0f)
    }

    override fun onBackProgressed(progress: Float, swipeEdge: Int) {
        if (predictiveProgress < 0f || screens.size < 2) return
        predictiveProgress = progress * 0.5f
        apply(screens[screens.size - 2], screens.last(), predictiveProgress)
    }

    override fun onBackInvoked() {
        val topScreen = top
        if (topScreen != null && topScreen.onBack()) {
            onBackCancelled()
            return
        }
        if (predictiveProgress >= 0f && screens.size >= 2) {
            val start = predictiveProgress
            predictiveProgress = -1f
            val leaving = screens.removeAt(screens.size - 1)
            val under = screens.last()
            under.onResume()
            leaving.onExit()
            run(from = start, to = 1f, top = leaving, under = under) { root.removeView(leaving) }
            back.invalidate()
        } else {
            pop()
        }
    }

    override fun onBackCancelled() {
        if (predictiveProgress < 0f || screens.size < 2) return
        val start = predictiveProgress
        predictiveProgress = -1f
        val under = screens[screens.size - 2]
        run(from = start, to = 0f, top = screens.last(), under = under) { under.visibility = View.INVISIBLE }
    }

    private companion object {
        val EASE = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}

inline fun ValueAnimator.doOnEnd(crossinline block: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = block()
    })
}
