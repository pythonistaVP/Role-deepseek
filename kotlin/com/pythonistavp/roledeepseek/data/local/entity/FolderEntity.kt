package com.pythonistavp.roledeepseek.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Таблица папок (категорий). */
@Entity(
    tableName = "folders",
    indices = [Index(value = ["name"], unique = true)],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long = 0xFF4F5BD5,
    val icon: String = "star",
    val orderIndex: Int = 0,
)
