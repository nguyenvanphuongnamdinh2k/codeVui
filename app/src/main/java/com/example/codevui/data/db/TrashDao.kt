package com.example.codevui.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {

    @Query("SELECT * FROM trash_items ORDER BY deleteTimeEpoch DESC")
    suspend fun getAll(): List<TrashEntity>

    @Query("SELECT * FROM trash_items ORDER BY deleteTimeEpoch DESC")
    fun observeAll(): Flow<List<TrashEntity>>

    @Query("SELECT COUNT(*) FROM trash_items")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM trash_items")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrashEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TrashEntity>)

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM trash_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM trash_items")
    suspend fun clearAll()

    @Query("SELECT * FROM trash_items WHERE deleteTimeEpoch < :cutoffEpoch")
    suspend fun findExpired(cutoffEpoch: Long): List<TrashEntity>

    @Query("SELECT SUM(size) FROM trash_items")
    suspend fun totalSize(): Long?
}
