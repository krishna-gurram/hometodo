package dev.krishnagurram.hometodo.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.krishnagurram.hometodo.domain.CatalogEntry
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

    @Query("SELECT * FROM stores ORDER BY sortOrder")
    abstract fun observeAll(): Flow<List<Store>>

    /** Nullable because the store might be deleted while its list screen is open. */
    @Query("SELECT * FROM stores WHERE id = :storeId")
    abstract fun observe(storeId: String): Flow<Store?>

    @Upsert
    abstract suspend fun upsert(store: Store)

    @Query("UPDATE stores SET sortOrder = :order, updatedAt = :at WHERE id = :storeId")
    abstract suspend fun setSortOrder(storeId: String, order: Int, at: Long)

    /** Rewrites every store's position from a list. Simpler and safer than swapping two numbers. */
    @Transaction
    open suspend fun saveOrder(orderedIds: List<String>) {
        val at = now()
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index, at) }
    }

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
     * Returns the purchase id so Undo knows which receipt to tear up.
     */
    @Transaction
    open suspend fun checkOff(item: Item, fromStoreId: String): String {
        val at = now()
        markBought(item.id, at)
        val purchase = Purchase(itemId = item.id, storeId = fromStoreId, quantity = item.quantity, boughtAt = at)
        insertPurchase(purchase)
        return purchase.id
    }

    @Query("DELETE FROM purchases WHERE id = :purchaseId")
    abstract suspend fun deletePurchase(purchaseId: String)

    @Query("UPDATE items SET needed = :needed, updatedAt = :at WHERE id = :itemId")
    abstract suspend fun setNeeded(itemId: String, needed: Boolean, at: Long)

    @Transaction
    open suspend fun undoCheckOff(itemId: String, purchaseId: String) {
        deletePurchase(purchaseId)
        setNeeded(itemId, true, now())
    }

    // ---- store list screen ----

    /**
     * Everything on one store's list, plus the OTHER stores each item is tagged to
     * (the "+ Costco" chips). GROUP_CONCAT joins those names into one string.
     */
    @Query(
        """
        SELECT i.*,
          (SELECT GROUP_CONCAT(s2.name, ', ')
             FROM item_stores x2 JOIN stores s2 ON s2.id = x2.storeId
            WHERE x2.itemId = i.id AND x2.storeId != :storeId) AS otherStores
        FROM items i
        JOIN item_stores x ON x.itemId = i.id
        WHERE x.storeId = :storeId AND i.needed = 1
        ORDER BY i.name COLLATE NOCASE
        """
    )
    abstract fun observeList(storeId: String): Flow<List<ListItem>>

    /** Items you checked off at this store since `since` (start of today) and haven't re-added. */
    @Query(
        """
        SELECT i.*, p.id AS purchaseId
        FROM purchases p JOIN items i ON i.id = p.itemId
        WHERE p.storeId = :storeId AND p.boughtAt >= :since AND i.needed = 0
        ORDER BY p.boughtAt DESC
        """
    )
    abstract fun observeGotToday(storeId: String, since: Long): Flow<List<GotItem>>

    /**
     * Typing "Ghee" in a store's add box. If Ghee already exists anywhere in your
     * catalog, reuse it (and its history); otherwise create it. Either way, tag it to
     * this store and put it on the list.
     */
    @Transaction
    open suspend fun quickAdd(name: String, storeId: String) {
        val item = findByName(name) ?: Item(name = name)
        addToLists(item, listOf(storeId))
    }

    @Query("SELECT storeId FROM item_stores WHERE itemId = :itemId")
    abstract suspend fun storeIdsFor(itemId: String): List<String>

    @Query("DELETE FROM item_stores WHERE itemId = :itemId")
    abstract suspend fun untagAll(itemId: String)

    @Query("UPDATE items SET quantity = :quantity, updatedAt = :at WHERE id = :itemId")
    abstract suspend fun setQuantity(itemId: String, quantity: String?, at: Long)

    @Query("UPDATE items SET aliases = :aliases, updatedAt = :at WHERE id = :itemId")
    abstract suspend fun setAliases(itemId: String, aliases: String, at: Long)

    /** The edit dialog's Save: quantity, other names, and a fresh set of store tags, all at once. */
    @Transaction
    open suspend fun saveEdits(itemId: String, quantity: String?, aliases: String, storeIds: List<String>) {
        val at = now()
        setQuantity(itemId, quantity, at)
        setAliases(itemId, aliases, at)
        untagAll(itemId)
        tag(storeIds.map { ItemStore(itemId, it) })
    }

    @Query("SELECT * FROM items WHERE id = :itemId")
    abstract suspend fun byId(itemId: String): Item?

    @Query("SELECT boughtAt FROM purchases WHERE itemId = :itemId ORDER BY boughtAt DESC")
    abstract suspend fun purchaseTimes(itemId: String): List<Long>

    // ---- search and buy again (milestone 4) ----

    /**
     * The search-first add box. Matches name or aliases anywhere, then ranks:
     * names that START with what you typed, then items bought in the last 6 months,
     * then how often you buy it. So "ri" shows the rice you buy weekly first.
     */
    @Query(
        """
        SELECT i.*,
          (SELECT COUNT(*) FROM purchases p WHERE p.itemId = i.id) AS timesBought,
          (SELECT MAX(p.boughtAt) FROM purchases p WHERE p.itemId = i.id) AS lastBought,
          (SELECT GROUP_CONCAT(s.name, ', ')
             FROM item_stores x JOIN stores s ON s.id = x.storeId
            WHERE x.itemId = i.id) AS storeNames
        FROM items i
        WHERE i.name LIKE '%' || :query || '%' OR i.aliases LIKE '%' || :query || '%'
        ORDER BY (i.name LIKE :query || '%') DESC,
                 (COALESCE(lastBought, 0) >= :activeSince) DESC,
                 timesBought DESC,
                 i.name COLLATE NOCASE
        LIMIT 30
        """
    )
    abstract fun search(query: String, activeSince: Long): Flow<List<CatalogItem>>

    /** Every purchase of items tagged to this store that aren't on the list right now. */
    @Query(
        """
        SELECT p.itemId AS itemId, i.name AS name, p.boughtAt AS boughtAt
        FROM purchases p JOIN items i ON i.id = p.itemId
        WHERE i.needed = 0
          AND EXISTS (SELECT 1 FROM item_stores x WHERE x.itemId = i.id AND x.storeId = :storeId)
        """
    )
    abstract fun observePurchaseTimes(storeId: String): Flow<List<PurchaseTime>>

    /** Tapping "+" on a search result or a "probably due" chip: back on the list, tagged here. */
    @Transaction
    open suspend fun addExistingToStore(itemId: String, storeId: String) {
        setNeeded(itemId, true, now())
        tag(listOf(ItemStore(itemId, storeId)))
    }

    // ---- WhatsApp import (milestone 7) ----

    @Query("SELECT id, name, aliases FROM items")
    abstract suspend fun catalog(): List<CatalogEntry>

    /** Applies the whole reviewed import in one transaction: all items land, or none do. */
    @Transaction
    open suspend fun applyImport(rows: List<ImportWrite>) {
        val at = now()
        rows.forEach { row ->
            val item = row.itemId?.let { byId(it) } ?: findByName(row.name) ?: Item(name = row.name)
            upsert(item.copy(needed = true, quantity = row.quantity ?: item.quantity, updatedAt = at))
            if (row.storeIds.isNotEmpty()) tag(row.storeIds.map { ItemStore(item.id, it) })
        }
    }

    // ---- history (milestone 8) ----

    @Query(
        """
        SELECT p.id AS id, i.name AS itemName, s.name AS storeName, s.colorHex AS colorHex, p.boughtAt AS boughtAt
        FROM purchases p
        JOIN items i ON i.id = p.itemId
        LEFT JOIN stores s ON s.id = p.storeId
        ORDER BY p.boughtAt DESC
        LIMIT 1000
        """
    )
    abstract fun observePurchaseHistory(): Flow<List<PurchaseRow>>
}

