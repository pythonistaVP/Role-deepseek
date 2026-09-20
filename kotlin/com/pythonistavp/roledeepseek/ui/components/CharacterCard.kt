package com.pythonistavp.roledeepseek.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Character

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CharacterCard(
    character: Character,
    query: String,
    folderName: String?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStartChat: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleSelect: () -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Box {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterAvatar(character = character, size = 56.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        HighlightedText(
                            text = character.name,
                            query = query,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        val subtitle = folderName ?: character.tags.firstOrNull()
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                        )
                    }
                }

                Text(
                    text = character.promptPreview().ifBlank { stringResource(R.string.card_no_prompt) },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )

                if (character.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        character.tags.take(2).forEach { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onStartChat,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.card_start_chat),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                }
            }

            if (character.pinned) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = stringResource(R.string.card_pinned),
                    tint = scheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp),
                )
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (character.pinned) R.string.card_unpin else R.string.card_pin,
                            ),
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                    onClick = { menuOpen = false; onTogglePin() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.card_move)) },
                    leadingIcon = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null) },
                    onClick = { menuOpen = false; onMove() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_duplicate)) },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = { menuOpen = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_export)) },
                    leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                    onClick = { menuOpen = false; onExport() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share)) },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    onClick = { menuOpen = false; onShare() },
                )
                if (selectionMode) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_menu_select)) },
                        onClick = { menuOpen = false; onToggleSelect() },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = scheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = scheme.error)
                    },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
