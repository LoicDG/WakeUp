package com.loic.wakeup.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Int): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getAllEnabled(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE enabled = 0 AND temporaryDisabledUntilMillis IS NOT NULL")
    suspend fun getAllTemporarilyDisabled(): List<AlarmEntity>

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled, temporaryDisabledUntilMillis = NULL WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE alarms SET enabled = 0, temporaryDisabledUntilMillis = :untilMillis WHERE id = :id")
    suspend fun setTemporarilyDisabledUntil(id: Int, untilMillis: Long)

    @Query("UPDATE alarms SET enabled = 1, temporaryDisabledUntilMillis = NULL WHERE id = :id")
    suspend fun clearTemporaryDisableAndEnable(id: Int)

    @Query("UPDATE alarms SET snoozeCount = :count WHERE id = :id")
    suspend fun setSnoozeCount(id: Int, count: Int)

    @Query("UPDATE alarms SET enabled = 0, temporaryDisabledUntilMillis = NULL WHERE enabled = 1 OR temporaryDisabledUntilMillis IS NOT NULL")
    suspend fun disableAll()
}
