package com.pythonistavp.roledeepseek.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pythonistavp.roledeepseek.data.model.AppLanguage
import com.pythonistavp.roledeepseek.data.model.AppSettings
import com.pythonistavp.roledeepseek.data.model.AppTheme
import com.pythonistavp.roledeepseek.data.model.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "role_deepseek_settings",
)

/** Все пользовательские настройки и черновик редактора. */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val OVERLAY_HINT = booleanPreferencesKey("overlay_hint")
        val ANIMATIONS = booleanPreferencesKey("animate_transitions")
        val FAVORITE_ID = stringPreferencesKey("favorite_character_id")
        val TAG_HISTORY = stringPreferencesKey("tag_history")
        val DRAFT = stringPreferencesKey("editor_draft")

        // Производительность
        val AVATAR_PX = intPreferencesKey("perf_avatar_px")
        val AVATAR_QUALITY = intPreferencesKey("perf_avatar_quality")
        val BLUR_HASH = booleanPreferencesKey("perf_blur_hash")
        val THUMBNAILS = booleanPreferencesKey("perf_thumbnails")
        val CROSSFADE = booleanPreferencesKey("perf_crossfade")
        val MEMORY_CACHE = intPreferencesKey("perf_memory_cache_percent")
        val DISK_CACHE = intPreferencesKey("perf_disk_cache_mb")
        val ANALYTICS = booleanPreferencesKey("perf_analytics")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs -> read(prefs) }

    /** Черновик формы редактора (JSON) — автосохранение раз в 3 секунды. */
    val draft: Flow<String?> = context.settingsDataStore.data.map { it[Keys.DRAFT] }

    suspend fun current(): AppSettings = settings.first()

    /**
     * Изменение настроек одной транзакцией: читаем текущие, применяем [transform],
     * пишем все ключи сразу. Так пресет производительности меняет 9 полей
     * атомарно, а не девятью отдельными записями.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(current()).sanitized()
        edit { prefs -> write(prefs, updated) }
    }

    suspend fun setTheme(theme: AppTheme) = update { it.copy(theme = theme) }

    suspend fun setLanguage(language: AppLanguage) = update { it.copy(language = language) }

    suspend fun setSortOrder(order: SortOrder) = update { it.copy(sortOrder = order) }

    suspend fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    suspend fun setGridColumns(columns: Int) = update { it.copy(gridColumns = columns) }

    suspend fun setOverlayHint(enabled: Boolean) = update { it.copy(overlayHintEnabled = enabled) }

    suspend fun setAnimateTransitions(enabled: Boolean) =
        update { it.copy(animateTransitions = enabled) }

    suspend fun setFavorite(id: String?) = update { it.copy(favoriteCharacterId = id) }

    // --- Производительность ---

    suspend fun setAvatarMaxPx(px: Int) = update { it.copy(avatarMaxPx = px) }

    suspend fun setAvatarQuality(quality: Int) = update { it.copy(avatarQuality = quality) }

    suspend fun setBlurHash(enabled: Boolean) = update { it.copy(blurHashEnabled = enabled) }

    suspend fun setThumbnailAvatars(enabled: Boolean) = update { it.copy(thumbnailAvatars = enabled) }

    suspend fun setImageCrossfade(enabled: Boolean) = update { it.copy(imageCrossfade = enabled) }

    suspend fun setImageMemoryCachePercent(percent: Int) =
        update { it.copy(imageMemoryCachePercent = percent) }

    suspend fun setDiskCacheMb(megabytes: Int) = update { it.copy(diskCacheMb = megabytes) }

    suspend fun setAnalyticsEnabled(enabled: Boolean) = update { it.copy(analyticsEnabled = enabled) }

    suspend fun addTagToHistory(tag: String) {
        val clean = tag.trim()
        if (clean.isEmpty()) return
        edit { prefs ->
            val current = decodeTags(prefs[Keys.TAG_HISTORY]).filterNot { it.equals(clean, true) }
            prefs[Keys.TAG_HISTORY] = encodeTags((listOf(clean) + current).take(MAX_TAGS))
        }
    }

    /** История тегов в порядке «сначала самые свежие». */
    suspend fun rememberTags(tags: List<String>) {
        val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        edit { prefs ->
            val existing = decodeTags(prefs[Keys.TAG_HISTORY])
            // Идём с конца: последний тег карточки попадёт в начало списка.
            val merged = (clean.reversed() + existing).distinctBy { it.lowercase() }.take(MAX_TAGS)
            prefs[Keys.TAG_HISTORY] = encodeTags(merged)
        }
    }

    suspend fun clearTagHistory() = edit { it.remove(Keys.TAG_HISTORY) }

    suspend fun saveDraft(json: String?) = edit { prefs ->
        if (json.isNullOrBlank()) prefs.remove(Keys.DRAFT) else prefs[Keys.DRAFT] = json
    }

    suspend fun readDraft(): String? = draft.first()

    /** Сброс к значениям по умолчанию: тема, язык, интерфейс — персонажи не трогаются. */
    suspend fun resetToDefaults() {
        val favorite = readFavorite()
        edit { prefs ->
            prefs.clear()
            write(prefs, AppSettings().copy(favoriteCharacterId = favorite))
        }
    }

    private suspend fun readFavorite(): String? = settings.first().favoriteCharacterId

    private fun read(prefs: Preferences): AppSettings = AppSettings(
        theme = enumOr(prefs[Keys.THEME], AppTheme.DARK),
        language = enumOr(prefs[Keys.LANGUAGE], AppLanguage.SYSTEM),
        sortOrder = enumOr(prefs[Keys.SORT_ORDER], SortOrder.DATE),
        dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
        gridColumns = prefs[Keys.GRID_COLUMNS] ?: AppSettings.GRID_DEFAULT,
        overlayHintEnabled = prefs[Keys.OVERLAY_HINT] ?: false,
        animateTransitions = prefs[Keys.ANIMATIONS] ?: true,
        favoriteCharacterId = prefs[Keys.FAVORITE_ID],
        tagHistory = decodeTags(prefs[Keys.TAG_HISTORY]),
        avatarMaxPx = prefs[Keys.AVATAR_PX] ?: AppSettings.AVATAR_PX_DEFAULT,
        avatarQuality = prefs[Keys.AVATAR_QUALITY] ?: AppSettings.AVATAR_QUALITY_DEFAULT,
        blurHashEnabled = prefs[Keys.BLUR_HASH] ?: true,
        thumbnailAvatars = prefs[Keys.THUMBNAILS] ?: true,
        imageCrossfade = prefs[Keys.CROSSFADE] ?: true,
        imageMemoryCachePercent = prefs[Keys.MEMORY_CACHE] ?: AppSettings.CACHE_PERCENT_DEFAULT,
        diskCacheMb = prefs[Keys.DISK_CACHE] ?: AppSettings.DISK_CACHE_DEFAULT_MB,
        analyticsEnabled = prefs[Keys.ANALYTICS] ?: true,
    ).sanitized()

    private fun write(prefs: MutablePreferences, settings: AppSettings) {
        val s = settings.sanitized()
        prefs[Keys.THEME] = s.theme.name
        prefs[Keys.LANGUAGE] = s.language.name
        prefs[Keys.SORT_ORDER] = s.sortOrder.name
        prefs[Keys.DYNAMIC_COLOR] = s.dynamicColor
        prefs[Keys.GRID_COLUMNS] = s.gridColumns
        prefs[Keys.OVERLAY_HINT] = s.overlayHintEnabled
        prefs[Keys.ANIMATIONS] = s.animateTransitions
        if (s.favoriteCharacterId == null) {
            prefs.remove(Keys.FAVORITE_ID)
        } else {
            prefs[Keys.FAVORITE_ID] = s.favoriteCharacterId
        }
        prefs[Keys.TAG_HISTORY] = encodeTags(s.tagHistory)
        prefs[Keys.AVATAR_PX] = s.avatarMaxPx
        prefs[Keys.AVATAR_QUALITY] = s.avatarQuality
        prefs[Keys.BLUR_HASH] = s.blurHashEnabled
        prefs[Keys.THUMBNAILS] = s.thumbnailAvatars
        prefs[Keys.CROSSFADE] = s.imageCrossfade
        prefs[Keys.MEMORY_CACHE] = s.imageMemoryCachePercent
        prefs[Keys.DISK_CACHE] = s.diskCacheMb
        prefs[Keys.ANALYTICS] = s.analyticsEnabled
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun encodeTags(tags: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), tags)

    private fun decodeTags(value: String?): List<String> =
        value?.let {
            runCatching { Json.decodeFromString(ListSerializer(String.serializer()), it) }
                .getOrDefault(emptyList())
        } ?: emptyList()

    private companion object {
        const val MAX_TAGS = 40
    }
}
