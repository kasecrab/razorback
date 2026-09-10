package io.github.kasecrab.razorback.ui.voice

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.widget.Shapes

/**
 * Everything said in the conversation, oldest at the top: what the person said in small
 * bubbles on the trailing side, what the assistant said as paragraphs that light up as they
 * are spoken. Follows the newest line unless the person has scrolled back.
 */
class TranscriptView(context: Context) : ScrollView(context), Themed {

    private val column = LinearLayout(context)
    private var userLine: TextView? = null
    private var following = true

    /** The assistant line currently being spoken, if any. */
    var reply: KaraokeTextView? = null
        private set

    init {
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(24), dp(12), dp(24), dp(16))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        isVerticalScrollBarEnabled = false
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(56))
        overScrollMode = OVER_SCROLL_NEVER
        onThemeChanged(context.appTheme)
    }

    fun clear() {
        column.removeAllViews()
        userLine = null
        reply = null
        following = true
    }

    /** A finished line from before this session. */
    fun addHistory(text: String, fromUser: Boolean) {
        if (fromUser) {
            addUserLine(text, final = true)
        } else {
            val k = newReply()
            k.setText(text)
            k.lightAll()
        }
        userLine = null
        reply = null
    }

    fun userSaid(text: String, final: Boolean) {
        val line = userLine ?: addUserLine(text, final).also { userLine = it }
        line.text = text
        line.alpha = if (final) 0.75f else 1f
        if (final) userLine = null
        follow()
    }

    fun startReply() {
        userLine = null
        reply = newReply()
        follow()
    }

    fun replySentence(text: String) {
        val k = reply ?: newReply().also { reply = it }
        k.append(text)
        follow()
    }

    fun endReply() {
        reply?.lightAll()
        reply = null
    }

    /** Keep the line holding [word] of the current reply visible while it is being read. */
    fun revealWord(word: Int) {
        if (!following) return
        val k = reply ?: return
        val bottom = k.top + k.lineBottomOf(word) + column.paddingTop
        val visibleBottom = scrollY + height - paddingBottom
        if (bottom > visibleBottom) smoothScrollTo(0, bottom - height + dp(24))
    }

    private fun addUserLine(text: String, final: Boolean): TextView {
        val t = TextView(context)
        t.typeface = Fonts.regular
        t.text = text
        t.alpha = if (final) 0.75f else 1f
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        val theme = context.appTheme
        t.setTextColor(theme.textPrimary)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        t.background = Shapes.rounded(theme.userBubble, dp(theme.radiusL))
        val lp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.END
        lp.topMargin = dp(14)
        lp.marginStart = dp(48)
        column.addView(t, lp)
        return t
    }

    private fun newReply(): KaraokeTextView {
        val k = KaraokeTextView(context)
        k.setPadding(0, dp(12), dp(8), 0)
        column.addView(k, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return k
    }

    private fun follow() {
        if (!following) return
        post { fullScroll(FOCUS_DOWN) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) following = false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val atBottom = t + height >= column.height - dp(8)
        if (atBottom) following = true
    }

    override fun onThemeChanged(theme: Theme) {
        for (i in 0 until column.childCount) {
            val v = column.getChildAt(i)
            if (v is TextView) {
                v.setTextColor(theme.textPrimary)
                v.background = Shapes.rounded(theme.userBubble, dp(theme.radiusL))
            }
            if (v is KaraokeTextView) v.onThemeChanged(theme)
        }
    }
}
