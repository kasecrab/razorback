package io.github.kasecrab.razorback.core

import java.util.Locale

object Fmt {
    fun money(v: Double): String = when {
        v == 0.0 -> "$0"
        v < 0.01 -> String.format(Locale.US, "$%.4f", v)
        v < 1.0 -> String.format(Locale.US, "$%.3f", v)
        else -> String.format(Locale.US, "$%.2f", v)
    }

    /** Price per million tokens as people quote it: $0.15, $3, $15. */
    fun perM(v: Double): String = when {
        v == 0.0 -> "free"
        v < 1.0 -> String.format(Locale.US, "$%.2f", v).trimEnd('0').trimEnd('.')
        else -> String.format(Locale.US, "$%.1f", v).trimEnd('0').trimEnd('.')
    }

    fun tokens(n: Long): String = when {
        n < 1000 -> n.toString()
        n < 1_000_000 -> String.format(Locale.US, "%.1fk", n / 1000.0).replace(".0k", "k")
        else -> String.format(Locale.US, "%.1fm", n / 1_000_000.0).replace(".0m", "m")
    }

    fun context(n: Int): String = if (n >= 1000) "${n / 1000}k" else n.toString()

    fun duration(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
            s < 3600 -> "${s / 60}m ${s % 60}s"
            else -> "${s / 3600}h ${(s % 3600) / 60}m"
        }
    }
}
