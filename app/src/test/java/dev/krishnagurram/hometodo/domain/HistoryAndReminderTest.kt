package dev.krishnagurram.hometodo.domain

import dev.krishnagurram.hometodo.data.ChoreDoneRow
import dev.krishnagurram.hometodo.data.PurchaseRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class HistoryAndReminderTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 28)

    private fun millis(date: LocalDate, hour: Int) = date.atTime(hour, 0).toInstant(zone).toEpochMilli()

    @Test
    fun `purchases at one store on one day become one trip`() {
        val purchases = listOf(
            PurchaseRow("p1", "Rice", "Indian", "#B45309", millis(today, 10)),
            PurchaseRow("p2", "Ghee", "Indian", "#B45309", millis(today, 11)),
            PurchaseRow("p3", "Eggs", "Costco", "#475569", millis(today, 12)),
        )
        val days = HistoryBuilder.build(emptyList(), purchases, HistoryFilter.ALL, zone, today)
        assertEquals(1, days.size)
        assertEquals("Today", days.single().label)
        val trips = days.single().entries.filterIsInstance<HistoryEntry.TripEntry>()
        assertEquals(listOf("Costco", "Indian"), trips.map { it.storeName })
        assertEquals(listOf("Ghee", "Rice"), trips.last().itemNames)
    }

    @Test
    fun `days are newest first with friendly labels`() {
        val chores = listOf(
            ChoreDoneRow("c1", "Trash", millis(today.minusDays(1), 19)),
            ChoreDoneRow("c2", "Filter", millis(today.minusDays(5), 9)),
        )
        val days = HistoryBuilder.build(chores, emptyList(), HistoryFilter.ALL, zone, today)
        assertEquals("Yesterday", days[0].label)
        assertTrue(days[1].label.isNotEmpty() && days[1].label != "Today")
    }

    @Test
    fun `chores filter hides grocery trips`() {
        val days = HistoryBuilder.build(
            listOf(ChoreDoneRow("c1", "Trash", millis(today, 19))),
            listOf(PurchaseRow("p1", "Rice", "Indian", null, millis(today, 10))),
            HistoryFilter.CHORES,
            zone,
            today,
        )
        assertTrue(days.single().entries.all { it is HistoryEntry.ChoreEntry })
    }

    @Test
    fun `weekday reminder is 5 30 pm, later the same day if not yet passed`() {
        val wednesdayNoon = LocalDateTime.of(2026, 10, 28, 12, 0)
        assertEquals(LocalDateTime.of(2026, 10, 28, 17, 30), ReminderTime.next(wednesdayNoon))
    }

    @Test
    fun `friday evening after the reminder rolls to saturday morning`() {
        val fridayNight = LocalDateTime.of(2026, 10, 30, 20, 0)
        assertEquals(LocalDateTime.of(2026, 10, 31, 9, 0), ReminderTime.next(fridayNight))
    }
}
