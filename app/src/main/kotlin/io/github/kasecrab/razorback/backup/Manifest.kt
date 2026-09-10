package io.github.kasecrab.razorback.backup

import io.github.kasecrab.razorback.core.int
import io.github.kasecrab.razorback.core.jsonObject
import io.github.kasecrab.razorback.core.long
import io.github.kasecrab.razorback.core.str
import org.json.JSONObject

class Manifest(val format: Int, val appVersion: String, val createdAt: Long, val counts: Map<String, Int>) {

    fun toJson(): JSONObject = jsonObject {
        put("format", format)
        put("app", "razorback")
        put("app_version", appVersion)
        put("created_at", createdAt)
        put("counts", jsonObject { for ((k, v) in counts) put(k, v) })
    }

    companion object {
        const val FORMAT = 1

        fun parse(json: JSONObject): Manifest {
            require(json.str("app") == "razorback") { "not a Razorback backup" }
            val format = json.int("format") ?: 0
            require(format in 1..FORMAT) { "backup format $format is newer than this app" }
            val counts = HashMap<String, Int>()
            json.optJSONObject("counts")?.let { c -> for (k in c.keys()) counts[k] = c.optInt(k) }
            return Manifest(format, json.str("app_version") ?: "", json.long("created_at") ?: 0L, counts)
        }
    }
}
