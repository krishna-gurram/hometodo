package dev.krishnagurram.hometodo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When the daily "here's what's due" notification fires:
 * weekdays at 5:30 pm (you're home from work), weekends at 9:00 am.
 */
object ReminderTime {

    val WEEKDAY: LocalTime = LocalTime.of(17, 30)
    val WEEKEND: LocalTime = LocalTime.of(9, 0)

    fun timeOn(date: LocalDate): LocalTime =
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) WEEKEND else WEEKDAY

    /** The next reminder strictly after `now`: later today if it hasn't passed, else tomorrow. */
    fun next(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        val laterToday = today.atTime(timeOn(today))
        if (laterToday.isAfter(now)) return laterToday
        val tomorrow = today.plusDays(1)
        return tomorrow.atTime(timeOn(tomorrow))
    }
}
