package com.pythonistavp.roledeepseek.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.AppSettings
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.SortOrder
import com.pythonistavp.roledeepseek.domain.FolderFilter
import com.pythonistavp.roledeepseek.ui.components.BetaBadge
import com.pythonistavp.roledeepseek.ui.components.CharacterCard
import com.pythonistavp.roledeepseek.ui.components.ChoiceDialog
import com.pythonistavp.roledeepseek.ui.components.ConfirmDialog
import com.pythonistavp.roledeepseek.ui.components.EmptyState
import com.pythonistavp.roledeepseek.ui.components.FolderPickerDialog
import com.pythonistavp.roledeepseek.util.AppLinks
import com.pythonistavp.roledeepseek.util.ClipboardUtil
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenCharacter: (String) -> Unit,
    onStartChat: (String) -> Unit,
    onEditCharacter: (String?) -> Unit,
    onOpenFolders: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var searchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var importDialog by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<Character?>(null) }
    var moveTarget by remember { mutableStateOf<List<String>?>(null) }
    var moveTargetFolder by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<List<String>?>(null) }
    var pendingExport by remember { mutableStateOf<ExportTarget?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val target = pendingExport
        if (uri != null && target != null) viewModel.export(target, uri)
        pendingExport = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> if (uri != null) viewModel.importFromUri(uri) }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            context.resources.getString(current.resId, *current.args.toTypedArray()),
        )
        viewModel.consumeMessage()
    }

    // Дебаунс 300 мс: печатаем быстро, фильтруем спокойно.
    LaunchedEffect(searchText) {
        if (searchText == state.query.search) return@LaunchedEffect
        delay(300)
        viewModel.setSearch(searchText)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, state.selection.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_clear_selection),
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                moveTargetFolder = state.selectedCharacters()
                                    .firstOrNull()?.folderId
                                moveTarget = state.selection.toList()
                            },
                        ) {
                            Icon(
                                Icons.Rounded.DriveFileMove,
                                contentDescription = stringResource(R.string.library_bulk_move),
                            )
                        }
                        IconButton(
                            onClick = {
                                val selected = state.selectedCharacters()
                                if (selected.isNotEmpty()) {
                                    pendingExport = ExportTarget.Selection(selected)
                                    exportLauncher.launch("role_deepseek_selection.json")
                                }
                            },
                        ) {
                            Icon(
                                Icons.Rounded.FileDownload,
                                contentDescription = stringResource(R.string.library_bulk_export),
                            )
                        }
                        IconButton(onClick = { deleteTarget = state.selection.toList() }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_splash_logo),
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            BetaBadge(modifier = Modifier.padding(start = 6.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = { AppLinks.open(context, AppLinks.GITHUB_AUTHOR) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = "GitHub",
                            )
                        }
                        IconButton(onClick = { searchActive = !searchActive }) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.library_search_hint),
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                        Box {
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_more),
                                )
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_import_button)) },
                                    leadingIcon = { Icon(Icons.Rounded.FileOpen, contentDescription = null) },
                                    onClick = { moreMenu = false; importDialog = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.folders_title)) },
                                    leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                                    onClick = { moreMenu = false; onOpenFolders() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_sort_label)) },
                                    leadingIcon = { Icon(Icons.Rounded.Sort, contentDescription = null) },
                                    onClick = { moreMenu = false; sortMenu = true },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_menu_select)) },
                                    leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                                    onClick = {
                                        moreMenu = false
                                        state.characters.firstOrNull()?.let { viewModel.startSelection(it.id) }
                                    },
                                )
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(order.labelRes())) },
                                        leadingIcon = {
                                            if (state.query.sort == order) {
                                                Icon(Icons.Rounded.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = { sortMenu = false; viewModel.setSort(order) },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        },
        floatingActionButton = {
            if (!state.selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { onEditCharacter(null) },
                    icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_create_character)) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = searchActive) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text(stringResource(R.string.library_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                searchText = ""
                                searchActive = false
                                viewModel.setSearch("")
                            },
                        ) { Text(stringResource(R.string.action_close)) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(48.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.query.folder == FolderFilter.All,
                        onClick = { viewModel.setFolderFilter(FolderFilter.All) },
                        label = { Text(stringResource(R.string.library_filter_all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.query.folder == FolderFilter.Pinned,
                        onClick = { viewModel.setFolderFilter(FolderFilter.Pinned) },
                        label = { Text(stringResource(R.string.library_filter_pinned)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
                item {
                    FilterChip(
                        selected = state.query.folder == FolderFilter.Unfiled,
                        onClick = { viewModel.setFolderFilter(FolderFilter.Unfiled) },
                        label = { Text(stringResource(R.string.folders_unfiled)) },
                    )
                }
                items(state.folders, key = { it.id }) { folder ->
                    FilterChip(
                        selected = state.query.folder == FolderFilter.InFolder(folder.id),
                        onClick = { viewModel.setFolderFilter(FolderFilter.InFolder(folder.id)) },
                        label = { Text(folder.name) },
                    )
                }
            }

            if (state.characters.isEmpty() && !state.loading) {
                if (state.query.search.isNotBlank()) {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = stringResource(R.string.library_empty_search_title),
                        description = stringResource(R.string.library_empty_search_description),
                        actionLabel = stringResource(R.string.action_reset),
                        onAction = {
                            searchText = ""
                            viewModel.clearFilters()
                        },
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.PersonAdd,
                        title = stringResource(R.string.library_empty_title),
                        description = stringResource(R.string.library_empty_description),
                        actionLabel = stringResource(R.string.library_create_character),
                        onAction = { onEditCharacter(null) },
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(
                        state.settings.gridColumns.coerceIn(
                            AppSettings.GRID_MIN,
                            AppSettings.GRID_MAX,
                        ),
                    ),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.characters, key = { it.id }) { character ->
                        CharacterCard(
                            character = character,
                            query = state.query.search,
                            folderName = state.folderName(character.folderId),
                            selected = state.selection.contains(character.id),
                            selectionMode = state.selectionMode,
                            onClick = {
                                if (state.selectionMode) {
                                    viewModel.toggleSelection(character.id)
                                } else {
                                    onOpenCharacter(character.id)
                                }
                            },
                            onLongClick = { viewModel.startSelection(character.id) },
                            onStartChat = { onStartChat(character.id) },
                            onEdit = { onEditCharacter(character.id) },
                            onTogglePin = { viewModel.togglePin(character) },
                            onToggleSelect = { viewModel.toggleSelection(character.id) },
                            onMove = {
                                moveTargetFolder = character.folderId
                                moveTarget = listOf(character.id)
                            },
                            onDuplicate = { viewModel.duplicate(character) },
                            onExport = {
                                pendingExport = ExportTarget.Single(character)
                                exportLauncher.launch(viewModel.exportFileName(character))
                            },
                            onShare = { shareTarget = character },
                            onDelete = { deleteTarget = listOf(character.id) },
                        )
                    }
                }
            }
        }
    }

    if (importDialog) {
        ChoiceDialog(
            title = stringResource(R.string.library_import_button),
            options = listOf(
                Icons.Rounded.FileOpen to stringResource(R.string.action_import),
                Icons.Rounded.ContentCopy to stringResource(R.string.import_from_clipboard),
            ),
            onPick = { index ->
                importDialog = false
                when (index) {
                    0 -> importLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream"),
                    )
                    else -> {
                        val text = ClipboardUtil.read(context)
                        if (text.isNullOrBlank()) {
                            viewModel.onClipboardEmpty()
                        } else {
                            viewModel.importText(text)
                        }
                    }
                }
            },
            onDismiss = { importDialog = false },
        )
    }

    shareTarget?.let { character ->
        ChoiceDialog(
            title = stringResource(R.string.share_choose),
            options = listOf(
                Icons.Rounded.Share to stringResource(R.string.share_as_text),
                Icons.Rounded.FileDownload to stringResource(R.string.share_as_file),
            ),
            onPick = { index ->
                if (index == 0) {
                    viewModel.shareText(character, context.getString(R.string.share_footer))
                } else {
                    viewModel.shareAsFile(
                        character,
                        context.getString(R.string.share_character_title),
                    )
                }
                shareTarget = null
            },
            onDismiss = { shareTarget = null },
        )
    }

    moveTarget?.let { ids ->
        FolderPickerDialog(
            folders = state.folders,
            selectedFolderId = moveTargetFolder,
            onPick = { folderId ->
                viewModel.move(ids, folderId)
                moveTarget = null
            },
            onCreate = { name ->
                val existing = state.folders.firstOrNull { it.name.equals(name.trim(), true) }
                if (existing != null) {
                    viewModel.move(ids, existing.id)
                } else {
                    viewModel.createFolder(name) { folderId ->
                        if (folderId != null) viewModel.move(ids, folderId)
                    }
                }
                moveTarget = null
            },
            onDismiss = { moveTarget = null },
        )
    }

    deleteTarget?.let { ids ->
        ConfirmDialog(
            title = stringResource(R.string.library_bulk_delete_title),
            message = stringResource(R.string.library_bulk_delete_message, ids.size),
            confirmLabel = stringResource(R.string.action_delete_forever),
            destructive = true,
            onConfirm = {
                viewModel.delete(ids)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.DATE -> R.string.library_sort_date
    SortOrder.NAME -> R.string.library_sort_name
    SortOrder.PINNED -> R.string.library_sort_pinned
    SortOrder.USAGE -> R.string.library_sort_usage
}
