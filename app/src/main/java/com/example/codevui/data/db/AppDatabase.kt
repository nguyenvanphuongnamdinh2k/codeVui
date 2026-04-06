package com.example.codevui.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrashEntity::class, FavoriteEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trashDao(): TrashDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 -> v2: add indexes
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_items_deleteTimeEpoch ON trash_items(deleteTimeEpoch)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_items_originalPath ON trash_items(originalPath)")
            }
        }

        // v2 -> v3: add favorites table
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorites (
                        fileId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        path TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        mimeType TEXT,
                        isDirectory INTEGER NOT NULL,
                        dateModified INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favorites_path ON favorites(path)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_sortOrder ON favorites(sortOrder)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "codevui.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
