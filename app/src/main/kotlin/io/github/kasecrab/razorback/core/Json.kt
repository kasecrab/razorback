package io.github.kasecrab.razorback.core

import org.json.JSONArray
import org.json.JSONObject

/*
 * Thin helpers over the framework org.json so call sites read like typed access.
 * optString returns the literal "null" for JSON null, hence the isNull checks.
 */

fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)

fun JSONObject.dbl(key: String): Double? = when (val v = opt(key)) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

fun JSONObject.long(key: String): Long? = when (val v = opt(key)) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull()
    else -> null
}

fun JSONObject.int(key: String): Int? = long(key)?.toInt()

fun JSONObject.bool(key: String): Boolean? = when (val v = opt(key)) {
    is Boolean -> v
    is String -> v.toBooleanStrictOrNull()
    else -> null
}

inline fun jsonObject(build: JSONObject.() -> Unit): JSONObject = JSONObject().apply(build)

fun jsonArrayOf(vararg items: Any?): JSONArray = JSONArray().apply { for (i in items) put(i) }

inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        block(o)
    }
}

fun JSONArray.strings(): List<String> = List(length()) { optString(it) }
