package com.pythonistavp.roledeepseek.data.model

/** Событие локальной аналитики (никуда не отправляется). */
data class UsageRecord(
    val id: Long = 0L,
    val characterId: String,
    val action: UsageAction,
    val timestamp: Long,
    val detail: String? = null,
)

/** Агрегат «сколько раз использовали персонажа». */
data class CharacterUsage(
    val characterId: String,
    val uses: Int,
)

/** Сводная статистика библиотеки (экран настроек). */
data class LibraryStatistics(
    val characters: Int = 0,
    val folders: Int = 0,
    val pinned: Int = 0,
    val chatLaunches: Int = 0,
    val copies: Int = 0,
    val totalActions: Int = 0,
    val avatars: Int = 0,
    val avatarBytes: Long = 0L,
    val databaseBytes: Long = 0L,
)

/** Счётчики по одному персонажу (экран просмотра). */
data class CharacterCounters(
    val chats: Int = 0,
    val copies: Int = 0,
    val views: Int = 0,
    val edits: Int = 0,
    val lastUsedAt: Long? = null,
)
