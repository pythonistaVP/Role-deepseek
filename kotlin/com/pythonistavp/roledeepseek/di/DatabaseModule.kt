package com.pythonistavp.roledeepseek.di

import android.content.Context
import androidx.room.Room
import com.pythonistavp.roledeepseek.data.local.RoleDeepSeekDatabase
import com.pythonistavp.roledeepseek.data.local.dao.CharacterDao
import com.pythonistavp.roledeepseek.data.local.dao.FolderDao
import com.pythonistavp.roledeepseek.data.local.dao.UsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RoleDeepSeekDatabase =
        Room.databaseBuilder(context, RoleDeepSeekDatabase::class.java, RoleDeepSeekDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCharacterDao(database: RoleDeepSeekDatabase): CharacterDao = database.characterDao()

    @Provides
    fun provideFolderDao(database: RoleDeepSeekDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideUsageDao(database: RoleDeepSeekDatabase): UsageDao = database.usageDao()
}
