package com.pythonistavp.roledeepseek.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Folder

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.action_cancel),
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

/** Диалог ввода одной строки (имя папки, имя персонажа и т.п.). */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmLabel: String = stringResource(R.string.action_ok),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Выбор папки (в том числе «без папки» и создание новой на лету). */
@Composable
fun FolderPickerDialog(
    folders: List<Folder>,
    selectedFolderId: String?,
    onPick: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    allowUnfiled: Boolean = true,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_move)) },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.editor_folder_new_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { creating = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        TextButton(
                            onClick = { onCreate(newName.trim()) },
                            enabled = newName.isNotBlank(),
                        ) { Text(stringResource(R.string.action_create)) }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        if (allowUnfiled) {
                            item {
                                PickerRow(
                                    label = stringResource(R.string.folders_unfiled),
                                    selected = selectedFolderId == null,
                                    onClick = { onPick(null) },
                                )
                            }
                        }
                        items(folders, key = { it.id }) { folder ->
                            PickerRow(
                                label = folder.name,
                                selected = folder.id == selectedFolderId,
                                onClick = { onPick(folder.id) },
                            )
                        }
                        item {
                            TextButton(
                                onClick = { creating = true },
                                modifier = Modifier.padding(top = 4.dp),
                            ) { Text(stringResource(R.string.editor_folder_new)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!creating) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Диалог-меню из иконок и подписей (импорт, «поделиться» и т.п.). */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<ImageVector, String>>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, (icon, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = icon, contentDescription = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
