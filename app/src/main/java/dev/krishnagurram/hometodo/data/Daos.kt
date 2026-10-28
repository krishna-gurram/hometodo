package dev.krishnagurram.hometodo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.krishnagurram.hometodo.domain.ChoreSchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// DAOs are Room's version of Spring Data repositories: you write the SQL (or an
// annotation) and Room generates the implementation at compile time. A bad column
// name here is a BUILD error, not a runtime crash. That's the main reason to use Room.
//
// Two return styles to notice:
//   suspend fun ...      -> runs once, off the main thread (like an async call)
//   fun ...: Flow<T>     -> a live query. Room re-emits whenever a table it reads
//                           changes, which is what makes the UI update by itself.

/** Read model for the snapshot tiles. Not a table, just the shape a query returns. */
data class StoreWithCount(
    val id: String,
    val name: String,
    val colorHex: String,
    val neededCount: Int,
)

@Dao
abstract class StoreDao {

    @Query(
        """
        SELECT s.id, s.name, s.colorHex, COUNT(i.id) AS neededCount
        FROM stores s
        LEFT JOIN item_stores x ON x.storeId = s.id
        LEFT JOIN items i ON i.id = x.itemId AND i.needed = 1
        GROUP BY s.id
        ORDER BY s.sortOrder
        """
    )
    abstract fun observeWithCounts(): Flow<List<StoreWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(stores: List<Store>)

    @Query("SELECT * FROM stores ORDER BY sortOrder")
    abstract suspend fun all(): List<Store>

    // ---- deleting a store (decided: warn first, then reset) ----

    /**
     * Items on your list that ONLY this store sells. These are the ones that would
     * silently vanish, so the delete screen lists them as a warning.
     */
    @Query(
        """
        SELECT i.* FROM items i
        JOIN item_stores x ON x.itemId = i.id AND x.storeId = :storeId
        WHERE i.needed = 1
          AND NOT EXISTS (
              SELECT 1 FROM item_stores y WHERE y.itemId = i.id AND y.storeId != :storeId
          )
        ORDER BY i.name
        """
    )
    abstract suspend fun neededItemsOnlyAt(storeId: String): List<Item>

    @Query(
        """
        UPDATE items SET needed = 0, updatedAt = :at
        WHERE needed = 1
          AND id IN (SELECT itemId FROM item_stores WHERE storeId = :storeId)
          AND id NOT IN (SELECT itemId FROM item_stores WHERE storeId != :storeId)
        """
    )
    abstract suspend fun resetItemsOnlyAt(storeId: String, at: Long): Int

    @Query("DELETE FROM stores WHERE id = :storeId")
    abstract suspend fun deleteById(storeId: String)

    /**
     * Items stay in the catalog (searchable, re-taggable). Only the store and its
     * labels go away, and orphaned items come off the list so they don't
     * reappear by surprise when you tag them to another store later.
     *
     * The ORDER of these two lines matters. See lesson 1, question 4.
     */
    @Transaction
    open suspend fun deleteAndReset(storeId: String) {
        resetItemsOnlyAt(storeId, now())
        deleteById(storeId)
    }
}

@Dao
abstract class ItemDao {

    @Upsert
    abstract suspend fun upsert(item: Item)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun tag(links: List<ItemStore>)

    @Insert
    abstract suspend fun insertPurchase(purchase: Purchase)

    @Query("SELECT COUNT(*) FROM items")
    abstract fun observeCatalogSize(): Flow<Int>

    @Query("SELECT * FROM items WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): Item?

    @Query("UPDATE items SET needed = 0, updatedAt = :at WHERE id = :itemId")
    abstract suspend fun markBought(itemId: String, at: Long)

    /** Add a new item to one or more store lists, as a single all-or-nothing write. */
    @Transaction
    open suspend fun addToLists(item: Item, storeIds: List<String>) {
        upsert(item.copy(needed = true))
        tag(storeIds.map { ItemStore(item.id, it) })
    }

    /**
     * Check-off. Two writes that must succeed or fail together: if the app died
     * between them you'd have an item that's off the list with no history row.
     */
    @Transaction
    open suspend fun checkOff(item: Item, fromStoreId: String) {
        val at = now()
        markBought(item.id, at)
        insertPurchase(Purchase(itemId = item.id, storeId = fromStoreId, quantity = item.quantity, boughtAt = at))
    }
}

@Dao
abstract class ChoreDao {

    @Upsert
    abstract suspend fun upsert(chore: Chore)

    @Query("SELECT * FROM chores WHERE completedAt IS NULL ORDER BY dueEpochDay IS NULL, dueEpochDay")
    abstract fun observeOpen(): Flow<List<Chore>>

    @Insert
    abstract suspend fun insertCompletion(completion: ChoreCompletion)

    @Query("DELETE FROM chore_completions WHERE id = :completionId")
    abstract suspend fun deleteCompletion(completionId: String)

    /**
     * Tapping the circle. Always logs a completion (for History). Then either moves a
     * repeating chore to its next date, or marks a one-off chore finished.
     * Returns the completion id so the Undo button knows what to remove.
     */
    @Transaction
    open suspend fun complete(chore: Chore, today: LocalDate): String {
        val at = now()
        val completion = ChoreCompletion(choreId = chore.id, completedAt = at)
        insertCompletion(completion)
        val next = ChoreSchedule.nextDue(chore, today)
        if (next != null) {
            upsert(chore.copy(dueEpochDay = next.toEpochDay(), updatedAt = at))
        } else {
            upsert(chore.copy(completedAt = at, updatedAt = at))
        }
        return completion.id
    }

    /** Undo puts back the exact chore row we had before, and erases the log entry. */
    @Transaction
    open suspend fun undoComplete(previous: Chore, completionId: String) {
        deleteCompletion(completionId)
        upsert(previous)
    }
}
