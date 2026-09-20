package com.pythonistavp.roledeepseek.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pythonistavp.roledeepseek.data.local.entity.UsageEntity
import com.pythonistavp.roledeepseek.data.model.CharacterUsage
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {

    @Insert
    suspend fun insert(entity: UsageEntity)

    @Query("SELECT COUNT(*) FROM usage_events")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM usage_events WHERE action = :action")
    suspend fun countByAction(action: String): Int

    @Query("SELECT COUNT(*) FROM usage_events WHERE characterId = :id AND action = :action")
    suspend fun countFor(id: String, action: String): Int

    @Query("SELECT MAX(timestamp) FROM usage_events WHERE characterId = :id")
    suspend fun lastUsedAt(id: String): Long?

    @Query("SELECT characterId AS characterId, COUNT(*) AS uses FROM usage_events WHERE action = :action GROUP BY characterId")
    fun observeUsageCounts(action: String): Flow<List<CharacterUsage>>

    @Query("SELECT characterId AS characterId, COUNT(*) AS uses FROM usage_events WHERE action = :action GROUP BY characterId ORDER BY uses DESC LIMIT :limit")
    suspend fun topUsed(action: String, limit: Int): List<CharacterUsage>

    @Query("DELETE FROM usage_events WHERE characterId = :id")
    suspend fun deleteFor(id: String)

    @Query("DELETE FROM usage_events WHERE characterId IN (:ids)")
    suspend fun deleteForIds(ids: List<String>)

    @Query("DELETE FROM usage_events")
    suspend fun clear()
}
