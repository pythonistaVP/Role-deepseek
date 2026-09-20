package com.pythonistavp.roledeepseek.domain

import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.SortOrder

/** Фильтр по папкам и служебным подборкам. */
sealed interface FolderFilter {
    data object All : FolderFilter
    data object Pinned : FolderFilter
    data object Unfiled : FolderFilter
    data class InFolder(val folderId: String) : FolderFilter
}

/** Что показывает библиотека прямо сейчас. */
data class CharacterQuery(
    val search: String = "",
    val folder: FolderFilter = FolderFilter.All,
    val sort: SortOrder = SortOrder.DATE,
    val tag: String? = null,
)

/** Всё, по чему ищем: имя, промпт, заметка, теги, приветствие. */
fun Character.searchableText(): String = buildString {
    append(name)
    if (prompt.isNotEmpty()) append(' ').append(prompt)
    greeting?.let { append(' ').append(it) }
    note?.let { append(' ').append(it) }
    if (tags.isNotEmpty()) append(' ').append(tags.joinToString(" "))
}
