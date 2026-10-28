package dev.krishnagurram.hometodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Store::class,
        Item::class,
        ItemStore::class,
        Purchase::class,
        Chore::class,
        ChorePhoto::class,
        ChoreCompletion::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun storeDao(): StoreDao
    abstract fun itemDao(): ItemDao
    abstract fun choreDao(): ChoreDao

    // `companion object` is Kotlin's home for what Java would make static.
    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hometodo.db")
                .addCallback(SeedStores)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/**
 * Version 1 -> 2: lists became paper-style (strike through, then "Clear checked").
 * Two new nullable columns on items. Existing rows get null in both, which means
 * "unchecked" and "no list position yet", and the list falls back to updatedAt order.
 * Without this, Room would refuse to open your existing database and the app would crash.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN listedAt INTEGER")
        db.execSQL("ALTER TABLE items ADD COLUMN checkedAt INTEGER")
    }
}

/**
 * Runs exactly once, the first time the database file is created on the phone.
 * It talks raw SQL because the DAOs don't exist yet at this point.
 */
private object SeedStores : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        val stores = listOf(
            Triple("Indian", "#B45309", 0),
            Triple("American", "#1D4ED8", 1),
            Triple("Costco", "#475569", 2),
        )
        stores.forEach { (name, color, order) ->
            db.execSQL(
                "INSERT INTO stores (id, name, colorHex, sortOrder, updatedAt) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(newId(), name, color, order, now()),
            )
        }
    }
}
