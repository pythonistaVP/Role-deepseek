package com.pythonistavp.roledeepseek.data.repository

import androidx.room.withTransaction
import com.pythonistavp.roledeepseek.data.files.AvatarInfo
import com.pythonistavp.roledeepseek.data.files.AvatarStore
import com.pythonistavp.roledeepseek.data.local.RoleDeepSeekDatabase
import com.pythonistavp.roledeepseek.data.model.Character
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CharacterRepository @Inject constructor(
    private val database: RoleDeepSeekDatabase,
    private val avatarStore: AvatarStore,
) {
    private val dao = database.characterDao()

    fun observeAll(): Flow<List<Character>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: String): Flow<Character?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): Character? = dao.getById(id)?.toDomain()

    suspend fun getByIds(ids: List<String>): List<Character> =
        if (ids.isEmpty()) emptyList() else dao.getByIds(ids).map { it.toDomain() }

    suspend fun getAll(): List<Character> = dao.getAll().map { it.toDomain() }

    suspend fun upsert(character: Character) = dao.upsert(character.toEntity())

    suspend fun upsertAll(characters: List<Character>) {
        if (characters.isEmpty()) return
        database.withTransaction { dao.upsertAll(characters.map { it.toEntity() }) }
    }

    suspend fun setPinned(id: String, pinned: Boolean) =
        dao.setPinned(id, pinned, System.currentTimeMillis())

    suspend fun moveToFolder(id: String, folderId: String?) =
        dao.setFolder(id, folderId, System.currentTimeMillis())

    suspend fun moveToFolder(ids: List<String>, folderId: String?) {
        val now = System.currentTimeMillis()
        database.withTransaction { ids.forEach { dao.setFolder(it, folderId, now) } }
    }

    suspend fun delete(id: String) {
        val existing = dao.getById(id)
        dao.deleteById(id)
        avatarStore.delete(existing?.avatarPath)
    }

    suspend fun deleteMany(ids: List<String>) {
        if (ids.isEmpty()) return
        val existing = dao.getByIds(ids)
        database.withTransaction { dao.deleteByIds(ids) }
        existing.forEach { avatarStore.delete(it.avatarPath) }
    }

    /** Полная замена библиотеки (импорт бэкапа в режиме «Заменить всё»). */
    suspend fun replaceAll(characters: List<Character>) {
        val previous = dao.getAll()
        database.withTransaction {
            dao.deleteAll()
            dao.upsertAll(characters.map { it.toEntity() })
        }
        val keptPaths = characters.mapNotNull { it.avatarPath }.toSet()
        previous.filterNot { it.avatarPath in keptPaths }.forEach { avatarStore.delete(it.avatarPath) }
    }

    suspend fun count(): Int = dao.count()

    suspend fun pinnedCount(): Int = dao.pinnedCount()

    suspend fun avatarsCount(): Int = dao.avatarsCount()

    suspend fun attachAvatar(id: String, info: AvatarInfo): Character? {
        val existing = getById(id) ?: return null
        avatarStore.delete(existing.avatarPath)
        val updated = existing.copy(avatarPath = info.path, avatarBlurHash = info.blurHash)
        dao.upsert(updated.toEntity())
        return updated
    }

    suspend fun clearAvatar(id: String): Character? {
        val existing = getById(id) ?: return null
        avatarStore.delete(existing.avatarPath)
        val updated = existing.copy(avatarPath = null, avatarBlurHash = null)
        dao.upsert(updated.toEntity())
        return updated
    }

    suspend fun avatarBase64(character: Character): String? =
        avatarStore.toBase64(character.avatarPath)

    suspend fun duplicate(character: Character, newId: String, newName: String): Character {
        val copy = character.copy(
            id = newId,
            name = newName,
            avatarPath = null,
            avatarBlurHash = null,
            pinned = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        // Файл аватара копируется побайтово, хэш подложки переносится как есть —
        // перекодировать картинку при дублировании незачем.
        val avatar = avatarStore.copyFrom(character.avatarPath, newId, character.avatarBlurHash)
        val withAvatar = copy.copy(avatarPath = avatar?.path, avatarBlurHash = avatar?.blurHash)
        dao.upsert(withAvatar.toEntity())
        return withAvatar
    }

    fun avatarStore(): AvatarStore = avatarStore

    suspend fun avatarBytes(): Long = avatarStore.sizeBytes()

    /**
     * Персонаж «по умолчанию» для виджета: сначала явно выбранный избранный,
     * потом закреплённые, потом самый используемый, потом последний изменённый.
     */
    suspend fun favorite(preferredId: String?, mostUsed: List<String>): Character? {
        val all = getAll()
        if (all.isEmpty()) return null
        preferredId?.let { id -> all.firstOrNull { it.id == id }?.let { return it } }
        all.firstOrNull { it.pinned }?.let { return it }
        mostUsed.firstNotNullOfOrNull { id -> all.firstOrNull { it.id == id } }?.let { return it }
        return all.maxByOrNull { it.updatedAt }
    }
}
