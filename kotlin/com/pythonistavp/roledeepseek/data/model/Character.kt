package com.pythonistavp.roledeepseek.data.model

/**
 * Доменная модель персонажа. Не зависит ни от Room, ни от форматов импорта —
 * сюда сходятся все источники: редактор, SillyTavern, Character.AI, резервная копия.
 */
data class Character(
    val id: String,
    val name: String,
    val avatarPath: String? = null,
    val avatarBlurHash: String? = null,
    val prompt: String = "",
    val greeting: String? = null,
    val tags: List<String> = emptyList(),
    val folderId: String? = null,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topP: Float = DEFAULT_TOP_P,
    val note: String? = null,
    val language: String = "ru",
    val pinned: Boolean = false,
    val isPublic: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val version: Int = CURRENT_VERSION,
) {
    /**
     * Итоговый промпт, который уходит в DeepSeek:
     * приветствие → основной промпт → контекст (имя, теги, параметры выборки).
     */
    fun buildChatPrompt(): String = buildString {
        greeting?.takeIf { it.isNotBlank() }?.let { append(it.trim()).append("\n\n") }
        append(prompt.trim())
        val context = buildList {
            add("имя=$name")
            if (tags.isNotEmpty()) add("теги=${tags.joinToString(", ")}")
            if (temperature != DEFAULT_TEMPERATURE) add("temperature=$temperature")
            if (topP != DEFAULT_TOP_P) add("top_p=$topP")
        }
        append("\n\n[контекст: ").append(context.joinToString("; ")).append("]")
    }

    fun promptPreview(maxLength: Int = 140): String =
        prompt.replace(Regex("\\s+"), " ").trim().let {
            if (it.length <= maxLength) it else it.take(maxLength).trimEnd() + "…"
        }

    companion object {
        const val DEFAULT_TEMPERATURE = 1.0f
        const val DEFAULT_TOP_P = 0.9f
        const val CURRENT_VERSION = 1
    }
}
