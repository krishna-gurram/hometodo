package dev.krishnagurram.hometodo.domain

import dev.krishnagurram.hometodo.data.Chore
import dev.krishnagurram.hometodo.data.ChoreWhen
import dev.krishnagurram.hometodo.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Plain JUnit 4, exactly like Java. These run on your laptop's JVM (and on CI),
// no phone or emulator needed, because ChoreSchedule has no Android code in it.
// Kotlin lets test names be sentences when wrapped in backticks.

class ChoreScheduleTest {

    private val tuesday = LocalDate.of(2026, 10, 27)
    private val saturday = LocalDate.of(2026, 10, 31)

    private fun chore(
        whenType: ChoreWhen,
        due: LocalDate? = null,
        repeat: Repeat = Repeat.NONE,
    ) = Chore(title = "Test", whenType = whenType, dueEpochDay = due?.toEpochDay(), repeat = repeat)

    @Test
    fun `weeknight chore with no date shows tonight on a weekday`() {
        val row = ChoreSchedule.classify(chore(ChoreWhen.WEEKNIGHT), tuesday)
        assertEquals(Section.TODAY, row.section)
        assertEquals("Tonight", row.whenLabel)
    }

    @Test
    fun `weeknight chore waits for Monday on a weekend`() {
        val row = ChoreSchedule.classify(chore(ChoreWhen.WEEKNIGHT), saturday)
        assertEquals(Section.COMING_UP, row.section)
    }

    @Test
    fun `weekend chore moves into today on Saturday`() {
        assertEquals(Section.COMING_UP, ChoreSchedule.classify(chore(ChoreWhen.WEEKEND), tuesday).section)
        assertEquals(Section.TODAY, ChoreSchedule.classify(chore(ChoreWhen.WEEKEND), saturday).section)
    }

    @Test
    fun `dated chore three days out is coming up and urgent`() {
        val row = ChoreSchedule.classify(chore(ChoreWhen.DATE, due = tuesday.plusDays(3)), tuesday)
        assertEquals(Section.COMING_UP, row.section)
        assertTrue(row.urgent)
        assertTrue(row.whenLabel.endsWith("3 days left"))
    }

    @Test
    fun `overdue chore stays in today`() {
        val row = ChoreSchedule.classify(chore(ChoreWhen.DATE, due = tuesday.minusDays(2)), tuesday)
        assertEquals(Section.TODAY, row.section)
        assertTrue(row.whenLabel.startsWith("Overdue"))
    }

    @Test
    fun `one-off chore has no next date`() {
        assertNull(ChoreSchedule.nextDue(chore(ChoreWhen.DATE, due = tuesday), tuesday))
    }

    @Test
    fun `weekly chore done on time moves one week`() {
        val next = ChoreSchedule.nextDue(chore(ChoreWhen.WEEKNIGHT, due = tuesday, repeat = Repeat.WEEKLY), tuesday)
        assertEquals(tuesday.plusWeeks(1), next)
    }

    @Test
    fun `weekly chore done 10 days late skips missed weeks`() {
        val due = tuesday.minusDays(10)
        val next = ChoreSchedule.nextDue(chore(ChoreWhen.WEEKNIGHT, due = due, repeat = Repeat.WEEKLY), tuesday)
        assertEquals(due.plusWeeks(2), next)
        assertTrue(next!!.isAfter(tuesday))
    }

    /**
     * Known behavior, written down on purpose: Jan 31 + 1 month = Feb 28, and the
     * chore stays on the 28th from then on. See lesson 2, question 3.
     */
    @Test
    fun `monthly chore on the 31st drifts to the 28th`() {
        val jan31 = LocalDate.of(2027, 1, 31)
        val next = ChoreSchedule.nextDue(chore(ChoreWhen.DATE, due = jan31, repeat = Repeat.MONTHLY), jan31)
        assertEquals(LocalDate.of(2027, 2, 28), next)
    }
}
