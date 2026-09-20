package com.pythonistavp.roledeepseek.domain.usecase

import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import javax.inject.Inject

class SaveCharacterUseCase @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
) {
    sealed interface SaveResult {
        data class Saved(val character: Character) : SaveResult
        data object EmptyName : SaveResult
        data object EmptyPrompt : SaveResult
    }

    suspend operator fun invoke(input: Character, isNew: Boolean): SaveResult {
        if (input.name.isBlank()) return SaveResult.EmptyName
        if (input.prompt.isBlank() && input.greeting.isNullOrBlank()) return SaveResult.EmptyPrompt

        val now = System.currentTimeMillis()
        val character = input.copy(
            name = input.name.trim(),
            prompt = input.prompt.trim(),
            greeting = input.greeting?.trim()?.takeIf { it.isNotEmpty() },
            note = input.note?.trim()?.takeIf { it.isNotEmpty() },
            tags = input.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_TAGS),
            temperature = input.temperature.coerceIn(0f, 2f),
            topP = input.topP.coerceIn(0f, 1f),
            createdAt = if (isNew || input.createdAt <= 0L) now else input.createdAt,
            updatedAt = now,
        )

        characterRepository.upsert(character)
        usageRepository.record(
            characterId = character.id,
            action = if (isNew) UsageAction.CREATE else UsageAction.EDIT,
        )
        settingsRepository.rememberTags(character.tags)
        settingsRepository.saveDraft(null)
        return SaveResult.Saved(character)
    }

    private companion object {
        const val MAX_TAGS = 20
    }
}
