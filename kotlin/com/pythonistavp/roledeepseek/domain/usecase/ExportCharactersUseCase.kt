package com.pythonistavp.roledeepseek.domain.usecase

import android.graphics.Bitmap
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.CharacterFile
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.model.toFile
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.FolderRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import com.pythonistavp.roledeepseek.util.Formatters
import com.pythonistavp.roledeepseek.util.QrUtil
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Экспорт одной карточки или набора карточек в родной JSON-формат. */
class ExportCharactersUseCase @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val folderRepository: FolderRepository,
    private val usageRepository: UsageRepository,
    private val json: Json,
) {
    suspend operator fun invoke(character: Character, includeAvatar: Boolean = true): String =
        json.encodeToString(CharacterFile.serializer(), buildFile(character, includeAvatar))

    suspend fun many(characters: List<Character>, includeAvatar: Boolean = true): String {
        val files = characters.map { buildFile(it, includeAvatar) }
        return json.encodeToString(ListSerializer(CharacterFile.serializer()), files)
    }

    /** QR-код карточки (без аватара — иначе код нечитаем). */
    suspend fun qr(character: Character, size: Int = 768): Bitmap? =
        QrUtil.encodeCard(invoke(character, includeAvatar = false), size)

    fun fileName(character: Character): String =
        "role_deepseek_${Formatters.safeFileName(character.name)}.json"

    fun fileNameList(count: Int): String =
        "role_deepseek_${count}_characters_${Formatters.fileStamp()}.json"

    suspend fun recordExport(character: Character) =
        usageRepository.record(character.id, UsageAction.EXPORT)

    private suspend fun buildFile(character: Character, includeAvatar: Boolean): CharacterFile {
        val folderName = character.folderId?.let { folderRepository.getById(it)?.name }
        val avatar = if (includeAvatar) characterRepository.avatarBase64(character) else null
        return character.toFile(avatar, folderName)
    }
}
