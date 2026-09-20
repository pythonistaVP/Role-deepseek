package com.pythonistavp.roledeepseek.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pythonistavp.roledeepseek.data.local.dao.CharacterDao
import com.pythonistavp.roledeepseek.data.local.dao.FolderDao
import com.pythonistavp.roledeepseek.data.local.dao.UsageDao
import com.pythonistavp.roledeepseek.data.local.entity.CharacterEntity
import com.pythonistavp.roledeepseek.data.local.entity.FolderEntity
import com.pythonistavp.roledeepseek.data.local.entity.UsageEntity

@Database(
    entities = [
        CharacterEntity::class,
        FolderEntity::class,
        UsageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RoleDeepSeekDatabase : RoomDatabase() {

    abstract fun characterDao(): CharacterDao

    abstract fun folderDao(): FolderDao

    abstract fun usageDao(): UsageDao

    companion object {
        const val NAME = "role_deepseek.db"
    }
}
