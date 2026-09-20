package com.pythonistavp.roledeepseek.data.repository

import com.pythonistavp.roledeepseek.data.local.entity.CharacterEntity
import com.pythonistavp.roledeepseek.data.local.entity.FolderEntity
import com.pythonistavp.roledeepseek.data.local.entity.UsageEntity
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.Folder
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.model.UsageRecord

fun CharacterEntity.toDomain(): Character = Character(
    id = id,
    name = name,
    avatarPath = avatarPath,
    avatarBlurHash = avatarBlurHash,
    prompt = prompt,
    greeting = greeting,
    tags = tags,
    folderId = folderId,
    temperature = temperature,
    topP = topP,
    note = note,
    language = language,
    pinned = pinned,
    isPublic = isPublic,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
)

fun Character.toEntity(): CharacterEntity = CharacterEntity(
    id = id,
    name = name,
    avatarPath = avatarPath,
    avatarBlurHash = avatarBlurHash,
    prompt = prompt,
    greeting = greeting,
    tags = tags,
    folderId = folderId,
    temperature = temperature,
    topP = topP,
    note = note,
    language = language,
    pinned = pinned,
    isPublic = isPublic,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
)

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    name = name,
    color = color,
    icon = icon,
    orderIndex = orderIndex,
)

fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id,
    name = name,
    color = color,
    icon = icon,
    orderIndex = orderIndex,
)

fun UsageEntity.toDomain(): UsageRecord = UsageRecord(
    id = id,
    characterId = characterId,
    action = UsageAction.fromName(action) ?: UsageAction.VIEW,
    timestamp = timestamp,
    detail = detail,
)
