package io.github.kasecrab.razorback.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Numbers from usage_log for the stats screen. */
class Stats(private val db: Db) {

    class Totals(val requests: Int, val promptTokens: Long, val completionTokens: Long, val reasoningTokens: Long, val cost: Double, val avgTtftMs: Long, val avgLatencyMs: Long, val failures: Int)

    class ModelShare(val model: String, val requests: Int, val cost: Double, val tokens: Long)

    class Report(val allTime: Totals, val week: Totals, val month: Totals, val dailyCost: DoubleArray, val topModels: List<ModelShare>)

    suspend fun report(): Report = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val all = totals(0)
        val week = totals(now - 7 * day)
        val month = totals(now - 30 * day)
        val daily = DoubleArray(30)
        db.readableDatabase.rawQuery(
            "SELECT (ts / ?) AS d, SUM(cost) FROM usage_log WHERE ts >= ? GROUP BY d",
            arrayOf(day.toString(), (now - 30 * day).toString()),
        ).use { c ->
            val today = now / day
            while (c.moveToNext()) {
                val idx = 29 - (today - c.getLong(0)).toInt()
                if (idx in 0..29) daily[idx] = c.getDouble(1)
            }
        }
        val top = ArrayList<ModelShare>()
        db.readableDatabase.rawQuery(
            "SELECT model, COUNT(*), SUM(cost), SUM(prompt_tokens + completion_tokens) FROM usage_log WHERE ts >= ? GROUP BY model ORDER BY SUM(cost) DESC, COUNT(*) DESC LIMIT 8",
            arrayOf((now - 30 * day).toString()),
        ).use { c ->
            while (c.moveToNext()) top.add(ModelShare(c.getString(0), c.getInt(1), c.getDouble(2), c.getLong(3)))
        }
        Report(all, week, month, daily, top)
    }

    private fun totals(since: Long): Totals =
        db.readableDatabase.rawQuery(
            """SELECT COUNT(*), COALESCE(SUM(prompt_tokens),0), COALESCE(SUM(completion_tokens),0), COALESCE(SUM(reasoning_tokens),0),
               COALESCE(SUM(cost),0), COALESCE(AVG(ttft_ms),0), COALESCE(AVG(latency_ms),0), COALESCE(SUM(CASE WHEN ok=0 THEN 1 ELSE 0 END),0)
               FROM usage_log WHERE ts >= ?""",
            arrayOf(since.toString()),
        ).use { c ->
            c.moveToFirst()
            Totals(c.getInt(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getDouble(4), c.getLong(5), c.getLong(6), c.getInt(7))
        }
}
