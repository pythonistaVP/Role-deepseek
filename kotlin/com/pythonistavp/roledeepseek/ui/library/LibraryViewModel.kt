package com.pythonistavp.roledeepseek.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.AppSettings
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.Folder
import com.pythonistavp.roledeepseek.data.model.FolderIcon
import com.pythonistavp.roledeepseek.data.model.ImportFailure
import com.pythonistavp.roledeepseek.data.model.ImportReport
import com.pythonistavp.roledeepseek.data.model.SortOrder
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.FolderRepository
import com.pythonistavp.roledeepseek.data.repository.SettingsRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import com.pythonistavp.roledeepseek.domain.CharacterQuery
import com.pythonistavp.roledeepseek.domain.FolderFilter
import com.pythonistavp.roledeepseek.domain.usecase.CharacterActionsUseCase
import com.pythonistavp.roledeepseek.domain.usecase.DeleteCharactersUseCase
import com.pythonistavp.roledeepseek.domain.usecase.ExportCharactersUseCase
import com.pythonistavp.roledeepseek.domain.usecase.FilterCharactersUseCase
import com.pythonistavp.roledeepseek.domain.usecase.ImportCharactersUseCase
import com.pythonistavp.roledeepseek.ui.components.UiMessage
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val characters: List<Character> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val query: CharacterQuery = CharacterQuery(),
    val selection: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val tags: List<String> = emptyList(),
    val loading: Boolean = true,
) {
    val selectionMode: Boolean get() = selection.isNotEmpty()

    fun folderName(id: String?): String? =
        id?.let { folderId -> folders.firstOrNull { it.id == folderId }?.name }

    fun selectedCharacters(): List<Character> = characters.filter { selection.contains(it.id) }
}

