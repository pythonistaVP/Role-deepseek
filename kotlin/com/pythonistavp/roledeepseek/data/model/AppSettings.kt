package com.pythonistavp.roledeepseek.data.model

/**
 * Пользовательские настройки (DataStore).
 *
 * Вторая половина полей — раздел «Производительность»: качество и размер
 * аватаров, подложки blurhash, кроссфейд картинок, размеры кэшей Coil и
 * локальная статистика. Всё это реально влияет на расход памяти/CPU и батареи,
 * поэтому вынесено наружу вместо жёстко зашитых констант.
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.DARK,          // тёмная тема по умолчанию
    val language: AppLanguage = AppLanguage.SYSTEM,
    val sortOrder: SortOrder = SortOrder.DATE,
    val dynamicColor: Boolean = true,             // Material You на Android 12+
    val gridColumns: Int = GRID_DEFAULT,
    val overlayHintEnabled: Boolean = false,
    val animateTransitions: Boolean = true,
    val favoriteCharacterId: String? = null,
    val tagHistory: List<String> = emptyList(),

    // --- Производительность ---
    /** Сторона сохранённого аватара в пикселях (чем меньше — тем легче списки). */
    val avatarMaxPx: Int = AVATAR_PX_DEFAULT,
    /** Качество JPEG при сохранении аватара. */
    val avatarQuality: Int = AVATAR_QUALITY_DEFAULT,
    /** Считать blurhash-подложку (немного CPU при сохранении, зато плавные списки). */
    val blurHashEnabled: Boolean = true,
    /** Просить Coil уменьшать картинку до размера карточки, а не держать 768px в памяти. */
    val thumbnailAvatars: Boolean = true,
    /** Плавное появление картинок. */
    val imageCrossfade: Boolean = true,
    /** Сколько процентов от лимита памяти процесса отдаём кэшу картинок. */
    val imageMemoryCachePercent: Int = CACHE_PERCENT_DEFAULT,
    /** Размер дискового кэша картинок, МБ. */
    val diskCacheMb: Int = DISK_CACHE_DEFAULT_MB,
    /** Считать локальную статистику (запуски чатов, копирования). */
    val analyticsEnabled: Boolean = true,
) {
    /** Значения уже приведены к допустимым диапазонам (DataStore мог сохранить что угодно). */
    fun sanitized(): AppSettings = copy(
        gridColumns = gridColumns.coerceIn(GRID_MIN, GRID_MAX),
        avatarMaxPx = avatarMaxPx.coerceIn(AVATAR_PX_MIN, AVATAR_PX_MAX),
        avatarQuality = avatarQuality.coerceIn(AVATAR_QUALITY_MIN, AVATAR_QUALITY_MAX),
        imageMemoryCachePercent = imageMemoryCachePercent.coerceIn(CACHE_PERCENT_MIN, CACHE_PERCENT_MAX),
        diskCacheMb = diskCacheMb.coerceIn(DISK_CACHE_MIN_MB, DISK_CACHE_MAX_MB),
    )

    companion object {
        const val AVATAR_PX_DEFAULT = 768
        const val AVATAR_PX_MIN = 256
        const val AVATAR_PX_MAX = 1024

        const val AVATAR_QUALITY_DEFAULT = 90
        const val AVATAR_QUALITY_MIN = 60
        const val AVATAR_QUALITY_MAX = 100

        const val GRID_DEFAULT = 2
        const val GRID_MIN = 1
        /** 5 колонок на телефоне уже нечитаемы — библиотека рисует максимум 4. */
        const val GRID_MAX = 4

        const val CACHE_PERCENT_DEFAULT = 25
        const val CACHE_PERCENT_MIN = 5
        const val CACHE_PERCENT_MAX = 50

        const val DISK_CACHE_DEFAULT_MB = 128
        const val DISK_CACHE_MIN_MB = 32
        const val DISK_CACHE_MAX_MB = 512
    }
}
