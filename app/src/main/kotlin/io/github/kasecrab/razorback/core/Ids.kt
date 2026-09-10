package io.github.kasecrab.razorback.core

import java.security.SecureRandom

/** Time-ordered ids: 12 hex digits of milliseconds then 8 random hex digits. */
object Ids {
    private val random = SecureRandom()
    private const val HEX = "0123456789abcdef"

    fun next(): String {
        val sb = StringBuilder(20)
        val ms = System.currentTimeMillis()
        for (i in 11 downTo 0) sb.append(HEX[((ms ushr (i * 4)) and 0xF).toInt()])
        for (i in 0 until 8) sb.append(HEX[random.nextInt(16)])
        return sb.toString()
    }
}
