package com.pythonistavp.roledeepseek.domain.usecase

import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.SortOrder
import com.pythonistavp.roledeepseek.domain.CharacterQuery
import com.pythonistavp.roledeepseek.domain.FolderFilter
import com.pythonistavp.roledeepseek.domain.searchableText
import com.pythonistavp.roledeepseek.util.FuzzyMatch
import javax.inject.Inject

/** Поиск (с нечётким совпадением), фильтр по папкам и сортировка. */
class FilterCharactersUseCase @Inject constructor() {

    operator fun invoke(
        source: List<Character>,
        query: CharacterQuery,
        usageCounts: Map<String, Int> = emptyMap(),
    ): List<Character> {
        var list = source.filter { character ->
            when (val folder = query.folder) {
                FolderFilter.All -> true
                FolderFilter.Pinned -> character.pinned
                FolderFilter.Unfiled -> character.folderId == null
                is FolderFilter.InFolder -> character.folderId == folder.folderId
            }
        }

        query.tag?.let { tag ->
            list = list.filter { character -> character.tags.any { it.equals(tag, true) } }
        }

        val search = query.search.trim()
        if (search.isNotEmpty()) {
            // Токены запроса считаем один раз, а строку каждого персонажа
            // приводим к нижнему регистру тоже один раз (а не на каждое слово).
            val tokens = FuzzyMatch.queryTokens(search)
            list = list
                .mapNotNull { character ->
                    val score = FuzzyMatch.score(character.searchableText().lowercase(), tokens)
                    if (score >= 0) character to score else null
                }
                .sortedByDescending { it.second }
                .map { it.first }
            if (query.sort == SortOrder.DATE || query.sort == SortOrder.USAGE) return list
        }

        return when (query.sort) {
            SortOrder.NAME -> list.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { character: Character -> character.name },
            )
            SortOrder.DATE -> list.sortedByDescending { it.updatedAt }
            SortOrder.PINNED -> list.sortedWith(
                compareByDescending<Character> { it.pinned }.thenByDescending { it.updatedAt },
            )
            SortOrder.USAGE -> list.sortedByDescending { usageCounts[it.id] ?: 0 }
        }
    }
}
