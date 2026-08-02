package com.screenassistant.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AlarmDao {
    @Upsert
    suspend fun upsert(alarm: AlarmEntity)

    @Query("SELECT * FROM alarms WHERE requestCode = :requestCode")
    suspend fun getByRequestCode(requestCode: Int): AlarmEntity?

    @Query("SELECT * FROM alarms ORDER BY requestCode ASC")
    suspend fun getAll(): List<AlarmEntity>

    @Query("DELETE FROM alarms WHERE requestCode = :requestCode")
    suspend fun deleteByRequestCode(requestCode: Int)

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()
}
