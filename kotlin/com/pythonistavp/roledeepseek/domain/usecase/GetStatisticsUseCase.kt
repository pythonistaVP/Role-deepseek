package com.pythonistavp.roledeepseek.domain.usecase

import android.content.Context
import com.pythonistavp.roledeepseek.data.local.RoleDeepSeekDatabase
import com.pythonistavp.roledeepseek.data.model.LibraryStatistics
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.FolderRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Сводка для экрана настроек: всё считается локально. */
class GetStatisticsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterRepository: CharacterRepository,
    private val folderRepository: FolderRepository,
    private val usageRepository: UsageRepository,
) {
    suspend operator fun invoke(): LibraryStatistics {
        val databaseBytes = runCatching {
            context.getDatabasePath(RoleDeepSeekDatabase.NAME).length()
        }.getOrDefault(0L)

        return LibraryStatistics(
            characters = characterRepository.count(),
            folders = folderRepository.count(),
            pinned = characterRepository.pinnedCount(),
            chatLaunches = usageRepository.count(UsageAction.CHAT_LAUNCH),
            copies = usageRepository.count(UsageAction.COPY_PROMPT) +
                usageRepository.count(UsageAction.COPY_GREETING),
            totalActions = usageRepository.total(),
            avatars = characterRepository.avatarsCount(),
            avatarBytes = characterRepository.avatarBytes(),
            databaseBytes = databaseBytes,
        )
    }
}
