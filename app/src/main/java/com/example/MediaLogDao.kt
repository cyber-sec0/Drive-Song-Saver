package com.example

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaLogDao {
    @Query("SELECT * FROM media_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<MediaLogItem>>

    @Query("SELECT COUNT(*) FROM media_logs WHERE title = :title")
    suspend fun checkLogExists(title: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MediaLogItem)

    @Query("DELETE FROM media_logs")
    suspend fun clearLogs()

    @Delete
    suspend fun deleteLog(log: MediaLogItem)
}
