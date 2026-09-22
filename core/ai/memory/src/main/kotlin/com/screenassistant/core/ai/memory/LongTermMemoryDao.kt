package com.screenassistant.core.ai.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO para memoria a largo plazo.
 */
@Dao
interface LongTermMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LongTermMemoryEntity)

    @Query("SELECT * FROM long_term_memory ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<LongTermMemoryEntity>

    @Query("SELECT * FROM long_term_memory WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 10): List<LongTermMemoryEntity>

    @Query("SELECT COUNT(*) FROM long_term_memory")
    suspend fun count(): Int

    @Query("DELETE FROM long_term_memory WHERE timestamp < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long): Int

    @Query("DELETE FROM long_term_memory")
    suspend fun clear()
}
