package com.pythonistavp.roledeepseek.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Таблица событий локальной аналитики. */
@Entity(
    tableName = "usage_events",
    indices = [Index("characterId"), Index("action"), Index("timestamp")],
)
data class UsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val characterId: String,
    val action: String,
    val timestamp: Long,
    val detail: String? = null,
)
