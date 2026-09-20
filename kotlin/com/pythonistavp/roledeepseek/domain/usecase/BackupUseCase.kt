package com.pythonistavp.roledeepseek.domain.usecase

import com.pythonistavp.roledeepseek.BuildConfig
import com.pythonistavp.roledeepseek.data.model.AppLanguage
import com.pythonistavp.roledeepseek.data.model.AppTheme
import com.pythonistavp.roledeepseek.data.model.BackupFile
import com.pythonistavp.roledeepseek.data.model.BackupSettings
import com.pythonistavp.roledeepseek.data.model.CharacterFile
import com.pythonistavp.roledeepseek.data.model.FolderFile
import com.pythonistavp.roledeepseek.data.model.ImportFailure
import com.pythonistavp.roledeepseek.data.model.ImportMode
import com.pythonistavp.roledeepseek.data.model.ImportReport
import com.pythonistavp.roledeepseek.data.model.SortOrder
import com.pythonistavp.roledeepseek.data.model.toFile
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.FolderRepository
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import com.pythonistavp.roledeepseek.util.Formatters
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Полная резервная копия: персонажи + папки + настройки в один .json. */
class BackupUseCase @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val usageRepository: UsageRepository,
    private val importCharacters: ImportCharactersUseCase,
    private val json: Json,
) {

    suspend fun export(): String {
        val folders = folderRepository.getAll()
        val settings = settingsRepository.current()
        val characters = characterRepository.getAll().map { character ->
            val folderName = folders.firstOrNull { it.id == character.folderId }?.name
            val avatar = characterRepository.avatarBase64(character)
            character.toFile(avatar, folderName)
        }
        val backup = BackupFile(
            spec = BackupFile.SPEC,
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            characters = characters,
            folders = folders.map { FolderFile(it.id, it.name, it.color, it.icon, it.orderIndex) },
            settings = BackupSettings(
                theme = settings.theme.name,
                language = settings.language.name,
                sortOrder = settings.sortOrder.name,
                dynamicColor = settings.dynamicColor,
                gridColumns = settings.gridColumns,
                overlayHint = settings.overlayHintEnabled,
                animateTransitions = settings.animateTransitions,
                favoriteCharacterId = settings.favoriteCharacterId,
                avatarMaxPx = settings.avatarMaxPx,
                avatarQuality = settings.avatarQuality,
                blurHashEnabled = settings.blurHashEnabled,
                thumbnailAvatars = settings.thumbnailAvatars,
                imageCrossfade = settings.imageCrossfade,
                imageMemoryCachePercent = settings.imageMemoryCachePercent,
                diskCacheMb = settings.diskCacheMb,
                analyticsEnabled = settings.analyticsEnabled,
            ),
        )
        return json.encodeToString(BackupFile.serializer(), backup)
    }

    fun fileName(): String = "role_deepseek_backup_${Formatters.fileStamp()}.json"

    /** REPLACE — стереть библиотеку и развернуть бэкап; MERGE — добавить к текущей. */
    suspend fun import(payload: String, mode: ImportMode): ImportReport {
        val backup = runCatching { json.decodeFromString(BackupFile.serializer(), payload.trim()) }
            .getOrElse { return ImportReport(failure = ImportFailure.NOT_JSON) }
        if (backup.spec != BackupFile.SPEC) {
            return ImportReport(failure = ImportFailure.UNKNOWN_FORMAT)
        }
        if (backup.characters.isEmpty() && backup.folders.isEmpty()) {
            return ImportReport(failure = ImportFailure.EMPTY)
        }

        if (mode == ImportMode.REPLACE) {
            characterRepository.replaceAll(emptyList())
            folderRepository.deleteAll()
            usageRepository.clear()
            applySettings(backup.settings)
        }

        val existingFolders = folderRepository.getAll().map { it.name.lowercase() }.toSet()
        var foldersAdded = 0
        for (file in backup.folders) {
            val name = file.name.trim()
            if (name.isEmpty() || name.lowercase() in existingFolders) continue
            if (folderRepository.create(name, file.color, file.icon) != null) foldersAdded++
        }

        val charactersJson = json.encodeToString(
            ListSerializer(CharacterFile.serializer()),
            backup.characters,
        )
        val report = importCharacters(charactersJson)
        return report.copy(foldersCreated = report.foldersCreated + foldersAdded)
    }

    private suspend fun applySettings(settings: BackupSettings) {
        settings.theme?.let { value ->
            AppTheme.entries.firstOrNull { it.name == value }?.let { settingsRepository.setTheme(it) }
        }
        settings.language?.let { value ->
            AppLanguage.entries.firstOrNull { it.name == value }?.let { settingsRepository.setLanguage(it) }
        }
        settings.sortOrder?.let { value ->
            SortOrder.entries.firstOrNull { it.name == value }?.let { settingsRepository.setSortOrder(it) }
        }
        settings.dynamicColor?.let { settingsRepository.setDynamicColor(it) }
        settings.gridColumns?.let { settingsRepository.setGridColumns(it) }
        settings.overlayHint?.let { settingsRepository.setOverlayHint(it) }
        settings.animateTransitions?.let { settingsRepository.setAnimateTransitions(it) }
        settingsRepository.setFavorite(settings.favoriteCharacterId)

        // Настройки производительности: в бэкапах до 1.1.0 их просто нет.
        settings.avatarMaxPx?.let { settingsRepository.setAvatarMaxPx(it) }
        settings.avatarQuality?.let { settingsRepository.setAvatarQuality(it) }
        settings.blurHashEnabled?.let { settingsRepository.setBlurHash(it) }
        settings.thumbnailAvatars?.let { settingsRepository.setThumbnailAvatars(it) }
        settings.imageCrossfade?.let { settingsRepository.setImageCrossfade(it) }
        settings.imageMemoryCachePercent?.let { settingsRepository.setImageMemoryCachePercent(it) }
        settings.diskCacheMb?.let { settingsRepository.setDiskCacheMb(it) }
        settings.analyticsEnabled?.let { settingsRepository.setAnalyticsEnabled(it) }
    }
}
