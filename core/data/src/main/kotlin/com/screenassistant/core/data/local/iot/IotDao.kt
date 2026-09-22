package com.screenassistant.core.data.local.iot

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO para entidades IoT.
 *
 * Proporciona acceso a datos persistidos de dispositivos Matter,
 * métricas de salud, Tiles de Wear OS y estados del coche.
 * Los timestamps se manejan como Long (epoch millis).
 */
@Dao
interface IotDao {

    // ===== Matter Devices =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatterDevice(device: MatterDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatterDevices(devices: List<MatterDeviceEntity>)

    @Query("SELECT * FROM matter_devices WHERE deviceId = :deviceId")
    suspend fun getMatterDevice(deviceId: String): MatterDeviceEntity?

    @Query("SELECT * FROM matter_devices WHERE room = :room")
    suspend fun getMatterDevicesByRoom(room: String): List<MatterDeviceEntity>

    @Query("SELECT * FROM matter_devices WHERE type = :type")
    suspend fun getMatterDevicesByType(type: String): List<MatterDeviceEntity>

    @Query("SELECT * FROM matter_devices WHERE isOnline = 1")
    suspend fun getOnlineMatterDevices(): List<MatterDeviceEntity>

    @Query("SELECT * FROM matter_devices")
    suspend fun getAllMatterDevices(): List<MatterDeviceEntity>

    @Query("SELECT * FROM matter_devices")
    fun observeAllMatterDevices(): Flow<List<MatterDeviceEntity>>

    @Query("SELECT * FROM matter_devices WHERE isOnline = 1")
    fun observeOnlineMatterDevices(): Flow<List<MatterDeviceEntity>>

    @Update
    suspend fun updateMatterDevice(device: MatterDeviceEntity)

    @Query("DELETE FROM matter_devices WHERE deviceId = :deviceId")
    suspend fun deleteMatterDevice(deviceId: String)

    @Query("DELETE FROM matter_devices")
    suspend fun clearMatterDevices()

    // ===== Health Metrics =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthMetrics(metrics: HealthMetricsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthMetricsList(metrics: List<HealthMetricsEntity>)

    @Query("SELECT * FROM health_metrics ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestHealthMetrics(): HealthMetricsEntity?

    @Query("SELECT * FROM health_metrics WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getHealthMetricsInRange(startTime: Long, endTime: Long): List<HealthMetricsEntity>

    @Query("SELECT * FROM health_metrics WHERE source = :source ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getHealthMetricsBySource(source: String, limit: Int): List<HealthMetricsEntity>

    @Query("SELECT * FROM health_metrics ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatestHealthMetrics(limit: Int): Flow<List<HealthMetricsEntity>>

    @Query("DELETE FROM health_metrics WHERE timestamp < :cutoffTime")
    suspend fun deleteOldHealthMetrics(cutoffTime: Long)

    @Query("DELETE FROM health_metrics")
    suspend fun clearHealthMetrics()

    // ===== Wear Tiles =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWearTile(tile: WearTileEntity)

    @Query("SELECT * FROM wear_tiles WHERE tileId = :tileId")
    suspend fun getWearTile(tileId: String): WearTileEntity?

    @Query("SELECT * FROM wear_tiles")
    suspend fun getAllWearTiles(): List<WearTileEntity>

    @Query("SELECT * FROM wear_tiles")
    fun observeAllWearTiles(): Flow<List<WearTileEntity>>

    @Query("DELETE FROM wear_tiles WHERE tileId = :tileId")
    suspend fun deleteWearTile(tileId: String)

    @Query("DELETE FROM wear_tiles")
    suspend fun clearWearTiles()

    // ===== Car App States =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCarAppState(state: CarAppStateEntity)

    @Query("SELECT * FROM car_app_states ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCarAppState(): CarAppStateEntity?

    @Query("SELECT * FROM car_app_states WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getCarAppStatesInRange(startTime: Long, endTime: Long): List<CarAppStateEntity>

    @Query("SELECT * FROM car_app_states WHERE connectionType = :connectionType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getCarAppStatesByConnection(connectionType: String, limit: Int): List<CarAppStateEntity>

    @Query("SELECT * FROM car_app_states ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatestCarAppStates(limit: Int): Flow<List<CarAppStateEntity>>

    @Query("DELETE FROM car_app_states WHERE timestamp < :cutoffTime")
    suspend fun deleteOldCarAppStates(cutoffTime: Long)

    @Query("DELETE FROM car_app_states")
    suspend fun clearCarAppStates()

    // ===== Matter Scenes =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatterScene(scene: MatterSceneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatterScenes(scenes: List<MatterSceneEntity>)

    @Query("SELECT * FROM matter_scenes WHERE sceneId = :sceneId")
    suspend fun getMatterScene(sceneId: String): MatterSceneEntity?

    @Query("SELECT * FROM matter_scenes")
    suspend fun getAllMatterScenes(): List<MatterSceneEntity>

    @Query("SELECT * FROM matter_scenes")
    fun observeAllMatterScenes(): Flow<List<MatterSceneEntity>>

    @Update
    suspend fun updateMatterScene(scene: MatterSceneEntity)

    @Query("DELETE FROM matter_scenes WHERE sceneId = :sceneId")
    suspend fun deleteMatterScene(sceneId: String)

    @Query("DELETE FROM matter_scenes")
    suspend fun clearMatterScenes()

    // ===== Transactional Operations =====

    @Transaction
    suspend fun upsertMatterDevicesWithCleanup(devices: List<MatterDeviceEntity>, maxAge: Long) {
        insertMatterDevices(devices)
        // Opcional: limpiar dispositivos muy antiguos no vistos
        // deleteOldMatterDevices(maxAge)
    }
}