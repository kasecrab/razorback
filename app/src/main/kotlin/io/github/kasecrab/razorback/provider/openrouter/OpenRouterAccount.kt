package io.github.kasecrab.razorback.provider.openrouter

import io.github.kasecrab.razorback.core.Http
import io.github.kasecrab.razorback.core.HttpException
import io.github.kasecrab.razorback.core.bool
import io.github.kasecrab.razorback.core.dbl
import io.github.kasecrab.razorback.core.obj
import io.github.kasecrab.razorback.core.str
import java.io.IOException

class AccountInfo(
    val label: String?,
    val usage: Double,
    val usageDaily: Double,
    val usageWeekly: Double,
    val usageMonthly: Double,
    val limit: Double?,
    val limitRemaining: Double?,
    val isFreeTier: Boolean,
    val totalCredits: Double?,
    val totalUsage: Double?,
) {
    val balance: Double? get() = if (totalCredits != null && totalUsage != null) totalCredits - totalUsage else null
}

/** GET /key for the key's own spend, GET /credits for the account balance (best effort). */
object OpenRouterAccount {

    fun fetch(key: String): AccountInfo {
        val h = OpenRouter.headers(key)
        // /key is the documented path; older deployments and the mock server answer at /auth/key.
        val k = try {
            Http.getJson("${OpenRouter.BASE}/key", h)
        } catch (e: HttpException) {
            if (e.status == 404 || e.status == 405) Http.getJson("${OpenRouter.BASE}/auth/key", h) else throw e
        }.let { it.obj("data") ?: it }
        var credits: Double? = null
        var used: Double? = null
        try {
            val c = Http.getJson("${OpenRouter.BASE}/credits", h).let { it.obj("data") ?: it }
            credits = c.dbl("total_credits")
            used = c.dbl("total_usage")
        } catch (_: IOException) {
            // Some keys cannot read credits; the key's own numbers are still worth showing.
        }
        return AccountInfo(
            label = k.str("label"),
            usage = k.dbl("usage") ?: 0.0,
            usageDaily = k.dbl("usage_daily") ?: 0.0,
            usageWeekly = k.dbl("usage_weekly") ?: 0.0,
            usageMonthly = k.dbl("usage_monthly") ?: 0.0,
            limit = k.dbl("limit"),
            limitRemaining = k.dbl("limit_remaining"),
            isFreeTier = k.bool("is_free_tier") ?: false,
            totalCredits = credits,
            totalUsage = used,
        )
    }
}
