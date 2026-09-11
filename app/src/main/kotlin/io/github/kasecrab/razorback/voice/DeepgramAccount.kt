package io.github.kasecrab.razorback.voice

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.HttpException
import io.github.kasecrab.razorback.core.arr
import io.github.kasecrab.razorback.core.dbl
import io.github.kasecrab.razorback.core.forEachObject
import io.github.kasecrab.razorback.core.str
import java.util.Locale

/** Proves a Deepgram key works and, when the key is allowed to see it, reads the balance. */
object DeepgramAccount {
    /** What every Deepgram link reports when the service turns the key away. */
    const val KEY_REFUSED = "Deepgram refused the key"


    fun check(key: String): String {
        val headers = mapOf("Authorization" to "Token $key")
        val projects = Http.getJson("https://api.deepgram.com/v1/projects", headers)
        var name: String? = null
        var id: String? = null
        projects.arr("projects")?.forEachObject {
            if (id == null) {
                id = it.str("project_id")
                name = it.str("name")
            }
        }
        val sb = StringBuilder("Key ok")
        name?.let { sb.append(" · ").append(it) }
        val pid = id ?: return sb.toString()
        try {
            var total = 0.0
            Http.getJson("https://api.deepgram.com/v1/projects/$pid/balances", headers).arr("balances")?.forEachObject {
                total += it.dbl("amount") ?: 0.0
            }
            sb.append(" · balance ").append(String.format(Locale.US, "$%.2f", total))
        } catch (_: HttpException) {
            // Most keys may not read billing; the key itself is still fine.
        }
        return sb.toString()
    }
}
