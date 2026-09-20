package com.pythonistavp.roledeepseek.data.repository

import com.pythonistavp.roledeepseek.data.datastore.SettingsDataStore
import com.pythonistavp.roledeepseek.data.model.AppLanguage
import com.pythonistavp.roledeepseek.data.model.AppSettings
import com.pythonistavp.roledeepseek.data.model.AppTheme
import com.pythonistavp.roledeepseek.data.model.PerfPreset
import com.pythonistavp.roledeepseek.data.model.SortOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SettingsRepository @Inject constructor(
    private val store: SettingsDataStore,
) {
    val settings: Flow<AppSettings> = store.settings

    suspend fun current(): AppSettings = store.current()

    suspend fun setTheme(theme: AppTheme) = store.setTheme(theme)

    suspend fun setLanguage(language: AppLanguage) = store.setLanguage(language)

    suspend fun setSortOrder(order: SortOrder) = store.setSortOrder(order)

    suspend fun setDynamicColor(enabled: Boolean) = store.setDynamicColor(enabled)

    suspend fun setGridColumns(columns: Int) = store.setGridColumns(columns)

    suspend fun setOverlayHint(enabled: Boolean) = store.setOverlayHint(enabled)

    suspend fun setAnimateTransitions(enabled: Boolean) = store.setAnimateTransitions(enabled)

    suspend fun setFavorite(id: String?) = store.setFavorite(id)

    // --- Производительность ---

    suspend fun setAvatarMaxPx(px: Int) = store.setAvatarMaxPx(px)

    suspend fun setAvatarQuality(quality: Int) = store.setAvatarQuality(quality)

    suspend fun setBlurHash(enabled: Boolean) = store.setBlurHash(enabled)

    suspend fun setThumbnailAvatars(enabled: Boolean) = store.setThumbnailAvatars(enabled)

    suspend fun setImageCrossfade(enabled: Boolean) = store.setImageCrossfade(enabled)

    suspend fun setImageMemoryCachePercent(percent: Int) = store.setImageMemoryCachePercent(percent)

    suspend fun setDiskCacheMb(megabytes: Int) = store.setDiskCacheMb(megabytes)

    suspend fun setAnalyticsEnabled(enabled: Boolean) = store.setAnalyticsEnabled(enabled)

    /**
     * Готовый профиль производительности — одна транзакция вместо девяти.
     * Настройки темы/языка/папок не трогаем: пресет только про ресурсы.
     */
    suspend fun applyPerfPreset(preset: PerfPreset) = store.update { settings ->
        when (preset) {
            PerfPreset.QUALITY -> settings.copy(
                avatarMaxPx = AppSettings.AVATAR_PX_MAX,
                avatarQuality = AppSettings.AVATAR_QUALITY_MAX,
                blurHashEnabled = true,
                thumbnailAvatars = true,
                imageCrossfade = true,
                imageMemoryCachePercent = AppSettings.CACHE_PERCENT_MAX,
                diskCacheMb = AppSettings.DISK_CACHE_MAX_MB,
                animateTransitions = true,
                analyticsEnabled = true,
            )

            PerfPreset.BALANCED -> settings.copy(
                avatarMaxPx = AppSettings.AVATAR_PX_DEFAULT,
                avatarQuality = AppSettings.AVATAR_QUALITY_DEFAULT,
                blurHashEnabled = true,
                thumbnailAvatars = true,
                imageCrossfade = true,
                imageMemoryCachePercent = AppSettings.CACHE_PERCENT_DEFAULT,
                diskCacheMb = AppSettings.DISK_CACHE_DEFAULT_MB,
                animateTransitions = true,
                analyticsEnabled = true,
            )

            PerfPreset.BATTERY -> settings.copy(
                avatarMaxPx = AppSettings.AVATAR_PX_MIN,
                avatarQuality = 70,
                blurHashEnabled = false,
                thumbnailAvatars = true,
                imageCrossfade = false,
                imageMemoryCachePercent = AppSettings.CACHE_PERCENT_MIN,
                diskCacheMb = AppSettings.DISK_CACHE_MIN_MB,
                animateTransitions = false,
                analyticsEnabled = false,
            )
        }
    }

    suspend fun rememberTags(tags: List<String>) = store.rememberTags(tags)

    suspend fun clearTagHistory() = store.clearTagHistory()

    suspend fun saveDraft(json: String?) = store.saveDraft(json)

    suspend fun readDraft(): String? = store.readDraft()

    suspend fun resetToDefaults() = store.resetToDefaults()
}
