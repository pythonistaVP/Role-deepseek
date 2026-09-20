package com.pythonistavp.roledeepseek.domain.usecase

import android.net.Uri
import com.pythonistavp.roledeepseek.data.files.AvatarInfo
import com.pythonistavp.roledeepseek.data.files.AvatarStore
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import javax.inject.Inject

/** Сохранение, удаление и экспорт аватара. */
class AvatarUseCase @Inject constructor(
    private val avatarStore: AvatarStore,
    private val characterRepository: CharacterRepository,
) {
    /** Сохраняет выбранную (или обрезанную) картинку под id персонажа. */
    suspend fun save(uri: Uri, characterId: String): AvatarInfo? =
        avatarStore.importFromUri(uri, characterId)

    suspend fun clear(characterId: String) = characterRepository.clearAvatar(characterId)

    suspend fun base64(path: String?): String? = avatarStore.toBase64(path)

    suspend fun sizeBytes(): Long = avatarStore.sizeBytes()
}
