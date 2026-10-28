package dev.krishnagurram.hometodo.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Temporary: fills an empty app with realistic chores and groceries so the
 * snapshot has something to show before the Add screens exist (milestones 3 to 5).
 * Delete this file once you're entering real data.
 */
object SampleData {

    suspend fun load(db: AppDatabase, today: LocalDate) {
        val chores = db.choreDao()
        val nextTuesday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))

        chores.upsert(
            Chore(
                title = "Trash bins out",
                whenType = ChoreWhen.WEEKNIGHT,
                dueEpochDay = nextTuesday.toEpochDay(),
                repeat = Repeat.WEEKLY,
            )
        )
        chores.upsert(
            Chore(
                title = "Return headphones",
                note = "UPS drop-off, no box needed.",
                whenType = ChoreWhen.DATE,
                dueEpochDay = today.plusDays(3).toEpochDay(),
            )
        )
        chores.upsert(
            Chore(
                title = "Cancel trial subscription",
                whenType = ChoreWhen.DATE,
                dueEpochDay = today.plusDays(14).toEpochDay(),
            )
        )
        chores.upsert(Chore(title = "Replace AC filter", whenType = ChoreWhen.WEEKEND))

        val storeIds = db.storeDao().all().associate { it.name to it.id }
        val indian = listOfNotNull(storeIds["Indian"])
        val american = listOfNotNull(storeIds["American"])
        val costco = listOfNotNull(storeIds["Costco"])

        val items = db.itemDao()
        val groceries = listOf(
            "Sona masoori rice" to indian + costco,
            "Coriander" to indian,
            "Curry leaves" to indian,
            "Paneer" to indian + costco,
            "Milk" to american + costco,
            "Eggs" to costco,
            "Paper towels" to costco,
        )
        groceries.forEach { (name, stores) ->
            // Reuse the item if it already exists, so loading twice can't hit the unique name rule.
            val item = items.findByName(name) ?: Item(name = name)
            items.addToLists(item, stores)
        }
    }
}
