package com.pythonistavp.roledeepseek.data.model

/** Тема приложения. */
enum class AppTheme { SYSTEM, LIGHT, DARK, DYNAMIC }

/** Язык интерфейса. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    RU("ru"),
    EN("en"),
}

/** Порядок сортировки библиотеки. */
enum class SortOrder { DATE, NAME, PINNED, USAGE }

/** Действия, попадающие в локальную статистику. */
enum class UsageAction {
    CREATE,
    EDIT,
    VIEW,
    CHAT_LAUNCH,
    COPY_PROMPT,
    COPY_GREETING,
    SHARE,
    EXPORT,
    IMPORT;

    companion object {
        fun fromName(value: String?): UsageAction? =
            entries.firstOrNull { it.name == value }
    }
}

/** Режим импорта резервной копии. */
enum class ImportMode { REPLACE, MERGE }

/** Готовые профили производительности: одна кнопка вместо десятка ползунков. */
enum class PerfPreset {
    /** Максимум качества: большие аватары, blurhash, все анимации. */
    QUALITY,

    /** Разумный компромисс — так приложение работает по умолчанию. */
    BALANCED,

    /** Экономия: маленькие аватары, без подложек и анимаций — для слабых телефонов. */
    BATTERY,
}
