package com.pythonistavp.roledeepseek.data.model

/** Папка (категория) персонажей. */
data class Folder(
    val id: String,
    val name: String,
    val color: Long = DEFAULT_COLOR,
    val icon: String = FolderIcon.STAR.key,
    val orderIndex: Int = 0,
) {
    companion object {
        const val DEFAULT_COLOR = 0xFF4F5BD5
    }
}

/** Иконки папок: ключ хранится в БД, отрисовка — в UI-слое. */
enum class FolderIcon(val key: String) {
    STAR("star"),
    HEART("heart"),
    BOOK("book"),
    MAGIC("magic"),
    GAME("game"),
    ROCKET("rocket"),
    MASK("mask"),
    ROBOT("robot");

    companion object {
        fun fromKey(key: String?): FolderIcon =
            entries.firstOrNull { it.key == key } ?: STAR
    }
}
