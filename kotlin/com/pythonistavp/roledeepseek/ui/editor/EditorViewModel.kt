package com.pythonistavp.roledeepseek.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.CharacterFile
import com.pythonistavp.roledeepseek.data.model.Folder
import com.pythonistavp.roledeepseek.data.model.FolderIcon
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.model.toDomain
import com.pythonistavp.roledeepseek.data.model.toFile
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.FolderRepository
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.domain.usecase.AvatarUseCase
import com.pythonistavp.roledeepseek.domain.usecase.CharacterActionsUseCase
import com.pythonistavp.roledeepseek.domain.usecase.DeleteCharactersUseCase
import com.pythonistavp.roledeepseek.domain.usecase.ExportCharactersUseCase
import com.pythonistavp.roledeepseek.domain.usecase.SaveCharacterUseCase
import com.pythonistavp.roledeepseek.navigation.Routes
import com.pythonistavp.roledeepseek.ui.components.UiMessage
import com.pythonistavp.roledeepseek.util.DocumentIO
import com.pythonistavp.roledeepseek.util.Ids
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class EditorUiState(
    val id: String = "",
    val name: String = "",
    val prompt: String = "",
    val greeting: String = "",
    val tags: List<String> = emptyList(),
    val folderId: String? = null,
    val folders: List<Folder> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),
    val temperature: Float = Character.DEFAULT_TEMPERATURE,
    val topP: Float = Character.DEFAULT_TOP_P,
    val note: String = "",
    val isPublic: Boolean = false,
    val pinned: Boolean = false,
    val avatarPath: String? = null,
    val avatarBlurHash: String? = null,
    /** Идёт обработка выбранной картинки — показываем крутилку на аватаре. */
    val avatarLoading: Boolean = false,
    val isNew: Boolean = true,
    val loading: Boolean = true,
    val draftSaved: Boolean = false,
) {
    fun toCharacter(createdAt: Long): Character = Character(
        id = id,
        name = name,
        avatarPath = avatarPath,
        avatarBlurHash = avatarBlurHash,
        prompt = prompt,
        greeting = greeting.ifBlank { null },
        tags = tags,
        folderId = folderId,
        temperature = temperature,
        topP = topP,
        note = note.ifBlank { null },
        pinned = pinned,
        isPublic = isPublic,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
    )
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val characterRepository: CharacterRepository,
    private val folderRepository: FolderRepository,
    private val settingsRepository: SettingsRepository,
    private val saveCharacter: SaveCharacterUseCase,
    private val deleteCharacters: DeleteCharactersUseCase,
    private val exportCharacters: ExportCharactersUseCase,
    private val avatarUseCase: AvatarUseCase,
    private val characterActions: CharacterActionsUseCase,
    private val json: Json,
) : ViewModel() {

    private val routeId: String? =
        savedStateHandle.get<String>(ARG_CHARACTER_ID)?.takeIf { it.isNotBlank() }

    // Текст, которым с нами «поделились» (ACTION_SEND), приходит в base64url.
    private val prefill: String? = savedStateHandle.get<String>(ARG_PREFILL)
        ?.takeIf { it.isNotBlank() }
        ?.let { Routes.decode(it) ?: it }

    private var createdAt: Long = System.currentTimeMillis()
    private var autosaveJob: Job? = null

    /**
     * Черновик уже неактуален: персонаж сохранён или черновик удалён.
     * Без этого флага [onCleared] дописывал бы черновик обратно уже после
     * сохранения/удаления — и «сохранённый» персонаж снова всплывал в редакторе.
     */
    private var draftFinalized = false

    /**
     * Черновик пишется и при выходе с экрана: viewModelScope на этот момент уже
     * отменён, поэтому нужен отдельный скоуп (одна быстрая запись в DataStore).
     */
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(
        EditorUiState(id = routeId ?: Ids.newId(), isNew = routeId == null),
    )
    val state: StateFlow<EditorUiState> = _state

    private val _message = MutableStateFlow<UiMessage?>(null)
    val messages: StateFlow<UiMessage?> = _message

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    private val _qr = MutableStateFlow<Bitmap?>(null)
    val qr: StateFlow<Bitmap?> = _qr

    /** QR-код карточки: сжатый JSON, который влезает в один код. */
    fun showQr() {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                exportCharacters.qr(_state.value.toCharacter(createdAt))
            }
            if (bitmap == null) {
                _message.value = UiMessage.of(R.string.editor_qr_failed)
            } else {
                _qr.value = bitmap
            }
        }
    }

    fun dismissQr() {
        _qr.value = null
    }

    init {
        viewModelScope.launch {
            val folders = folderRepository.getAll()
            val suggestions = settingsRepository.current().tagHistory

            if (routeId == null) {
                val restored = settingsRepository.readDraft()
                    ?.let { draft -> runCatching { json.decodeFromString(CharacterFile.serializer(), draft) }.getOrNull() }
                _state.value = _state.value.copy(
                    name = restored?.name.orEmpty(),
                    prompt = prefill ?: restored?.prompt.orEmpty(),
                    greeting = restored?.greeting.orEmpty(),
                    tags = restored?.tags.orEmpty(),
                    temperature = restored?.temperature ?: Character.DEFAULT_TEMPERATURE,
                    topP = restored?.topP ?: Character.DEFAULT_TOP_P,
                    note = restored?.note.orEmpty(),
                    isPublic = restored?.isPublic ?: false,
                    folders = folders,
                    tagSuggestions = suggestions,
                    loading = false,
                )
            } else {
                val character = characterRepository.getById(routeId)
                if (character == null) {
                    _message.value = UiMessage.of(R.string.editor_name_required)
                    _finished.value = true
                    return@launch
                }
                createdAt = character.createdAt
                _state.value = _state.value.copy(
                    name = character.name,
                    prompt = character.prompt,
                    greeting = character.greeting.orEmpty(),
                    tags = character.tags,
                    folderId = character.folderId,
                    temperature = character.temperature,
                    topP = character.topP,
                    note = character.note.orEmpty(),
                    isPublic = character.isPublic,
                    pinned = character.pinned,
                    avatarPath = character.avatarPath,
                    avatarBlurHash = character.avatarBlurHash,
                    folders = folders,
                    tagSuggestions = suggestions,
                    loading = false,
                )
                characterActions.recordUsage(character.id, UsageAction.VIEW)
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun onNameChange(value: String) = update { it.copy(name = value) }

    fun onPromptChange(value: String) = update { it.copy(prompt = value) }

    fun onGreetingChange(value: String) = update { it.copy(greeting = value) }

    fun onNoteChange(value: String) = update { it.copy(note = value) }

    fun onTemperatureChange(value: Float) = update { it.copy(temperature = value) }

    fun onTopPChange(value: Float) = update { it.copy(topP = value) }

    fun onPublicChange(value: Boolean) = update { it.copy(isPublic = value) }

    fun onFolderChange(folderId: String?) = update { it.copy(folderId = folderId) }

    /** Дописывает текст из буфера обмена в конец промпта. */
    fun appendToPrompt(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        update { state ->
            val merged = if (state.prompt.isBlank()) {
                clean
            } else {
                state.prompt.trimEnd() + "\n\n" + clean
            }
            state.copy(prompt = merged)
        }
    }

    fun onClipboardEmpty() {
        _message.value = UiMessage.of(R.string.editor_clipboard_empty)
    }

    fun addTag(tag: String) {
        val clean = tag.trim().removePrefix("#")
        if (clean.isEmpty()) return
        update { state ->
            if (state.tags.any { it.equals(clean, true) }) state
            else state.copy(tags = state.tags + clean)
        }
    }

    fun removeTag(tag: String) = update { it.copy(tags = it.tags - tag) }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val clean = name.trim()
            // Папка с таким именем уже есть — не плодим дубликат (имя UNIQUE), просто выбираем её.
            val existing = folderRepository.getAll().firstOrNull { it.name.equals(clean, true) }
            if (existing != null) {
                _state.value = _state.value.copy(
                    folders = folderRepository.getAll(),
                    folderId = existing.id,
                )
                return@launch
            }
            val folder = folderRepository.create(clean, Folder.DEFAULT_COLOR, FolderIcon.STAR.key)
            if (folder != null) {
                _state.value = _state.value.copy(
                    folders = folderRepository.getAll(),
                    folderId = folder.id,
                )
                _message.value = UiMessage.of(R.string.toast_folder_created, folder.name)
            }
        }
    }

    /** Картинка уже обрезана в uCrop — сохраняем её под id персонажа. */
    fun onAvatarPicked(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(avatarLoading = true)
            val info = avatarUseCase.save(uri, _state.value.id)
            val current = _state.value
            _state.value = current.copy(
                avatarPath = info?.path ?: current.avatarPath,
                avatarBlurHash = if (info != null) info.blurHash else current.avatarBlurHash,
                avatarLoading = false,
            )
            if (info == null) {
                _message.value = UiMessage.of(R.string.editor_avatar_failed)
            }
            cleanTempImages()
        }
    }

    /** Не удалось получить картинку ни из галереи, ни из камеры. */
    fun onAvatarFailed() {
        _state.value = _state.value.copy(avatarLoading = false)
        _message.value = UiMessage.of(R.string.editor_avatar_failed)
    }

    /** Экран обрезки недоступен — картинка сохранена без обрезки. */
    fun onCropUnavailable() {
        _message.value = UiMessage.of(R.string.editor_crop_unavailable)
    }

    /** Камера закрылась без снимка — не оставляем пустой кадр в cacheDir. */
    fun onCameraCancelled() {
        cleanTempImages()
    }

    fun onAvatarCleared() {
        viewModelScope.launch {
            avatarUseCase.clear(_state.value.id)
            _state.value = _state.value.copy(avatarPath = null, avatarBlurHash = null)
        }
    }

    fun applyTemplate(template: String) = update { it.copy(prompt = template) }

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            val result = saveCharacter(current.toCharacter(createdAt), current.isNew)
            when (result) {
                is SaveCharacterUseCase.SaveResult.Saved -> {
                    autosaveJob?.cancel()
                    // Персонаж сохранён — черновик больше не нужен.
                    draftFinalized = true
                    settingsRepository.saveDraft(null)
                    _message.value = UiMessage.of(R.string.editor_saved)
                    _finished.value = true
                }
                SaveCharacterUseCase.SaveResult.EmptyName ->
                    _message.value = UiMessage.of(R.string.editor_name_required)
                SaveCharacterUseCase.SaveResult.EmptyPrompt ->
                    _message.value = UiMessage.of(R.string.editor_prompt_required)
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            autosaveJob?.cancel()
            draftFinalized = true
            if (!_state.value.isNew) deleteCharacters(listOf(_state.value.id))
            settingsRepository.saveDraft(null)
            _finished.value = true
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val character = _state.value.toCharacter(createdAt)
            val payload = withContext(Dispatchers.IO) { exportCharacters(character) }
            val written = withContext(Dispatchers.IO) { DocumentIO.writeText(context, uri, payload) }
            _message.value = UiMessage.of(
                if (written) R.string.export_success else R.string.export_error,
                exportCharacters.fileName(character),
            )
        }
    }

    fun exportFileName(): String =
        exportCharacters.fileName(_state.value.toCharacter(createdAt))

    /** Автосохранение черновика через 3 секунды бездействия. */
    private fun update(transform: (EditorUiState) -> EditorUiState) {
        _state.value = transform(_state.value).copy(draftSaved = false)
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            persistDraft()
        }
    }

    private fun persistDraft() {
        val current = _state.value
        if (!current.isNew || draftFinalized) return
        viewModelScope.launch { writeDraft(current) }
    }

    private suspend fun writeDraft(current: EditorUiState) {
        val payload = runCatching {
            current.toCharacter(createdAt)
                .toFile(avatarBase64 = null, folderName = null)
                .let { json.encodeToString(CharacterFile.serializer(), it) }
        }.getOrNull() ?: return
        settingsRepository.saveDraft(payload)
        _state.value = _state.value.copy(draftSaved = true)
    }

    /** Убираем временные кадры камеры и обрезки — иначе они копятся в cacheDir. */
    private fun cleanTempImages() {
        runCatching {
            TEMP_DIRS.forEach { name ->
                File(context.cacheDir, name).listFiles()?.forEach { it.delete() }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val current = _state.value
        autosaveJob?.cancel()
        // Черновик нового персонажа дописываем даже если ушли с экрана раньше 3 секунд.
        // Но не воскрешаем его после сохранения или удаления.
        if (!current.isNew || draftFinalized) return
        val payload = runCatching {
            current.toCharacter(createdAt)
                .toFile(avatarBase64 = null, folderName = null)
                .let { json.encodeToString(CharacterFile.serializer(), it) }
        }.getOrNull() ?: return
        flushScope.launch { settingsRepository.saveDraft(payload) }
    }

    companion object {
        const val ARG_CHARACTER_ID = "characterId"
        const val ARG_PREFILL = "prefill"
        private const val AUTOSAVE_DELAY_MS = 3000L
        private val TEMP_DIRS = listOf("crop", "camera")
    }
}
