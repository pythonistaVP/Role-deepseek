package com.pythonistavp.roledeepseek.ui.folders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.Folder
import com.pythonistavp.roledeepseek.ui.components.CharacterAvatar
import com.pythonistavp.roledeepseek.ui.components.ConfirmDialog
import com.pythonistavp.roledeepseek.ui.components.EmptyState
import com.pythonistavp.roledeepseek.ui.components.FolderPickerDialog
import com.pythonistavp.roledeepseek.ui.components.TextInputDialog
import com.pythonistavp.roledeepseek.ui.theme.FolderColors
import kotlin.math.roundToInt

private const val UNFILED_KEY = "__unfiled__"

private data class DragPayload(val character: Character, val position: Offset)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoldersScreen(
    onBack: () -> Unit,
    viewModel: FoldersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var createDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Folder?>(null) }
    var deleteTarget by remember { mutableStateOf<Folder?>(null) }
    var moveTarget by remember { mutableStateOf<Character?>(null) }
    var colorIndex by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf<DragPayload?>(null) }
    val dropZones = remember { mutableStateMapOf<String, Rect>() }
    val chipBounds = remember { mutableStateMapOf<String, Rect>() }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            context.resources.getString(current.resId, *current.args.toTypedArray()),
        )
        viewModel.consumeMessage()
    }

    val hovered = dragging?.let { payload ->
        dropZones.entries.firstOrNull { it.value.contains(payload.position) }?.key
    }

    fun resolveDrop() {
        val payload = dragging
        dragging = null
        if (payload == null) return
        val zone = dropZones.entries.firstOrNull { it.value.contains(payload.position) }?.key ?: return
        val folderId = if (zone == UNFILED_KEY) null else zone
        if (folderId != payload.character.folderId) {
            viewModel.moveCharacter(payload.character.id, folderId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.folders_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { createDialog = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.folders_new)) },
                )
            },
        ) { padding ->
            if (state.folders.isEmpty() && state.characters.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Folder,
                    title = stringResource(R.string.folders_title),
                    description = stringResource(R.string.folders_empty),
                    actionLabel = stringResource(R.string.folders_new),
                    onAction = { createDialog = true },
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.folders_drag_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    items(state.folders, key = { it.id }) { folder ->
                        FolderRow(
                            folder = folder,
                            count = state.counts[folder.id] ?: 0,
                            highlighted = hovered == folder.id,
                            onRename = { renameTarget = folder },
                            onDelete = { deleteTarget = folder },
                            onBounds = { rect -> dropZones[folder.id] = rect },
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { dropZones[UNFILED_KEY] = it.boundsInRoot() },
                            colors = CardDefaults.cardColors(
                                containerColor = if (hovered == UNFILED_KEY) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            ),
                            border = if (hovered == UNFILED_KEY) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null)
                                    Text(
                                        text = stringResource(R.string.folders_unfiled),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 12.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.folders_characters_count, state.unfiled.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                                if (hovered == UNFILED_KEY) {
                                    Text(
                                        text = stringResource(R.string.folders_drop_here),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.detail_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.characters.forEach { character ->
                                DraggableChip(
                                    character = character,
                                    onBounds = { rect -> chipBounds[character.id] = rect },
                                    onDragStart = { offset ->
                                        val bounds = chipBounds[character.id]
                                        val start = bounds?.topLeft ?: Offset.Zero
                                        dragging = DragPayload(character, start + offset)
                                    },
                                    onDrag = { delta ->
                                        dragging = dragging?.let { it.copy(position = it.position + delta) }
                                    },
                                    onDragEnd = { resolveDrop() },
                                    onDragCancel = { dragging = null },
                                    onClick = { moveTarget = character },
                                )
                            }
                        }
                    }
                }
            }
        }

        // «Летящая» карточка под пальцем.
        dragging?.let { payload ->
            Box(
                modifier = Modifier.offset {
                    IntOffset(
                        (payload.position.x - 60).roundToInt(),
                        (payload.position.y - 30).roundToInt(),
                    )
                },
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CharacterAvatar(character = payload.character, size = 28.dp)
                        Text(
                            text = payload.character.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (createDialog) {
        TextInputDialog(
            title = stringResource(R.string.folders_new),
            label = stringResource(R.string.folders_name_hint),
            onConfirm = { name ->
                viewModel.createFolder(
                    name,
                    FolderColors[colorIndex % FolderColors.size],
                    "star",
                )
                colorIndex += 1
                createDialog = false
            },
            onDismiss = { createDialog = false },
        )
    }

    renameTarget?.let { folder ->
        TextInputDialog(
            title = stringResource(R.string.folders_rename_title),
            label = stringResource(R.string.folders_name_hint),
            initialValue = folder.name,
            onConfirm = { name ->
                viewModel.renameFolder(folder, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.folders_delete_title),
            message = stringResource(R.string.folders_delete_message, folder.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteFolder(folder)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    moveTarget?.let { character ->
        FolderPickerDialog(
            folders = state.folders,
            selectedFolderId = character.folderId,
            onPick = { folderId ->
                viewModel.moveCharacter(character.id, folderId)
                moveTarget = null
            },
            onCreate = { name ->
                val existing = state.folders.firstOrNull { it.name.equals(name.trim(), true) }
                if (existing != null) {
                    // Папка с таким именем уже есть — просто кладём персонажа туда.
                    viewModel.moveCharacter(character.id, existing.id)
                } else {
                    viewModel.createFolder(name, FolderColors.first(), "star") { folderId ->
                        if (folderId != null) viewModel.moveCharacter(character.id, folderId)
                    }
                }
                moveTarget = null
            },
            onDismiss = { moveTarget = null },
        )
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    count: Int,
    highlighted: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBounds: (Rect) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val color = Color(folder.color)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBounds(it.boundsInRoot()) },
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (highlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = folder.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.folders_characters_count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (highlighted) {
                Text(
                    text = stringResource(R.string.folders_drop_here),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DraggableChip(
    character: Character,
    onBounds: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .pointerInput(character.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onDragStart(offset) },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterAvatar(character = character, size = 28.dp)
            Text(
                text = character.name,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Icon(
                Icons.Rounded.DragIndicator,
                contentDescription = stringResource(R.string.folders_drag_hint),
                modifier = Modifier.size(16.dp).padding(start = 4.dp),
            )
        }
    }
}
