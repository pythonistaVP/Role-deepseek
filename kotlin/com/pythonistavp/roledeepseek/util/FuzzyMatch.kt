package com.pythonistavp.roledeepseek.util

/**
 * Нечёткий поиск: сначала точное вхождение подстроки, иначе — символы по
 * порядку («дтк нур» → «Детектив Нуар»). Возвращает и диапазоны для подсветки.
 *
 * Поиск выполняется на каждый символ ввода и по всем персонажам, поэтому:
 * строка-кандидат приводится к нижнему регистру один раз, а не по разу на слово,
 * и токены запроса считаются заранее (см. [queryTokens]).
 */
object FuzzyMatch {

    fun matches(haystack: String, needle: String): Boolean {
        val tokens = tokens(needle)
        if (tokens.isEmpty()) return true
        val lower = haystack.lowercase()
        return tokens.all { token -> findToken(lower, token) != null }
    }

    /** Больше — лучше; -1, если запрос не найден. */
    fun score(haystack: String, needle: String): Int =
        score(haystack.lowercase(), tokens(needle))

    /**
     * Быстрый вариант: [haystackLower] уже в нижнем регистре, [queryTokens] —
     * результат [queryTokens] для запроса.
     */
    fun score(haystackLower: String, queryTokens: List<String>): Int {
        if (queryTokens.isEmpty()) return 0
        var total = 0
        for (token in queryTokens) {
            val hit = findToken(haystackLower, token) ?: return -1
            total += hit.score
        }
        return total
    }

    /** Готовые токены запроса — считаем один раз на весь проход фильтрации. */
    fun queryTokens(needle: String): List<String> = tokens(needle)

    /** Диапазоны символов для подсветки (не пересекаются, отсортированы). */
    fun highlightRanges(haystack: String, needle: String): List<IntRange> {
        if (haystack.isEmpty()) return emptyList()
        val lower = haystack.lowercase()
        val ranges = mutableListOf<IntRange>()
        for (token in tokens(needle)) {
            when (val hit = findToken(lower, token)) {
                null -> Unit
                is Hit.Substring -> ranges += hit.start until (hit.start + token.length)
                is Hit.Subsequence -> hit.indices.forEach { ranges += it..it }
            }
        }
        if (ranges.isEmpty()) return emptyList()
        val merged = mutableListOf<IntRange>()
        for (range in ranges.sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged.filter { it.first in haystack.indices }
    }

    private sealed class Hit(val score: Int) {
        class Substring(val start: Int) : Hit(1000 - start)
        class Subsequence(val indices: List<Int>) : Hit(100 - (indices.firstOrNull() ?: 0))
    }

    /** [lower] обязан быть уже приведён к нижнему регистру. */
    private fun findToken(lower: String, token: String): Hit? {
        if (token.isEmpty()) return null
        val index = lower.indexOf(token)
        if (index >= 0) return Hit.Substring(index)
        val indices = mutableListOf<Int>()
        var cursor = 0
        for (char in token) {
            val found = lower.indexOf(char, cursor)
            if (found < 0) return null
            indices += found
            cursor = found + 1
        }
        return Hit.Subsequence(indices)
    }

    private fun tokens(needle: String): List<String> =
        needle.lowercase().split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
}
