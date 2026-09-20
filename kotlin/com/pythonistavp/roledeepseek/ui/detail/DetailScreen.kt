package com.pythonistavp.roledeepseek.ui.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.ui.components.CharacterAvatar
import com.pythonistavp.roledeepseek.ui.components.ConfirmDialog
import com.pythonistavp.roledeepseek.ui.components.SectionHeader
import com.pythonistavp.roledeepseek.ui.components.StatRow
import com.pythonistavp.roledeepseek.util.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onStartChat: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qr.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> if (uri != null) viewModel.export(uri) }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            context.resources.getString(current.resId, *current.args.toTypedArray()),
        )
        viewModel.consumeMessage()
    }

    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { state.character?.let { onEdit(it.id) } },
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (state.isFavorite) R.string.action_remove
                                            else R.string.settings_star,
                                        ),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (state.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = null,
                                    )
                                },
                                onClick = { menuOpen = false; viewModel.toggleFavorite() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_qr)) },
                                leadingIcon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) },
                                onClick = { menuOpen = false; viewModel.showQr() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_as_text)) },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.shareText(context.getString(R.string.share_footer))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_as_file)) },
                                leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.shareFile(context.getString(R.string.share_character_title))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_export)) },
                                leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    exportLauncher.launch(viewModel.exportFileName())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.duplicate { newId -> onOpenCharacter(newId) }
                                },
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
                                onClick = { menuOpen = false; deleteDialog = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val character = state.character
        if (character == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CharacterAvatar(character = character, size = 144.dp)
            Text(
                text = character.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (character.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    character.tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag) })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onStartChat(character.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Launch, contentDescription = null)
                    Text(
                        text = stringResource(R.string.card_start_chat),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                FilledTonalButton(onClick = { onEdit(character.id) }) {
                    Text(stringResource(R.string.action_edit))
                }
            }

            SectionHeader(title = stringResource(R.string.detail_stats))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatRow(
                        label = stringResource(R.string.detail_stat_chats),
                        value = state.counters.chats.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.detail_stat_copies),
                        value = state.counters.copies.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.detail_stat_views),
                        value = state.counters.views.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.detail_stat_edits),
                        value = state.counters.edits.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.detail_created),
                        value = Formatters.dateTime(character.createdAt),
                    )
                    StatRow(
                        label = stringResource(R.string.detail_updated),
                        value = Formatters.dateTime(character.updatedAt),
                    )
                    if (state.counters.lastUsedAt != null) {
                        StatRow(
                            label = stringResource(R.string.detail_stat_last_used),
                            value = Formatters.dateTime(state.counters.lastUsedAt ?: 0L),
                        )
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.detail_prompt))
            TextCard(
                text = character.prompt,
                onCopy = { viewModel.copyPrompt() },
                copyLabel = stringResource(R.string.action_copy),
            )

            if (!character.greeting.isNullOrBlank()) {
                SectionHeader(title = stringResource(R.string.detail_greeting))
                TextCard(text = character.greeting.orEmpty(), onCopy = null, copyLabel = null)
            }

            if (!character.note.isNullOrBlank()) {
                SectionHeader(title = stringResource(R.string.detail_note))
                TextCard(text = character.note.orEmpty(), onCopy = null, copyLabel = null)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            ) {
                FilledTonalButton(
                    onClick = { viewModel.shareText(context.getString(R.string.share_footer)) },
                ) { Text(stringResource(R.string.action_share)) }
                FilledTonalButton(
                    onClick = { exportLauncher.launch(viewModel.exportFileName()) },
                ) { Text(stringResource(R.string.action_export)) }
                FilledTonalButton(onClick = { viewModel.showQr() }) {
                    Text(stringResource(R.string.editor_qr))
                }
                FilledTonalButton(
                    onClick = { viewModel.duplicate { newId -> onOpenCharacter(newId) } },
                ) { Text(stringResource(R.string.action_duplicate)) }
                TextButton(onClick = { deleteDialog = true }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (deleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.editor_delete_title),
            message = stringResource(
                R.string.editor_delete_message,
                state.character?.name.orEmpty(),
            ),
            confirmLabel = stringResource(R.string.action_delete_forever),
            destructive = true,
            onConfirm = {
                viewModel.delete()
                deleteDialog = false
            },
            onDismiss = { deleteDialog = false },
        )
    }

    qrBitmap?.let { bitmap ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissQr() },
            title = { Text(stringResource(R.string.editor_qr_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.editor_qr_title),
                        modifier = Modifier.size(240.dp),
                    )
                    Text(
                        text = stringResource(R.string.editor_qr_description),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissQr() }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun TextCard(text: String, onCopy: (() -> Unit)?, copyLabel: String?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            SelectionContainer {
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
            if (onCopy != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onCopy) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = copyLabel ?: stringResource(R.string.action_copy),
                        )
                    }
                }
            }
        }
    }
}
