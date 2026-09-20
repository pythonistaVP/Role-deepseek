package com.pythonistavp.roledeepseek.data.repository

import com.pythonistavp.roledeepseek.data.datastore.SettingsDataStore
import com.pythonistavp.roledeepseek.data.local.RoleDeepSeekDatabase
import com.pythonistavp.roledeepseek.data.local.entity.UsageEntity
import com.pythonistavp.roledeepseek.data.model.CharacterCounters
import com.pythonistavp.roledeepseek.data.model.CharacterUsage
import com.pythonistavp.roledeepseek.data.model.UsageAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Локальная аналитика: только счётчики на устройстве, никакой сети. */
@Singleton
class UsageRepository @Inject constructor(
    private val database: RoleDeepSeekDatabase,
    private val settingsStore: SettingsDataStore,
) {
    private val dao = database.usageDao()

    suspend fun record(
        characterId: String,
        action: UsageAction,
        detail: String? = null,
    ) {
        // Статистику можно выключить в «Производительности»: тогда не пишем в БД
        // на каждом открытии карточки и копировании промпта.
        if (!settingsStore.current().analyticsEnabled) return
        dao.insert(
            UsageEntity(
                characterId = characterId,
                action = action.name,
                timestamp = System.currentTimeMillis(),
                detail = detail,
            ),
        )
    }

    fun observeChatCounts(): Flow<List<CharacterUsage>> =
        dao.observeUsageCounts(UsageAction.CHAT_LAUNCH.name)

    suspend fun counters(characterId: String): CharacterCounters = CharacterCounters(
        chats = dao.countFor(characterId, UsageAction.CHAT_LAUNCH.name),
        copies = dao.countFor(characterId, UsageAction.COPY_PROMPT.name) +
            dao.countFor(characterId, UsageAction.COPY_GREETING.name),
        views = dao.countFor(characterId, UsageAction.VIEW.name),
        edits = dao.countFor(characterId, UsageAction.EDIT.name),
        lastUsedAt = dao.lastUsedAt(characterId),
    )

    suspend fun total(): Int = dao.totalCount()

    suspend fun count(action: UsageAction): Int = dao.countByAction(action.name)

    suspend fun topUsed(action: UsageAction, limit: Int): List<CharacterUsage> =
        dao.topUsed(action.name, limit)

    suspend fun deleteFor(characterId: String) = dao.deleteFor(characterId)

    suspend fun deleteFor(ids: List<String>) = dao.deleteForIds(ids)

    suspend fun clear() = dao.clear()
}
