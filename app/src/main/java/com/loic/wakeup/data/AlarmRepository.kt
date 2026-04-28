package com.loic.wakeup.data

import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val dao: AlarmDao) {
    fun observeAll(): Flow<List<AlarmEntity>> = dao.observeAll()
    suspend fun getById(id: Int): AlarmEntity? = dao.getById(id)
    suspend fun getAllEnabled(): List<AlarmEntity> = dao.getAllEnabled()
    suspend fun getAllTemporarilyDisabled(): List<AlarmEntity> = dao.getAllTemporarilyDisabled()
    suspend fun upsert(alarm: AlarmEntity): Long = dao.upsert(alarm)
    suspend fun delete(alarm: AlarmEntity) = dao.delete(alarm)
    suspend fun setEnabled(id: Int, enabled: Boolean) = dao.setEnabled(id, enabled)
    suspend fun setTemporarilyDisabledUntil(id: Int, untilMillis: Long) =
        dao.setTemporarilyDisabledUntil(id, untilMillis)
    suspend fun clearTemporaryDisableAndEnable(id: Int) = dao.clearTemporaryDisableAndEnable(id)
    suspend fun setSnoozeCount(id: Int, count: Int) = dao.setSnoozeCount(id, count)
    suspend fun disableAll() = dao.disableAll()
}
