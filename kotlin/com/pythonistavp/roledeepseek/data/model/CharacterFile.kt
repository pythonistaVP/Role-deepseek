package com.pythonistavp.roledeepseek.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Родной формат Role DeepSeek: role_deepseek_v1. */
@Serializable
data class CharacterFile(
    val spec: String = SPEC,
    val id: String? = null,
    val name: String = "",
    @SerialName("avatar_base64") val avatarBase64: String? = null,
    val prompt: String = "",
    val greeting: String? = null,
    val tags: List<String> = emptyList(),
    val folder: String? = null,
    val temperature: Float = 1.0f,
    @SerialName("top_p") val topP: Float = 0.9f,
    val note: String? = null,
    val language: String = "ru",
    val pinned: Boolean = false,
    @SerialName("is_public") val isPublic: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L,
    val version: Int = 1,
    val author: String = AUTHOR,
) {
    companion object {
        const val SPEC = "role_deepseek_v1"
        const val AUTHOR = "pythonistaVP"
    }
}

fun Character.toFile(avatarBase64: String?, folderName: String?): CharacterFile = CharacterFile(
    spec = CharacterFile.SPEC,
    id = id,
    name = name,
    avatarBase64 = avatarBase64,
    prompt = prompt,
    greeting = greeting,
    tags = tags,
    folder = folderName,
    temperature = temperature,
    topP = topP,
    note = note,
    language = language,
    pinned = pinned,
    isPublic = isPublic,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
)

fun CharacterFile.toDomain(fallbackId: String): Character = Character(
    id = id ?: fallbackId,
    name = name.trim(),
    prompt = prompt,
    greeting = greeting,
    tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
    temperature = temperature.coerceIn(0f, 2f),
    topP = topP.coerceIn(0f, 1f),
    note = note,
    language = language.ifBlank { "ru" },
    pinned = pinned,
    isPublic = isPublic,
    createdAt = createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
    updatedAt = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
    version = version,
)

/** Файл папки внутри резервной копии. */
@Serializable
data class FolderFile(
    val id: String? = null,
    val name: String = "",
    val color: Long = Folder.DEFAULT_COLOR,
    val icon: String = "star",
    @SerialName("order_index") val orderIndex: Int = 0,
)

@Serializable
data class BackupSettings(
    val theme: String? = null,
    val language: String? = null,
    @SerialName("sort_order") val sortOrder: String? = null,
    @SerialName("dynamic_color") val dynamicColor: Boolean? = null,
    @SerialName("grid_columns") val gridColumns: Int? = null,
    @SerialName("overlay_hint") val overlayHint: Boolean? = null,
    @SerialName("animate_transitions") val animateTransitions: Boolean? = null,
    @SerialName("favorite_character_id") val favoriteCharacterId: String? = null,

    // Производительность. Все поля необязательные: старые бэкапы (v1.0.0)
    // читаются как есть, а новые поля просто остаются со значениями по умолчанию.
    @SerialName("avatar_max_px") val avatarMaxPx: Int? = null,
    @SerialName("avatar_quality") val avatarQuality: Int? = null,
    @SerialName("blur_hash") val blurHashEnabled: Boolean? = null,
    @SerialName("thumbnail_avatars") val thumbnailAvatars: Boolean? = null,
    @SerialName("image_crossfade") val imageCrossfade: Boolean? = null,
    @SerialName("image_memory_cache_percent") val imageMemoryCachePercent: Int? = null,
    @SerialName("disk_cache_mb") val diskCacheMb: Int? = null,
    @SerialName("analytics_enabled") val analyticsEnabled: Boolean? = null,
)

/** role_deepseek_backup_v1 — вся библиотека одним файлом. */
@Serializable
data class BackupFile(
    val spec: String = SPEC,
    @SerialName("exported_at") val exportedAt: Long = 0L,
    @SerialName("app_version") val appVersion: String = "1.1.0-beta",
    val characters: List<CharacterFile> = emptyList(),
    val folders: List<FolderFile> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
) {
    companion object {
        const val SPEC = "role_deepseek_backup_v1"
    }
}
