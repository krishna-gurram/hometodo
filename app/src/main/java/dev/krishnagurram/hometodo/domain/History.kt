package dev.krishnagurram.hometodo.domain

import dev.krishnagurram.hometodo.data.ChoreDoneRow
import dev.krishnagurram.hometodo.data.PurchaseRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class HistoryFilter { ALL, CHORES, GROCERIES }

/**
 * A history row is one of two things. `sealed` means "only these two kinds exist",
 * so a `when` over it must handle both (the compiler checks). Like a TypeScript
 * discriminated union: type HistoryEntry = ChoreEntry | TripEntry.
 */
sealed interface HistoryEntry {
    val at: Long

    data class ChoreEntry(val id: String, val title: String, val timeLabel: String, override val at: Long) : HistoryEntry

    /** Every item bought at one store on one day, collapsed into a single "trip" row. */
    data class TripEntry(
        val key: String,
        val storeName: String,
        val colorHex: String?,
        val itemNames: List<String>,
        override val at: Long,
    ) : HistoryEntry
}

data class HistoryDay(val label: String, val entries: List<HistoryEntry>)

object HistoryBuilder {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun build(
        chores: List<ChoreDoneRow>,
        purchases: List<PurchaseRow>,
        filter: HistoryFilter,
        zone: ZoneId,
        today: LocalDate,
    ): List<HistoryDay> {
        fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

        val choreEntries: List<Pair<LocalDate, HistoryEntry>> =
            if (filter == HistoryFilter.GROCERIES) emptyList()
            else chores.map { row ->
                val time = Instant.ofEpochMilli(row.completedAt).atZone(zone).toLocalTime()
                dateOf(row.completedAt) to HistoryEntry.ChoreEntry(row.id, row.title, timeFormat.format(time), row.completedAt)
            }

        val tripEntries: List<Pair<LocalDate, HistoryEntry>> =
            if (filter == HistoryFilter.CHORES) emptyList()
            else purchases
                .groupBy { dateOf(it.boughtAt) to (it.storeName ?: "Deleted store") }
                .map { (key, rows) ->
                    val (date, storeName) = key
                    val newestFirst = rows.sortedByDescending { it.boughtAt }
                    date to HistoryEntry.TripEntry(
                        key = "$date-$storeName",
                        storeName = storeName,
                        colorHex = rows.first().colorHex,
                        itemNames = newestFirst.map { it.itemName },
                        at = newestFirst.first().boughtAt,
                    )
                }

        return (choreEntries + tripEntries)
            .groupBy({ it.first }, { it.second })
            .toSortedMap(reverseOrder()) // newest day first
            .map { (date, entries) -> HistoryDay(labelFor(date, today), entries.sortedByDescending { it.at }) }
    }

    fun labelFor(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayFormat.format(date)
    }
}
