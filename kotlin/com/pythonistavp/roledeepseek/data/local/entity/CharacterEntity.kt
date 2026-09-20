package com.pythonistavp.roledeepseek.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Таблица персонажей. */
@Entity(
    tableName = "characters",
    indices = [Index("folderId"), Index("pinned"), Index("updatedAt")],
)
data class CharacterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarPath: String? = null,
    val avatarBlurHash: String? = null,
    val prompt: String = "",
    val greeting: String? = null,
    val tags: List<String> = emptyList(),
    val folderId: String? = null,
    val temperature: Float = 1.0f,
    val topP: Float = 0.9f,
    val note: String? = null,
    val language: String = "ru",
    val pinned: Boolean = false,
    val isPublic: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val version: Int = 1,
)
