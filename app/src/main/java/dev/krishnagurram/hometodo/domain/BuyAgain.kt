package dev.krishnagurram.hometodo.domain

import dev.krishnagurram.hometodo.data.PurchaseTime

/** One "probably due" chip: an item you buy regularly that's about due again. */
data class DueSuggestion(val itemId: String, val name: String, val everyDays: Int)

/**
 * "Probably due" is a guess from your own receipts, nothing smarter:
 *   - only items bought at least 3 times (2 purchases is one gap, too noisy)
 *   - average gap between purchases = (last - first) / (purchases - 1)
 *   - suggest it once 85% of that gap has passed since the last purchase
 *   - stop suggesting after 6 months without buying it (you've moved on)
 * Most-overdue first.
 */
object BuyAgain {

    const val MIN_PURCHASES = 3
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val DUE_AT = 0.85
    private const val DORMANT_AFTER_MS = 180 * DAY_MS

    fun probablyDue(purchases: List<PurchaseTime>, nowMillis: Long, limit: Int = 6): List<DueSuggestion> =
        purchases
            .groupBy { it.itemId }
            .mapNotNull { (itemId, rows) -> score(itemId, rows, nowMillis) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

    /** Returns the suggestion and how overdue it is (1.0 = exactly one average gap), or null. */
    private fun score(itemId: String, rows: List<PurchaseTime>, nowMillis: Long): Pair<DueSuggestion, Double>? {
        if (rows.size < MIN_PURCHASES) return null
        val times = rows.map { it.boughtAt }.sorted()
        val averageGap = (times.last() - times.first()) / (times.size - 1)
        if (averageGap <= 0) return null
        val sinceLast = nowMillis - times.last()
        if (sinceLast > DORMANT_AFTER_MS) return null
        val overdue = sinceLast.toDouble() / averageGap
        if (overdue < DUE_AT) return null
        val everyDays = (averageGap / DAY_MS).toInt().coerceAtLeast(1)
        return DueSuggestion(itemId, rows.first().name, everyDays) to overdue
    }
}
