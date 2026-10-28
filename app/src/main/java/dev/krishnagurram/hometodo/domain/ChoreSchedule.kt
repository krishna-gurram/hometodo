package dev.krishnagurram.hometodo.domain

import dev.krishnagurram.hometodo.data.Chore
import dev.krishnagurram.hometodo.data.ChoreWhen
import dev.krishnagurram.hometodo.data.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Pure logic: no database, no Android, no UI. Give it a chore and a date, get an
// answer back. That's what makes it easy to unit test (see ChoreScheduleTest),
// and it's why "today" is a parameter instead of calling LocalDate.now() in here.

enum class Section { TODAY, COMING_UP }

/** A chore plus everything the snapshot needs to draw it. */
data class ChoreRow(
    val chore: Chore,
    val section: Section,
    val whenLabel: String,
    /** Due within 3 days or overdue. Drawn in amber. */
    val urgent: Boolean,
)

object ChoreSchedule {

    private val shortDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

    fun classify(chore: Chore, today: LocalDate): ChoreRow {
        val due = chore.dueEpochDay?.let(LocalDate::ofEpochDay)
        val row = if (due != null) byDate(chore, due, today) else byRhythm(chore, today)
        return row.copy(whenLabel = row.whenLabel + repeatSuffix(chore.repeat))
    }

    /**
     * For a repeating chore you just finished: the next due date that's strictly
     * after today. Null means "this chore is finished for good."
     *
     * Undated chores never repeat on their own. A repeating chore always has a date
     * (the Add chore screen will enforce that in milestone 5).
     */
    fun nextDue(chore: Chore, today: LocalDate): LocalDate? {
        val due = chore.dueEpochDay?.let(LocalDate::ofEpochDay) ?: return null
        if (chore.repeat == Repeat.NONE) return null
        var next = step(due, chore.repeat)
        // Done late? Skip past every missed occurrence instead of landing in the past.
        while (!next.isAfter(today)) {
            next = step(next, chore.repeat)
        }
        return next
    }

    private fun byDate(chore: Chore, due: LocalDate, today: LocalDate): ChoreRow {
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            days < 0 -> ChoreRow(chore, Section.TODAY, "Overdue · was due ${shortDate.format(due)}", urgent = true)
            days == 0L && chore.whenType == ChoreWhen.WEEKNIGHT -> ChoreRow(chore, Section.TODAY, "Tonight", urgent = false)
            days == 0L -> ChoreRow(chore, Section.TODAY, "Due today", urgent = true)
            days == 1L -> ChoreRow(chore, Section.COMING_UP, "Tomorrow", urgent = true)
            else -> ChoreRow(
                chore,
                Section.COMING_UP,
                "${shortDate.format(due)} · $days days left",
                urgent = days <= 3,
            )
        }
    }

    private fun byRhythm(chore: Chore, today: LocalDate): ChoreRow {
        val isWeekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
        return when (chore.whenType) {
            ChoreWhen.WEEKNIGHT ->
                if (isWeekend) ChoreRow(chore, Section.COMING_UP, "Next weeknight", urgent = false)
                else ChoreRow(chore, Section.TODAY, "Tonight", urgent = false)
            ChoreWhen.WEEKEND ->
                ChoreRow(chore, if (isWeekend) Section.TODAY else Section.COMING_UP, "This weekend", urgent = false)
            ChoreWhen.DATE ->
                ChoreRow(chore, Section.COMING_UP, "No date set", urgent = false)
        }
    }

    private fun step(date: LocalDate, repeat: Repeat): LocalDate = when (repeat) {
        Repeat.NONE -> date
        Repeat.WEEKLY -> date.plusWeeks(1)
        Repeat.BIWEEKLY -> date.plusWeeks(2)
        Repeat.MONTHLY -> date.plusMonths(1)
    }

    private fun repeatSuffix(repeat: Repeat): String = when (repeat) {
        Repeat.NONE -> ""
        Repeat.WEEKLY -> " · every week"
        Repeat.BIWEEKLY -> " · every 2 weeks"
        Repeat.MONTHLY -> " · every month"
    }
}
