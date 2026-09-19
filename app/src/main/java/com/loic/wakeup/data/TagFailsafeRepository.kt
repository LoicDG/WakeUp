package com.loic.wakeup.data

import kotlinx.coroutines.flow.Flow

class TagFailsafeRepository(private val dao: TagFailsafeDao) {
    fun observeAll(): Flow<List<TagFailsafeEntity>> = dao.observeAll()
    suspend fun getByUid(tagUid: String): TagFailsafeEntity? = dao.getByUid(tagUid)
    suspend fun getAllEnabled(): List<TagFailsafeEntity> = dao.getAllEnabled()
    suspend fun upsert(failsafe: TagFailsafeEntity) = dao.upsert(failsafe)
    suspend fun delete(tagUid: String) = dao.delete(tagUid)
}
