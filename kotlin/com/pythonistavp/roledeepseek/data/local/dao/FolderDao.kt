package com.pythonistavp.roledeepseek.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pythonistavp.roledeepseek.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY orderIndex ASC, name ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY orderIndex ASC, name ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FolderEntity>)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun count(): Int
}
