package com.pythonistavp.roledeepseek.ui.chatlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.domain.usecase.CharacterActionsUseCase
import com.pythonistavp.roledeepseek.domain.usecase.ChatPromptUseCase
import com.pythonistavp.roledeepseek.service.FloatingHintService
import com.pythonistavp.roledeepseek.ui.components.UiMessage
import com.pythonistavp.roledeepseek.util.ClipboardUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatLauncherUiState(
    val character: Character? = null,
    val prompt: String = "",
    val greeting: String = "",
    val confirmVisible: Boolean = false,
    val clipboardPreview: String? = null,
    val overlayHintEnabled: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val deepSeekInstalled: Boolean = false,
    val loading: Boolean = true,
)

/**
 * Шаги запуска чата:
 * 1) собрать итоговый промпт; 2) положить его в буфер обмена;
 * 3) спросить «открыть DeepSeek?»; 4) открыть приложение или сайт;
 * 5) напомнить про «долгое нажатие → Вставить» (по желанию — плавающей подсказкой).
 */
@HiltViewModel
class ChatLauncherViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val characterRepository: CharacterRepository,
    private val settingsRepository: SettingsRepository,
    private val chatPrompt: ChatPromptUseCase,
    private val characterActions: CharacterActionsUseCase,
) : ViewModel() {

    private val characterId: String = savedStateHandle.get<String>(ARG_CHARACTER_ID).orEmpty()

    private val _state = MutableStateFlow(ChatLauncherUiState())
    val state: StateFlow<ChatLauncherUiState> = _state

    private val _message = MutableStateFlow<UiMessage?>(null)
    val messages: StateFlow<UiMessage?> = _message

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val character = characterRepository.getById(characterId)
            if (character == null) {
                _message.value = UiMessage.of(R.string.import_error_empty)
                _state.value = _state.value.copy(loading = false)
                return@launch
            }
            val prompt = chatPrompt.fullPrompt(character)
            val greeting = chatPrompt.greetingOnly(character)
            ClipboardUtil.copy(context, character.name, prompt)
            characterActions.recordUsage(character.id, UsageAction.CHAT_LAUNCH)
            _state.value = ChatLauncherUiState(
                character = character,
                prompt = prompt,
                greeting = greeting,
                confirmVisible = true,
                overlayHintEnabled = settings.overlayHintEnabled,
                overlayPermissionGranted = canDrawOverlays(),
                deepSeekInstalled = chatPrompt.isDeepSeekInstalled(),
                loading = false,
            )
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun dismissConfirm() {
        _state.value = _state.value.copy(confirmVisible = false)
    }

    /** Открывает DeepSeek и, если включено, показывает плавающую подсказку поверх. */
    fun openDeepSeek() {
        _state.value = _state.value.copy(confirmVisible = false)
        val opened = chatPrompt.openDeepSeek()
        if (!opened) {
            _message.value = UiMessage.of(R.string.chat_open_failed)
            return
        }
        if (!_state.value.deepSeekInstalled) {
            _message.value = UiMessage.of(R.string.chat_app_not_found)
        }
        showOverlayIfEnabled()
    }

    fun copyPrompt() {
        val character = _state.value.character ?: return
        ClipboardUtil.copy(context, character.name, _state.value.prompt)
        viewModelScope.launch {
            characterActions.recordUsage(character.id, UsageAction.COPY_PROMPT)
        }
        _message.value = UiMessage.of(R.string.chat_snackbar_copied)
    }

    fun copyGreeting() {
        val character = _state.value.character ?: return
        ClipboardUtil.copy(context, character.name, _state.value.greeting)
        viewModelScope.launch {
            characterActions.recordUsage(character.id, UsageAction.COPY_GREETING)
        }
        _message.value = UiMessage.of(R.string.toast_copied_to_clipboard)
    }

    fun recordShare() {
        val character = _state.value.character ?: return
        viewModelScope.launch { characterActions.recordUsage(character.id, UsageAction.SHARE) }
    }

    fun showClipboard() {
        val text = ClipboardUtil.read(context)
        if (text.isNullOrBlank()) {
            _message.value = UiMessage.of(R.string.editor_clipboard_empty)
        } else {
            _state.value = _state.value.copy(clipboardPreview = text)
        }
    }

    fun dismissClipboard() {
        _state.value = _state.value.copy(clipboardPreview = null)
    }

    fun setOverlayHint(enabled: Boolean) {
        _state.value = _state.value.copy(overlayHintEnabled = enabled)
        viewModelScope.launch { settingsRepository.setOverlayHint(enabled) }
        if (enabled && !canDrawOverlays()) requestOverlayPermission()
        if (enabled) showOverlayIfEnabled()
    }

    fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun refreshOverlayPermission() {
        _state.value = _state.value.copy(overlayPermissionGranted = canDrawOverlays())
    }

    private fun showOverlayIfEnabled() {
        if (!_state.value.overlayHintEnabled) return
        if (!canDrawOverlays()) {
            _message.value = UiMessage.of(R.string.chat_overlay_denied)
            return
        }
        FloatingHintService.start(context)
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    companion object {
        const val ARG_CHARACTER_ID = "characterId"
    }
}