/** A search result: the item plus what the ranking and the row need to show. */
data class CatalogItem(
    @Embedded val item: Item,
    val timesBought: Int,
    val lastBought: Long?,
    val storeNames: String?,
)

data class PurchaseTime(val itemId: String, val name: String, val boughtAt: Long)

/** One reviewed line of a WhatsApp import. itemId is null for a brand-new item. */
data class ImportWrite(val itemId: String?, val name: String, val quantity: String?, val storeIds: List<String>)

data class PurchaseRow(
    val id: String,
    val itemName: String,
    val storeName: String?,
    val colorHex: String?,
    val boughtAt: Long,
)

data class ChoreDoneRow(val id: String, val title: String, val completedAt: Long)

/** An open chore plus how many photos it has (for the photo badge on the snapshot). */
data class OpenChore(
    @Embedded val chore: Chore,
    val photoCount: Int,
)

/** A row on a store's list. @Embedded = "the Item's columns are flattened into this row". */
data class ListItem(
    @Embedded val item: Item,
    val otherStores: String?,
)

data class GotItem(
    @Embedded val item: Item,
    val purchaseId: String,
)

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

    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM chore_photos p WHERE p.choreId = c.id) AS photoCount
        FROM chores c
        WHERE c.completedAt IS NULL
        ORDER BY c.dueEpochDay IS NULL, c.dueEpochDay
        """
    )
    abstract fun observeOpenWithPhotos(): Flow<List<OpenChore>>

    /** Same as observeOpen, but read once. The reminder needs a snapshot, not a live feed. */
    @Query("SELECT * FROM chores WHERE completedAt IS NULL")
    abstract suspend fun openNow(): List<Chore>

    @Query("SELECT * FROM chores WHERE id = :choreId")
    abstract suspend fun byId(choreId: String): Chore?

    @Query("SELECT * FROM chore_photos WHERE choreId = :choreId ORDER BY addedAt")
    abstract suspend fun photos(choreId: String): List<ChorePhoto>

    @Insert
    abstract suspend fun insertPhotos(photos: List<ChorePhoto>)

    @Query("DELETE FROM chore_photos WHERE id IN (:photoIds)")
    abstract suspend fun deletePhotos(photoIds: List<String>)

    @Query("DELETE FROM chores WHERE id = :choreId")
    abstract suspend fun delete(choreId: String)

    /**
     * Save from the chore screen. The chore row must exist BEFORE its photo rows,
     * because each photo's choreId is a foreign key pointing at it.
     */
    @Transaction
    open suspend fun save(chore: Chore, addPhotos: List<ChorePhoto>, removePhotoIds: List<String>) {
        upsert(chore)
        if (removePhotoIds.isNotEmpty()) deletePhotos(removePhotoIds)
        if (addPhotos.isNotEmpty()) insertPhotos(addPhotos)
    }

    @Query(
        """
        SELECT c.id AS id, ch.title AS title, c.completedAt AS completedAt
        FROM chore_completions c JOIN chores ch ON ch.id = c.choreId
        ORDER BY c.completedAt DESC
        LIMIT 500
        """
    )
    abstract fun observeCompletionHistory(): Flow<List<ChoreDoneRow>>
}
