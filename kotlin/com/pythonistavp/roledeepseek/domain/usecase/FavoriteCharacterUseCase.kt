package com.pythonistavp.roledeepseek.domain.usecase

import android.content.Context
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import com.pythonistavp.roledeepseek.widget.FavoriteCharacterWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Любимый персонаж — для виджета и быстрого запуска. */
class FavoriteCharacterUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterRepository: CharacterRepository,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun resolve(): Character? {
        val preferred = settingsRepository.current().favoriteCharacterId
        val mostUsed = usageRepository.topUsed(UsageAction.CHAT_LAUNCH, 10).map { it.characterId }
        return characterRepository.favorite(preferred, mostUsed)
    }

    suspend fun setFavorite(characterId: String?) {
        settingsRepository.setFavorite(characterId)
        FavoriteCharacterWidgetProvider.refresh(context)
    }
}
