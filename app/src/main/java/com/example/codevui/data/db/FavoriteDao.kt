package com.example.codevui.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * FavoriteDao — Room DAO cho thao tác CRUD với bảng favorites.
 */
@Dao
interface FavoriteDao {

    /**
     * Lấy tất cả favorites, sắp xếp theo sortOrder ASC.
     */
    @Query("SELECT * FROM favorites ORDER BY sortOrder ASC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    /**
     * Lấy tất cả favorites (non-flow, dùng cho ViewModel không cần observe).
     */
    @Query("SELECT * FROM favorites ORDER BY sortOrder ASC")
    suspend fun getAllFavoritesList(): List<FavoriteEntity>

    /**
     * Lấy count tất cả favorites.
     */
    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoritesCount(): Int

    /**
     * Kiểm tra xem 1 path có trong favorites chưa.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path LIMIT 1)")
    suspend fun isFavorite(path: String): Boolean

    /**
     * Lấy 1 favorite theo path.
     */
    @Query("SELECT * FROM favorites WHERE path = :path LIMIT 1")
    suspend fun getFavoriteByPath(path: String): FavoriteEntity?

    /**
     * Thêm 1 file vào favorites.
     * @return số row inserted (1 = thành công, 0 = thất bại do conflict)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity): Long

    /**
     * Xóa 1 file khỏi favorites theo path.
     * @return số row deleted
     */
    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun deleteByPath(path: String): Int

    /**
     * Xóa 1 file khỏi favorites theo fileId.
     * @return số row deleted
     */
    @Query("DELETE FROM favorites WHERE fileId = :fileId")
    suspend fun deleteById(fileId: String): Int

    /**
     * Xóa nhiều file khỏi favorites theo path.
     */
    @Query("DELETE FROM favorites WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>): Int

    /**
     * Cập nhật sortOrder cho 1 favorite (sau khi reorder).
     */
    @Query("UPDATE favorites SET sortOrder = :sortOrder WHERE fileId = :fileId")
    suspend fun updateSortOrder(fileId: String, sortOrder: Int)

    /**
     * Cập nhật thông tin file (sau khi file bị rename/move).
     * Chỉ cập nhật những trường có thể thay đổi.
     */
    @Query("""
        UPDATE favorites
        SET name = :name, path = :path, size = :size,
            dateModified = :dateModified
        WHERE fileId = :fileId
    """)
    suspend fun updateFileInfo(
        fileId: String,
        name: String,
        path: String,
        size: Long,
        dateModified: Long
    ): Int

    /**
     * Xóa tất cả favorites.
     */
    @Query("DELETE FROM favorites")
    suspend fun deleteAll()

    /**
     * Lấy max sortOrder hiện tại (dùng để thêm item mới vào cuối).
     */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM favorites")
    suspend fun getMaxSortOrder(): Int

    /**
     * Xóa favorites mà file không còn tồn tại trên hệ thống.
     * Dùng trong validation lazy — kiểm tra từng path.
     * @return số row deleted
     */
    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun deleteIfNotExists(path: String): Int
}
