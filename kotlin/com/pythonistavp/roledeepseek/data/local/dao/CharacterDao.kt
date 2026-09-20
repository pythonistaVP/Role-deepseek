package com.pythonistavp.roledeepseek.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pythonistavp.roledeepseek.data.local.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    @Query("SELECT * FROM characters")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<CharacterEntity?>

    @Query("SELECT * FROM characters WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CharacterEntity?

    @Query("SELECT * FROM characters WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CharacterEntity>

    @Query("SELECT * FROM characters")
    suspend fun getAll(): List<CharacterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CharacterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CharacterEntity>)

    @Query("UPDATE characters SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE characters SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFolder(id: String, folderId: String?, updatedAt: Long)

    @Query("UPDATE characters SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: String)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM characters WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM characters")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM characters WHERE pinned = 1")
    suspend fun pinnedCount(): Int

    @Query("SELECT COUNT(*) FROM characters WHERE avatarPath IS NOT NULL")
    suspend fun avatarsCount(): Int
}
