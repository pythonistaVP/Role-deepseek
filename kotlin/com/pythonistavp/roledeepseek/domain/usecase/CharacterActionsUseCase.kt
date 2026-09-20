package com.pythonistavp.roledeepseek.domain.usecase

import android.net.Uri
import com.pythonistavp.roledeepseek.data.files.AvatarInfo
import com.pythonistavp.roledeepseek.data.files.AvatarStore
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import com.pythonistavp.roledeepseek.util.Ids
import javax.inject.Inject

/** Закрепление, перемещение между папками, дублирование и запись статистики. */
class CharacterActionsUseCase @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun setPinned(id: String, pinned: Boolean) =
        characterRepository.setPinned(id, pinned)

    suspend fun moveToFolder(ids: List<String>, folderId: String?) =
        characterRepository.moveToFolder(ids, folderId)

    suspend fun duplicate(character: Character): Character {
        val all = characterRepository.getAll()
        val base = character.name.substringBeforeLast(DUPLICATE_MARK, character.name)
        var index = 2
        while (all.any { it.name.equals("$base$DUPLICATE_MARK$index", true) }) index++
        val copy = characterRepository.duplicate(character, Ids.newId(), "$base$DUPLICATE_MARK$index")
        usageRepository.record(copy.id, UsageAction.CREATE)
        return copy
    }

    suspend fun recordUsage(characterId: String, action: UsageAction, detail: String? = null) =
        usageRepository.record(characterId, action, detail)

    suspend fun setFavorite(characterId: String?) = settingsRepository.setFavorite(characterId)

    private companion object {
        const val DUPLICATE_MARK = " · "
    }
}
