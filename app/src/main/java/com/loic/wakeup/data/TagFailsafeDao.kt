package com.loic.wakeup.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagFailsafeDao {
    @Query("SELECT * FROM tag_failsafes")
    fun observeAll(): Flow<List<TagFailsafeEntity>>

    @Query("SELECT * FROM tag_failsafes WHERE tagUid = :tagUid")
    suspend fun getByUid(tagUid: String): TagFailsafeEntity?

    @Query("SELECT * FROM tag_failsafes WHERE enabled = 1")
    suspend fun getAllEnabled(): List<TagFailsafeEntity>

    @Upsert
    suspend fun upsert(failsafe: TagFailsafeEntity)

    @Query("DELETE FROM tag_failsafes WHERE tagUid = :tagUid")
    suspend fun delete(tagUid: String)
}
