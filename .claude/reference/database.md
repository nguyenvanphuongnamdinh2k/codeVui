# Database Reference

> Chi tiết Room schema cho CodeVui. Dùng khi cần thêm column, migration, hoặc debug DB.

---

## Schema: `trash_items`

| Column | Type | Notes |
|---|:---:|---|
| id | TEXT PK | = trashName |
| originalName | TEXT | |
| trashName | TEXT | `<timestamp>_<safeName>` |
| originalPath | TEXT | INDEX (v2) |
| size | INTEGER | bytes |
| deleteTimeEpoch | INTEGER | seconds, INDEX (v2) |
| isDirectory | INTEGER | 0/1 |
| mimeType | TEXT | |
| extension_ | TEXT? | nullable |

**Migration v1→v2**: Thêm indexes trên `deleteTimeEpoch` và `originalPath`.

### DAO: `TrashDao.kt`

```kotlin
@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deleteTimeEpoch DESC")
    fun observeAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TrashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrashEntity)

    @Query("DELETE FROM trash_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM trash_items WHERE deleteTimeEpoch < :epoch")
    suspend fun deleteExpired(epoch: Long)

    @Query("SELECT SUM(size) FROM trash_items")
    suspend fun getTotalSize(): Long?
}
```

---

## Schema: `favorites`

| Column | Type | Notes |
|---|:---:|---|
| fileId | TEXT PK | SHA-256 hash of path |
| name | TEXT | Display name |
| path | TEXT | Full file path, UNIQUE index |
| size | INTEGER | bytes |
| mimeType | TEXT? | nullable |
| isDirectory | INTEGER | 0/1 |
| dateModified | INTEGER | seconds |
| addedAt | INTEGER | seconds |
| sortOrder | INTEGER | INDEX, for drag-drop reorder |

**Migration v2→v3**: Thêm bảng `favorites` (với `MIGRATION_2_3`).

### DAO: `FavoriteDao.kt`

```kotlin
@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): FavoriteEntity?

    @Query("SELECT path FROM favorites")
    suspend fun getAllPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM favorites WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Update
    suspend fun update(item: FavoriteEntity)

    @Query("UPDATE favorites SET sortOrder = :sortOrder WHERE path = :path")
    suspend fun updateSortOrder(path: String, sortOrder: Int)
}
```

---

## AppDatabase

```kotlin
@Database(
    entities = [TrashEntity::class, FavoriteEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trashDao(): TrashDao
    abstract fun favoriteDao(): FavoriteDao
}

// Migration chain: v1 → v2 → v3
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trash_deleteTime ON trash_items(deleteTimeEpoch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trash_originalPath ON trash_items(originalPath)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE favorites (
                fileId TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                path TEXT NOT NULL UNIQUE,
                size INTEGER NOT NULL DEFAULT 0,
                mimeType TEXT,
                isDirectory INTEGER NOT NULL DEFAULT 0,
                dateModified INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorites_sortOrder ON favorites(sortOrder)")
    }
}
```
