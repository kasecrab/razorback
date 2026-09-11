package io.github.kasecrab.razorback.voice

/**
 * How fast the assistant talks. Aura itself goes up to 1.5x; anything past that is time
 * stretching on the phone, pitch kept, so 3x is Aura at 1.5x played twice as fast.
 */
object Speed {
    const val MIN = 0.7f
    const val MAX = 3f
    const val SERVER_MAX = 1.5f

    /** The part Aura renders. */
    fun server(total: Float): Float = total.coerceIn(MIN, SERVER_MAX)

    /** The remaining factor the player applies, 1 when Aura covers it all. */
    fun stretch(total: Float): Float = (total.coerceIn(MIN, MAX) / server(total)).coerceAtLeast(1f)
}
