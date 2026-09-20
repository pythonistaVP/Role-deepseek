package com.pythonistavp.roledeepseek.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.pythonistavp.roledeepseek.BuildConfig
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.AppLanguage
import com.pythonistavp.roledeepseek.data.model.AppSettings
import com.pythonistavp.roledeepseek.data.model.AppTheme
import com.pythonistavp.roledeepseek.data.model.ImportFailure
import com.pythonistavp.roledeepseek.data.model.ImportMode
import com.pythonistavp.roledeepseek.data.model.ImportReport
import com.pythonistavp.roledeepseek.data.model.LibraryStatistics
import com.pythonistavp.roledeepseek.data.model.PerfPreset
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import com.pythonistavp.roledeepseek.domain.usecase.BackupUseCase
import com.pythonistavp.roledeepseek.domain.usecase.GetStatisticsUseCase
import com.pythonistavp.roledeepseek.ui.components.UiMessage
import com.pythonistavp.roledeepseek.util.DocumentIO
import com.pythonistavp.roledeepseek.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val statistics: LibraryStatistics = LibraryStatistics(),
    val version: String = BuildConfig.VERSION_NAME,
    val build: Int = BuildConfig.VERSION_CODE,
    /** Какой из готовых профилей производительности сейчас активен (если какой-то). */
    val preset: PerfPreset? = PerfPreset.BALANCED,
    val loading: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val usageRepository: UsageRepository,
    private val statistics: GetStatisticsUseCase,
    private val backup: BackupUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    private val _message = MutableStateFlow<UiMessage?>(null)
    val messages: StateFlow<UiMessage?> = _message

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.value = _state.value.copy(
                    settings = settings,
                    preset = presetOf(settings),
                    loading = false,
                )
            }
        }
        refreshStatistics()
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun refreshStatistics() {
        viewModelScope.launch {
            _state.value = _state.value.copy(statistics = statistics())
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { settingsRepository.setGridColumns(columns) }
    }

    fun setOverlayHint(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setOverlayHint(enabled) }
    }

    fun setAnimateTransitions(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAnimateTransitions(enabled) }
    }

    // --- Производительность ---

    fun applyPreset(preset: PerfPreset) {
        viewModelScope.launch {
            settingsRepository.applyPerfPreset(preset)
            _message.value = UiMessage.of(
                R.string.settings_perf_preset_applied,
                context.getString(preset.labelRes()),
            )
        }
    }

    fun setAvatarMaxPx(px: Int) {
        viewModelScope.launch { settingsRepository.setAvatarMaxPx(px) }
    }

    fun setAvatarQuality(quality: Int) {
        viewModelScope.launch { settingsRepository.setAvatarQuality(quality) }
    }

    fun setBlurHash(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBlurHash(enabled) }
    }

    fun setThumbnailAvatars(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setThumbnailAvatars(enabled) }
    }

    fun setImageCrossfade(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setImageCrossfade(enabled) }
    }

    fun setImageMemoryCachePercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setImageMemoryCachePercent(percent) }
    }

    fun setDiskCacheMb(megabytes: Int) {
        viewModelScope.launch { settingsRepository.setDiskCacheMb(megabytes) }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAnalyticsEnabled(enabled) }
    }

    /** Полный сброс: тема, язык, интерфейс, производительность. Персонажи не трогаются. */
    fun resetSettings() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            _message.value = UiMessage.of(R.string.settings_reset_done)
        }
    }

    fun clearAnalytics() {
        viewModelScope.launch {
            usageRepository.clear()
            refreshStatistics()
            _message.value = UiMessage.of(R.string.settings_analytics_cleared)
        }
    }

    /** Чистим кэш Coil (память + диск) — аватары в filesDir не трогаем. */
    fun clearImageCache() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                val loader = Coil.imageLoader(context)
                val bytes = runCatching { loader.diskCache?.size }.getOrNull() ?: 0L
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                bytes
            }
            _message.value = UiMessage.of(R.string.settings_clear_cache_done, Formatters.fileSize(freed))
            refreshStatistics()
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val fileName = backup.fileName()
            val payload = withContext(Dispatchers.IO) { backup.export() }
            val written = withContext(Dispatchers.IO) { DocumentIO.writeText(context, uri, payload) }
            _message.value = UiMessage.of(
                if (written) R.string.backup_success else R.string.export_error,
                fileName,
            )
        }
    }

    fun backupFileName(): String = backup.fileName()

    fun importBackup(uri: Uri, mode: ImportMode) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { DocumentIO.readText(context, uri) }
            if (text == null) {
                _message.value = UiMessage.of(R.string.import_error_read)
                return@launch
            }
            val report = backup.import(text, mode)
            _message.value = report.toMessage()
            refreshStatistics()
        }
    }

    private fun ImportReport.toMessage(): UiMessage = when {
        failure != null -> UiMessage.of(failure.messageRes())
        else -> UiMessage.of(
            R.string.backup_import_result,
            imported,
            skipped,
            foldersCreated,
        )
    }

    private fun ImportFailure?.messageRes(): Int = when (this) {
        ImportFailure.NOT_JSON -> R.string.import_error_not_json
        ImportFailure.UNKNOWN_FORMAT -> R.string.import_error_unknown_format
        ImportFailure.EMPTY -> R.string.import_error_empty
        ImportFailure.READ_ERROR, null -> R.string.import_error_read
    }

    /** Сравниваем текущие значения с профилями — так в UI видно, какой из них активен. */
    private fun presetOf(settings: AppSettings): PerfPreset? {
        val s = settings
        return when {
            s.avatarMaxPx == AppSettings.AVATAR_PX_MAX &&
                s.avatarQuality == AppSettings.AVATAR_QUALITY_MAX &&
                s.blurHashEnabled &&
                s.imageCrossfade &&
                s.animateTransitions &&
                s.analyticsEnabled -> PerfPreset.QUALITY

            s.avatarMaxPx == AppSettings.AVATAR_PX_DEFAULT &&
                s.avatarQuality == AppSettings.AVATAR_QUALITY_DEFAULT &&
                s.blurHashEnabled &&
                s.imageCrossfade &&
                s.animateTransitions &&
                s.analyticsEnabled -> PerfPreset.BALANCED

            s.avatarMaxPx == AppSettings.AVATAR_PX_MIN &&
                !s.blurHashEnabled &&
                !s.imageCrossfade &&
                !s.animateTransitions &&
                !s.analyticsEnabled -> PerfPreset.BATTERY

            else -> null
        }
    }
}

internal fun PerfPreset.labelRes(): Int = when (this) {
    PerfPreset.QUALITY -> R.string.settings_perf_preset_quality
    PerfPreset.BALANCED -> R.string.settings_perf_preset_balanced
    PerfPreset.BATTERY -> R.string.settings_perf_preset_battery
}
