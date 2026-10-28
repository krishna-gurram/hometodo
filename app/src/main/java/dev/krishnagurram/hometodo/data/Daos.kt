package dev.krishnagurram.hometodo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
}
