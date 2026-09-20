package com.pythonistavp.roledeepseek.domain.usecase

import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import javax.inject.Inject

class DeleteCharactersUseCase @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val favoriteCharacter: FavoriteCharacterUseCase,
) {
    suspend operator fun invoke(ids: List<String>) {
        if (ids.isEmpty()) return
        characterRepository.deleteMany(ids)
        usageRepository.deleteFor(ids)
        val favorite = settingsRepository.current().favoriteCharacterId
        if (favorite != null && ids.contains(favorite)) {
            // Именно через FavoriteCharacterUseCase: он ещё и обновляет виджет,
            // иначе на рабочем столе остался бы удалённый персонаж.
            favoriteCharacter.setFavorite(null)
        }
    }
}
