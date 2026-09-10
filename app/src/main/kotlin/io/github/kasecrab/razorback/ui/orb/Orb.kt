package io.github.kasecrab.razorback.ui.orb

import android.graphics.Canvas
import io.github.kasecrab.razorback.ui.core.Theme

/**
 * One way of drawing the voice orb. Drawn every frame while visible with the current
 * time, the smoothed mic and speaker levels, and the session state. Nothing may be
 * allocated inside [draw].
 */
interface Orb {
    val id: String
    val name: String

    /** Frames per second while nothing is happening; the view raises it when levels move. */
    val idleFps: Int get() = 30

    fun onAttach() {}
    fun onDetach() {}

    fun draw(canvas: Canvas, w: Int, h: Int, t: Float, inLevel: Float, outLevel: Float, state: Int, theme: Theme)

    companion object {
        const val IDLE = 0
        const val LISTENING = 1
        const val USER_SPEAKING = 2
        const val THINKING = 3
        const val SPEAKING = 4
    }
}

object Orbs {
    val all: List<Orb> by lazy { listOf(SolOrb(), RingOrb(), NebulaOrb(), PulseOrb(), EclipseOrb(), ReactorOrb(), LatticeOrb()) }

    fun byId(id: String): Orb = all.firstOrNull { it.id == id } ?: all[0]
}
