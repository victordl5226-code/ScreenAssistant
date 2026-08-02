package com.screenassistant.core.data.local

/**
 * Acceso a las alarmas activas de la app (mismo patrón que MessageQueueManager:
 * envoltorio fino sobre el DAO). Sin @Inject constructor: se provee únicamente
 * vía @Provides en AppModule (única vía de creación).
 */
class ActiveAlarmStore(
    private val alarmDao: AlarmDao
) {
    suspend fun upsertAlarm(alarm: AlarmEntity) {
        alarmDao.upsert(alarm)
    }

    suspend fun findAlarm(requestCode: Int): AlarmEntity? = alarmDao.getByRequestCode(requestCode)

    suspend fun allAlarms(): List<AlarmEntity> = alarmDao.getAll()

    suspend fun removeAlarm(requestCode: Int) {
        alarmDao.deleteByRequestCode(requestCode)
    }

    suspend fun clearAll() {
        alarmDao.deleteAll()
    }
}
