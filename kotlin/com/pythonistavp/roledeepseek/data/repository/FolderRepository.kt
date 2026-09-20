package com.pythonistavp.roledeepseek.data.repository

import androidx.room.withTransaction
import com.pythonistavp.roledeepseek.data.local.RoleDeepSeekDatabase
import com.pythonistavp.roledeepseek.data.model.Folder
import com.pythonistavp.roledeepseek.data.model.FolderIcon
import com.pythonistavp.roledeepseek.util.Ids
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FolderRepository @Inject constructor(
    private val database: RoleDeepSeekDatabase,
) {
    private val folderDao = database.folderDao()
    private val characterDao = database.characterDao()

    fun observeAll(): Flow<List<Folder>> =
        folderDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<Folder> = folderDao.getAll().map { it.toDomain() }

    suspend fun getById(id: String): Folder? = folderDao.getById(id)?.toDomain()

    suspend fun findOrCreate(name: String, color: Long = Folder.DEFAULT_COLOR): Folder? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        folderDao.getByName(clean)?.let { return it.toDomain() }
        return create(clean, color, FolderIcon.STAR.key)
    }

    /**
     * Создаёт папку. Возвращает null, если имя пустое ИЛИ папка с таким именем уже есть:
     * у `folders.name` есть UNIQUE-индекс, а вставка идёт с `INSERT OR REPLACE`,
     * поэтому «создание» дубликата молча снесло бы старую папку вместе с id —
     * и все персонажи этой папки остались бы ни с чем.
     */
    suspend fun create(name: String, color: Long, icon: String): Folder? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        if (folderDao.getByName(clean) != null) return null
        val folder = Folder(
            id = Ids.newId(),
            name = clean,
            color = color,
            icon = icon,
            orderIndex = folderDao.count(),
        )
        folderDao.upsert(folder.toEntity())
        return folder
    }

    suspend fun update(folder: Folder) = folderDao.upsert(folder.toEntity())

    /**
     * Переименование. Возвращает false, если имя пустое или уже занято другой папкой:
     * иначе UPDATE нарушил бы UNIQUE-индекс и уронил приложение
     * (SQLiteConstraintException).
     */
    suspend fun rename(id: String, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val existing = folderDao.getByName(clean)
        if (existing != null && existing.id != id) return false
        folderDao.rename(id, clean)
        return true
    }

    /** Удаление папки не удаляет персонажей — они просто остаются без папки. */
    suspend fun delete(id: String) {
        database.withTransaction {
            characterDao.clearFolder(id)
            folderDao.deleteById(id)
        }
    }

    suspend fun deleteAll() = folderDao.deleteAll()

    suspend fun count(): Int = folderDao.count()
}
