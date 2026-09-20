package com.pythonistavp.roledeepseek.ui.detail

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.CharacterCounters
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import com.pythonistavp.roledeepseek.domain.usecase.CharacterActionsUseCase
import com.pythonistavp.roledeepseek.domain.usecase.DeleteCharactersUseCase
import com.pythonistavp.roledeepseek.domain.usecase.ExportCharactersUseCase
import com.pythonistavp.roledeepseek.domain.usecase.FavoriteCharacterUseCase
import com.pythonistavp.roledeepseek.ui.components.UiMessage
import com.pythonistavp.roledeepseek.util.ClipboardUtil
import com.pythonistavp.roledeepseek.util.DocumentIO
import com.pythonistavp.roledeepseek.util.ShareUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailUiState(
    val character: Character? = null,
    val counters: CharacterCounters = CharacterCounters(),
    val isFavorite: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    characterRepository: CharacterRepository,
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val characterActions: CharacterActionsUseCase,
    private val deleteCharacters: DeleteCharactersUseCase,
    private val exportCharacters: ExportCharactersUseCase,
    private val favoriteCharacter: FavoriteCharacterUseCase,
) : ViewModel() {

    private val characterId: String = savedStateHandle.get<String>(ARG_CHARACTER_ID).orEmpty()

    private val counters = MutableStateFlow(CharacterCounters())

    val state: StateFlow<DetailUiState> = combine(
        characterRepository.observeById(characterId),
        settingsRepository.settings,
        counters,
    ) { character, settings, counters ->
        DetailUiState(
            character = character,
            counters = counters,
            isFavorite = settings.favoriteCharacterId == characterId,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    private val _message = MutableStateFlow<UiMessage?>(null)
    val messages: StateFlow<UiMessage?> = _message

    private val _qr = MutableStateFlow<Bitmap?>(null)
    val qr: StateFlow<Bitmap?> = _qr

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    init {
        viewModelScope.launch {
            characterRepository.getById(characterId)?.let {
                characterActions.recordUsage(it.id, UsageAction.VIEW)
            }
            refreshCounters()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun dismissQr() {
        _qr.value = null
    }

    fun copyPrompt() {
        val character = state.value.character ?: return
        ClipboardUtil.copy(context, character.name, character.buildChatPrompt())
        viewModelScope.launch {
            characterActions.recordUsage(character.id, UsageAction.COPY_PROMPT)
            refreshCounters()
        }
        _message.value = UiMessage.of(R.string.detail_copied)
    }

    fun shareText(footer: String) {
        val character = state.value.character ?: return
        val text = buildString {
            append(character.name).append("\n\n")
            append(character.buildChatPrompt())
            append("\n\n").append(footer)
        }
        ShareUtil.shareText(context, character.name, text)
        viewModelScope.launch { characterActions.recordUsage(character.id, UsageAction.SHARE) }
    }

    fun shareFile(chooserTitle: String) {
        val character = state.value.character ?: return
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { exportCharacters(character) }
            ShareUtil.shareJsonFile(context, exportCharacters.fileName(character), json, chooserTitle)
            characterActions.recordUsage(character.id, UsageAction.SHARE)
        }
    }

    fun export(uri: Uri) {
        val character = state.value.character ?: return
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { exportCharacters(character) }
            val written = withContext(Dispatchers.IO) { DocumentIO.writeText(context, uri, json) }
            exportCharacters.recordExport(character)
            _message.value = UiMessage.of(
                if (written) R.string.export_success else R.string.export_error,
                exportCharacters.fileName(character),
            )
        }
    }

    fun exportFileName(): String =
        state.value.character?.let { exportCharacters.fileName(it) } ?: "character.json"

    fun showQr() {
        val character = state.value.character ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { exportCharacters.qr(character) }
            if (bitmap == null) {
                _message.value = UiMessage.of(R.string.editor_qr_failed)
            } else {
                _qr.value = bitmap
            }
        }
    }

    fun duplicate(onCreated: (String) -> Unit) {
        val character = state.value.character ?: return
        viewModelScope.launch {
            val copy = characterActions.duplicate(character)
            _message.value = UiMessage.of(R.string.toast_duplicated, copy.name)
            onCreated(copy.id)
        }
    }

    fun delete() {
        val character = state.value.character ?: return
        viewModelScope.launch {
            deleteCharacters(listOf(character.id))
            _deleted.value = true
        }
    }

    fun toggleFavorite() {
        val character = state.value.character ?: return
        viewModelScope.launch {
            val isFavorite = state.value.isFavorite
            favoriteCharacter.setFavorite(if (isFavorite) null else character.id)
            _message.value = UiMessage.of(
                if (isFavorite) R.string.toast_unpinned else R.string.settings_star,
            )
        }
    }

    private suspend fun refreshCounters() {
        counters.value = usageRepository.counters(characterId)
    }

    companion object {
        const val ARG_CHARACTER_ID = "characterId"
    }
}
