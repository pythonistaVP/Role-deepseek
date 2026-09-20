package com.pythonistavp.roledeepseek.di

import javax.inject.Qualifier

/** Пометка дисковой работы: импорт, экспорт, обработка картинок. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
