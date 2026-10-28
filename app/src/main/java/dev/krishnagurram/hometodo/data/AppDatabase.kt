package dev.krishnagurram.hometodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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
                .build()
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
