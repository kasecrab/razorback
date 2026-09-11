package io.github.kasecrab.razorback.ui.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.Shapes
import io.github.kasecrab.razorback.voice.Voice
import io.github.kasecrab.razorback.voice.VoiceCatalog
import kotlin.math.abs

/** One voice on a card: its language, a disc that plays and pulses with the sample, and who it sounds like. */
class VoiceCard(context: Context) : LinearLayout(context), Themed {

    val disc = VoiceDisc(context)
    var voice: Voice? = null
        private set

    private val language = TextView(context)
    private val name = TextView(context)
    private val meta = TextView(context)
    private val traits = TextView(context)
    private val badge = Chip(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        setPadding(dp(16), dp(16), dp(16), dp(16))
        language.typeface = Fonts.medium
        language.letterSpacing = 0.08f
        language.isAllCaps = true
        name.typeface = Fonts.medium
        name.maxLines = 1
        meta.typeface = Fonts.regular
        meta.maxLines = 1
        traits.typeface = Fonts.regular
        traits.gravity = Gravity.CENTER_HORIZONTAL
        traits.setLines(2)
        badge.style = Chip.Style.PLAIN
        badge.active = true
        badge.leadingIcon = R.drawable.ic_check
        badge.setText(R.string.voice_in_use)
        disc.contentDescription = context.getString(R.string.cd_play_sample)
        addView(language, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(disc, LayoutParams(dp(100), dp(100)).apply { topMargin = dp(12); bottomMargin = dp(12) })
        addView(name, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(meta, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
        addView(traits, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        onThemeChanged(context.appTheme)
    }

    fun bind(v: Voice, inUse: Boolean) {
        voice = v
        val lang = VoiceCatalog.languageName(v.language)
        language.text = if (v.architecture.isEmpty() || v.architecture == "aura-2") lang else "$lang · ${v.architecture}"
        name.text = v.name
        val who = when (v.feminine) {
            true -> context.getString(R.string.voice_f, v.accent)
            false -> context.getString(R.string.voice_m, v.accent)
            null -> v.accent
        }
        meta.text = if (v.age.isNotEmpty() && v.age != "Adult") "$who · ${v.age}" else who
        traits.text = v.traits
        badge.visibility = if (inUse) View.VISIBLE else View.INVISIBLE
        disc.hue = if (v.color != 0) hueOf(v.color) else hueOf(v.id)
    }

    override fun onThemeChanged(theme: Theme) {
        background = Shapes.rounded(theme.surface, dp(theme.radiusXl), dp(1), theme.outline)
        language.setTextColor(theme.accent)
        language.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        name.setTextColor(theme.textPrimary)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        meta.setTextColor(theme.textSecondary)
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        traits.setTextColor(theme.textTertiary)
        traits.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
    }

    private fun hueOf(id: String): Float {
        var h = 0
        for (c in id) h = h * 31 + c.code
        return (abs(h) % 360).toFloat()
    }

    private fun hueOf(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }
}

/** A tinted disc that is the sample's play button; it breathes with the voice while it speaks. */
class VoiceDisc(context: Context) : View(context), Themed, Choreographer.FrameCallback {

    enum class State { IDLE, LOADING, PLAYING }

    var state: State = State.IDLE
        set(value) {
            if (field == value) return
            field = value
            shown = 0f
            sweep = 0f
            invalidate()
            if (value != State.IDLE) tick() else Choreographer.getInstance().removeFrameCallback(this)
        }

    /** Where the pulse reads its size from while playing. */
    var level: () -> Float = { 0f }

    var hue = 200f
        set(value) {
            field = value
            paint(context.appTheme)
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arc = RectF()
    private val hsv = FloatArray(3)
    private var play: Drawable? = null
    private var stop: Drawable? = null
    private var shown = 0f
    private var sweep = 0f
    private var ticking = false

    init {
        isClickable = true
        isFocusable = true
        onThemeChanged(context.appTheme)
    }

    override fun onThemeChanged(theme: Theme) {
        paint(theme)
        foreground = Shapes.circleRipple(theme.accentSoft)
        invalidate()
    }

    private fun paint(theme: Theme) {
        hsv[0] = hue
        hsv[1] = if (theme.isLight) 0.38f else 0.5f
        hsv[2] = if (theme.isLight) 0.96f else 0.5f
        fill.color = Color.HSVToColor(hsv)
        hsv[1] = if (theme.isLight) 0.6f else 0.45f
        hsv[2] = if (theme.isLight) 0.45f else 0.95f
        val ink = Color.HSVToColor(hsv)
        ring.color = ink
        ring.strokeWidth = dp(2f)
        play = context.icon(R.drawable.ic_waveform, ink)
        stop = context.icon(R.drawable.ic_stop, ink)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (state != State.IDLE) tick()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        ticking = false
        super.onDetachedFromWindow()
    }

    private fun tick() {
        if (ticking || !isAttachedToWindow) return
        ticking = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        ticking = false
        if (state == State.IDLE) return
        if (state == State.PLAYING) {
            val target = level()
            shown += (target - shown) * if (target > shown) 0.5f else 0.12f
        } else {
            sweep += 6f
        }
        invalidate()
        tick()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val inset = dp(10f)
        val r = minOf(cx, cy) - inset
        canvas.drawCircle(cx, cy, r, fill)
        when (state) {
            State.PLAYING -> {
                ring.alpha = (200 * (0.35f + 0.65f * shown)).toInt()
                canvas.drawCircle(cx, cy, r + dp(3f) + inset * 0.6f * shown, ring)
            }
            State.LOADING -> {
                ring.alpha = 200
                arc.set(cx - r - dp(4f), cy - r - dp(4f), cx + r + dp(4f), cy + r + dp(4f))
                canvas.drawArc(arc, sweep, 90f, false, ring)
            }
            State.IDLE -> {}
        }
        val d = if (state == State.PLAYING) stop else if (state == State.IDLE) play else null
        if (d != null) {
            val half = dp(14)
            d.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            d.draw(canvas)
        }
    }
}
