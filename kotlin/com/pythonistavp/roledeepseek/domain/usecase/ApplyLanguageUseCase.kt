package com.pythonistavp.roledeepseek.domain.usecase

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.pythonistavp.roledeepseek.data.model.AppLanguage
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import javax.inject.Inject

/** Переключение языка приложения (AppCompatDelegate, работает и до Android 13). */
class ApplyLanguageUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): Boolean {
        val locales = when (settingsRepository.current().language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.RU -> LocaleListCompat.forLanguageTags("ru")
            AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
        }
        if (AppCompatDelegate.getApplicationLocales() == locales) return false
        AppCompatDelegate.setApplicationLocales(locales)
        return true
    }
}
