package io.github.kasecrab.razorback.ui.core

import android.content.Context
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.core.Keys

/**
 * Every vibration the app makes goes through here, so the feel is one vocabulary and one
 * switch turns it all off. It drives the vibrator itself rather than asking the view
 * system: the system's touch-feedback switch silences view haptics and is off on many
 * phones, so an app's own cues would vanish with it. The cues are filed as media
 * vibration, the class an app's content uses, which the person controls separately.
 * Tappable widgets give a light tick on their own; a screen that has something more to
 * say about a touch says it first, and the widget's tick then stays quiet.
 */
object Haptics {

    private val vibrator: Vibrator by lazy {
        (App.instance.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }
    private val attributes: VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA)

    private val tickEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    private val tapEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    private val heavyEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    /** A light touch then a firm one: something landed. */
    private val confirmEffect = VibrationEffect.createWaveform(longArrayOf(0, 12, 70, 30), intArrayOf(0, 110, 0, 255), -1)
    /** Three short shakes of the head. */
    private val rejectEffect = VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 40, 50, 40), intArrayOf(0, 200, 0, 200, 0, 200), -1)
    private val settleEffect = VibrationEffect.createOneShot(18, 160)
    private val toggleOnEffect = VibrationEffect.createWaveform(longArrayOf(0, 10, 40, 16), intArrayOf(0, 90, 0, 220), -1)
    private val toggleOffEffect = VibrationEffect.createWaveform(longArrayOf(0, 16, 40, 10), intArrayOf(0, 220, 0, 90), -1)
    /** The faintest flutter, for text as it writes itself. */
    private val streamEffect = VibrationEffect.createOneShot(10, 70)

    private var strongAt = 0L
    private var streamAt = 0L

    private val enabled: Boolean get() = App.instance.prefs[Keys.HAPTICS] && vibrator.hasVibrator()

    /** The lightest touch: a tap on something, a step, a choice among equals. */
    fun tick() = play(tickEffect, weak = true)

    /** A tap that does something. */
    fun tap() = play(tapEffect)

    /** Something landed: sent, saved, chosen, kept. */
    fun confirm() = play(confirmEffect)

    /** Something was refused or went wrong. */
    fun reject() = play(rejectEffect)

    /** A switch or star flipped. */
    fun toggle(on: Boolean) = play(if (on) toggleOnEffect else toggleOffEffect)

    /** Something that cannot be taken back: a delete, a hang-up. */
    fun heavy() = play(heavyEffect)

    /** A gesture reached its end: the sidebar snapped, a sheet was flicked away. */
    fun settle() = play(settleEffect)

    /** Text is arriving: a flutter under the words, never more than a few times a second. */
    fun stream() {
        if (!enabled || !App.instance.prefs[Keys.HAPTICS_STREAM]) return
        val now = SystemClock.uptimeMillis()
        if (now - streamAt < STREAM_GAP_MS) return
        streamAt = now
        vibrator.vibrate(streamEffect, attributes)
    }

    private fun play(effect: VibrationEffect, weak: Boolean = false) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        // A widget's own tick right after a screen already answered the same touch is noise.
        if (weak && now - strongAt < ECHO_MS) return
        if (!weak) strongAt = now
        vibrator.vibrate(effect, attributes)
    }

    private const val ECHO_MS = 120L
    private const val STREAM_GAP_MS = 90L
}
