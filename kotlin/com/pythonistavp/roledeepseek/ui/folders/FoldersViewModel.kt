package com.pythonistavp.roledeepseek.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.Folder
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.FolderRepository
import com.pythonistavp.roledeepseek.domain.usecase.CharacterActionsUseCase
import com.pythonistavp.roledeepseek.ui.components.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FoldersUiState(
    val folders: List<Folder> = emptyList(),
    val characters: List<Character> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val loading: Boolean = true,
) {
    val unfiled: List<Character> get() = characters.filter { it.folderId == null }

    fun charactersIn(folderId: String): List<Character> =
        characters.filter { it.folderId == folderId }
}

@HiltViewModel
class FoldersViewModel @Inject constructor(
    characterRepository: CharacterRepository,
    private val folderRepository: FolderRepository,
    private val characterActions: CharacterActionsUseCase,
) : ViewModel() {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val messages: StateFlow<UiMessage?> = _message

    val state: StateFlow<FoldersUiState> = combine(
        folderRepository.observeAll(),
        characterRepository.observeAll(),
    ) { folders, characters ->
        FoldersUiState(
            folders = folders,
            characters = characters,
            counts = characters
                .mapNotNull { it.folderId }
                .groupingBy { it }
                .eachCount(),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * Создаёт папку. null в [onCreated] означает, что папка с таким именем уже есть
     * (или имя пустое) — вызывающий код может сказать об этом пользователю.
     */
    fun createFolder(
        name: String,
        color: Long,
        icon: String,
        onCreated: (String?) -> Unit = {},
    ) {
        viewModelScope.launch {
            val folder = folderRepository.create(name, color, icon)
            _message.value = if (folder != null) {
                UiMessage.of(R.string.toast_folder_created, folder.name)
            } else {
                UiMessage.of(R.string.toast_folder_exists, name.trim())
            }
            onCreated(folder?.id)
        }
    }

    fun renameFolder(folder: Folder, name: String) {
        viewModelScope.launch {
            // false = имя уже занято другой папкой (иначе UPDATE нарушил бы UNIQUE).
            if (!folderRepository.rename(folder.id, name)) {
                _message.value = UiMessage.of(R.string.toast_folder_exists, name.trim())
            }
        }
    }

    fun updateFolder(folder: Folder) {
        viewModelScope.launch { folderRepository.update(folder) }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            folderRepository.delete(folder.id)
            _message.value = UiMessage.of(R.string.toast_deleted)
        }
    }

    fun moveCharacter(characterId: String, folderId: String?) {
        viewModelScope.launch {
            characterActions.moveToFolder(listOf(characterId), folderId)
            val name = folderId?.let { id -> folderRepository.getById(id)?.name }
            if (name != null) _message.value = UiMessage.of(R.string.folders_moved, name)
        }
    }
}
