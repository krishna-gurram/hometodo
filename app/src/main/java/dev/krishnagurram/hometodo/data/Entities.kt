package dev.krishnagurram.hometodo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// Every table in the app lives in this file. Room turns each @Entity into a SQLite
// table, the way JPA turns an @Entity into a Postgres table.
//
// Two rules apply to every row, so that adding sync for your wife later doesn't
// require a data migration:
//   1. ids are UUID strings, not auto-increment ints (two phones can't collide)
//   2. rows that can be edited carry updatedAt (lets a future sync pick the newer copy)

fun newId(): String = UUID.randomUUID().toString()
fun now(): Long = System.currentTimeMillis()

// ---------------------------------------------------------------- groceries

@Entity(tableName = "stores")
data class Store(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val colorHex: String,
    val sortOrder: Int,
    val updatedAt: Long = now(),
)

/**
 * An item on a list is needed = true. Ticking it sets checkedAt (struck through);
 * "Clear checked" or a swipe sets needed = false. The item itself is kept so typing
 * its name later suggests it.
 */
@Entity(
    tableName = "items",
    // NOCASE makes "Ghee" and "ghee" the same name, so the unique index catches both.
    indices = [Index(value = ["name"], unique = true)],
)
data class Item(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** Other names the WhatsApp parser should match, comma-separated: "dhania,cilantro". */
    val aliases: String = "",
    val needed: Boolean = false,
    /** Free text on purpose: "2", "20 lb" and "a big bunch" are all valid. */
    val quantity: String? = null,
    val updatedAt: Long = now(),
    /** When it was put on the list. Lists show items in this order, like lines on paper. */
    val listedAt: Long? = null,
    /** Set when you tick it: shown struck through until "Clear checked". Null = still to get. */
    val checkedAt: Long? = null,
)

/**
 * The many-to-many join: one row per (item, store) pair. Rice tagged to Indian
 * and Costco is two rows here, but still ONE item, so checking it off anywhere
 * clears it everywhere for free.
 */
@Entity(
    tableName = "item_stores",
    primaryKeys = ["itemId", "storeId"],
    foreignKeys = [
        ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Store::class, ["id"], ["storeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("storeId")],
)
data class ItemStore(
    val itemId: String,
    val storeId: String,
)

/** One row per tick. The History screen reads this, and it ranks suggestions by how often you buy things. */
@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(Item::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Store::class, ["id"], ["storeId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("itemId"), Index("storeId"), Index("boughtAt")],
)
data class Purchase(
    @PrimaryKey val id: String = newId(),
    val itemId: String,
    /** Which store's list you checked it off from. Null if that store was deleted. */
    val storeId: String?,
    val quantity: String?,
    val boughtAt: Long = now(),
)

// ---------------------------------------------------------------- chores

/** Room stores enums as their name ("WEEKEND"), so renaming a value later is a migration. */
enum class ChoreWhen { WEEKNIGHT, WEEKEND, DATE }

enum class Repeat { NONE, WEEKLY, BIWEEKLY, MONTHLY }

@Entity(tableName = "chores")
data class Chore(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val note: String = "",
    val whenType: ChoreWhen,
    /**
     * The next day it's due, as LocalDate.toEpochDay() (days since 1970-01-01).
     * A plain day count has no time zone, so "due Tuesday" stays Tuesday even if
     * you travel to India and the phone's zone changes.
     */
    val dueEpochDay: Long? = null,
    val repeat: Repeat = Repeat.NONE,
    /** Set when a one-off chore is done. Repeating chores just move dueEpochDay forward. */
    val completedAt: Long? = null,
    val updatedAt: Long = now(),
)

/** Any chore can hold any number of photos: QR codes, screenshots, receipts. */
@Entity(
    tableName = "chore_photos",
    foreignKeys = [ForeignKey(Chore::class, ["id"], ["choreId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("choreId")],
)
data class ChorePhoto(
    @PrimaryKey val id: String = newId(),
    val choreId: String,
    /** Path inside the app's private storage. We copy the image in, never just point at the gallery. */
    val filePath: String,
    val addedAt: Long = now(),
)

/** Log of every time a chore was done. This is what the History screen shows for chores. */
@Entity(
    tableName = "chore_completions",
    foreignKeys = [ForeignKey(Chore::class, ["id"], ["choreId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("choreId"), Index("completedAt")],
)
data class ChoreCompletion(
    @PrimaryKey val id: String = newId(),
    val choreId: String,
    val completedAt: Long = now(),
)