/** Что именно экспортируем. */
sealed interface ExportTarget {
    data class Single(val character: Character) : ExportTarget
    data class Selection(val characters: List<Character>) : ExportTarget
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    characterRepository: CharacterRepository,
    private val folderRepository: FolderRepository,
    usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val filterCharacters: FilterCharactersUseCase,
    private val characterActions: CharacterActionsUseCase,
    private val deleteCharacters: DeleteCharactersUseCase,
    private val exportCharacters: ExportCharactersUseCase,
    private val importCharacters: ImportCharactersUseCase,
) : ViewModel() {

    private val query = MutableStateFlow(CharacterQuery())
    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<UiMessage?>(null)

    private val preferences = combine(
        query,
        selection,
        settingsRepository.settings,
    ) { query, selection, settings -> Triple(query, selection, settings) }

    val state: StateFlow<LibraryUiState> = combine(
        characterRepository.observeAll(),
        folderRepository.observeAll(),
        usageRepository.observeChatCounts(),
        preferences,
    ) { characters, folders, usage, prefs ->
        val (currentQuery, currentSelection, settings) = prefs
        val counts = usage.associate { it.characterId to it.uses }
        LibraryUiState(
            characters = filterCharacters(characters, currentQuery, counts),
            folders = folders,
            query = currentQuery,
            selection = currentSelection,
            settings = settings,
            tags = characters.flatMap { it.tags }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(24),
            loading = false,
        )
        // Фильтрация, нечёткий поиск и сортировка крутятся в фоновом потоке:
        // на большой библиотеке это заметная работа, и на главном потоке она давала рывки.
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val messages: StateFlow<UiMessage?> = message

    fun consumeMessage() {
        message.value = null
    }

    fun setSearch(text: String) {
        query.value = query.value.copy(search = text)
    }

    fun setFolderFilter(filter: FolderFilter) {
        query.value = query.value.copy(folder = filter)
    }

    fun setSort(order: SortOrder) {
        query.value = query.value.copy(sort = order)
        viewModelScope.launch { settingsRepository.setSortOrder(order) }
    }

    fun clearFilters() {
        query.value = query.value.copy(search = "", folder = FolderFilter.All, tag = null)
    }

    fun toggleSelection(id: String) {
        selection.value = selection.value.let { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun startSelection(id: String) {
        selection.value = selection.value + id
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun togglePin(character: Character) {
        viewModelScope.launch {
            characterActions.setPinned(character.id, !character.pinned)
            message.value = UiMessage.of(
                if (character.pinned) R.string.toast_unpinned else R.string.toast_pinned,
            )
        }
    }

    fun duplicate(character: Character) {
        viewModelScope.launch {
            val copy = characterActions.duplicate(character)
            message.value = UiMessage.of(R.string.toast_duplicated, copy.name)
        }
    }

    fun move(ids: List<String>, folderId: String?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            characterActions.moveToFolder(ids, folderId)
            selection.value = emptySet()
            val name = folderRepository.getById(folderId ?: return@launch)?.name ?: return@launch
            message.value = UiMessage.of(R.string.folders_moved, name)
        }
    }

    fun createFolder(name: String, onCreated: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val folder = folderRepository.create(name, Folder.DEFAULT_COLOR, FolderIcon.STAR.key)
            message.value = if (folder != null) {
                UiMessage.of(R.string.toast_folder_created, folder.name)
            } else {
                UiMessage.of(R.string.toast_folder_exists, name.trim())
            }
            onCreated(folder?.id)
        }
    }

    fun delete(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            deleteCharacters(ids)
            selection.value = emptySet()
            message.value = UiMessage.of(R.string.toast_deleted)
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { DocumentIO.readText(context, uri) }
            if (text == null) {
                message.value = UiMessage.of(R.string.import_error_read)
                return@launch
            }
            val report = importCharacters(text)
            message.value = report.toMessage()
        }
    }

    fun importText(text: String) {
        viewModelScope.launch {
            message.value = importCharacters(text).toMessage()
        }
    }

    /** В буфере обмена нечего импортировать — говорим об этом вместо тишины. */
    fun onClipboardEmpty() {
        message.value = UiMessage.of(R.string.editor_clipboard_empty)
    }

    fun exportFileName(character: Character): String = exportCharacters.fileName(character)

    fun export(target: ExportTarget, uri: Uri) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                when (target) {
                    is ExportTarget.Single -> exportCharacters(target.character)
                    is ExportTarget.Selection -> exportCharacters.many(target.characters)
                }
            }
            val fileName = when (target) {
                is ExportTarget.Single -> exportCharacters.fileName(target.character)
                is ExportTarget.Selection -> exportCharacters.fileNameList(target.characters.size)
            }
            val written = withContext(Dispatchers.IO) { DocumentIO.writeText(context, uri, json) }
            val exported = when (target) {
                is ExportTarget.Single -> listOf(target.character)
                is ExportTarget.Selection -> target.characters
            }
            exported.forEach { exportCharacters.recordExport(it) }
            message.value = UiMessage.of(
                if (written) R.string.export_success else R.string.export_error,
                fileName,
            )
        }
    }

    fun shareText(character: Character, footer: String) {
        ShareUtil.shareText(context, character.name, buildShareText(character, footer))
        viewModelScope.launch { characterActions.recordUsage(character.id, UsageAction.SHARE) }
    }

    fun shareAsFile(character: Character, chooserTitle: String) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { exportCharacters(character) }
            ShareUtil.shareJsonFile(context, exportCharacters.fileName(character), json, chooserTitle)
            characterActions.recordUsage(character.id, UsageAction.SHARE)
        }
    }

    /** Текст для «Поделиться персонажем». */
    fun buildShareText(character: Character, footer: String): String = buildString {
        append(character.name).append('\n')
        if (character.tags.isNotEmpty()) {
            append(character.tags.joinToString(", ") { "#$it" }).append('\n')
        }
        append('\n')
        character.greeting?.takeIf { it.isNotBlank() }?.let { append(it.trim()).append("\n\n") }
        append(character.prompt.trim())
        if (footer.isNotBlank()) append("\n\n").append(footer)
    }

    private fun ImportReport.toMessage(): UiMessage = when {
        failure != null -> UiMessage.of(failure.messageRes())
        skipped > 0 -> UiMessage.of(R.string.import_success_skipped, imported, skipped)
        else -> UiMessage.of(R.string.import_success, imported)
    }

    private fun ImportFailure?.messageRes(): Int = when (this) {
        ImportFailure.NOT_JSON -> R.string.import_error_not_json
        ImportFailure.UNKNOWN_FORMAT -> R.string.import_error_unknown_format
        ImportFailure.EMPTY -> R.string.import_error_empty
        ImportFailure.READ_ERROR, null -> R.string.import_error_read
    }
}
