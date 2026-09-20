package com.pythonistavp.roledeepseek.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythonistavp.roledeepseek.data.model.AppSettings
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.domain.usecase.ApplyLanguageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val applyLanguage: ApplyLanguageUseCase,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )

    init {
        // Сохранённый язык применяем один раз при запуске.
        viewModelScope.launch { applyLanguage() }
    }
}
