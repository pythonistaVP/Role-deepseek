package com.pythonistavp.roledeepseek.data.model

/** Почему импорт не удался — UI переводит это в строку ресурса. */
enum class ImportFailure { NOT_JSON, UNKNOWN_FORMAT, EMPTY, READ_ERROR }

/** Промежуточная карточка: доменная модель + «сырой» аватар до сохранения. */
data class DraftCard(
    val draft: Character,
    val folderName: String? = null,
    val avatarBase64: String? = null,
    val avatarUrl: String? = null,
)

/** Итог импорта. */
data class ImportReport(
    val imported: Int = 0,
    val skipped: Int = 0,
    val foldersCreated: Int = 0,
    val characters: List<Character> = emptyList(),
    val failure: ImportFailure? = null,
) {
    val isSuccess: Boolean get() = failure == null
}
