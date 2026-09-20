package com.pythonistavp.roledeepseek.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pythonistavp.roledeepseek.R

/** Теги: ввод с Enter, чипы, автодополнение по истории. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsInput(
    tags: List<String>,
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val filtered = remember(text, suggestions, tags) {
        val query = text.trim()
        suggestions
            .filter { suggestion -> tags.none { it.equals(suggestion, true) } }
            .filter { query.isBlank() || it.contains(query, true) }
            .take(8)
    }

    fun submit() {
        val value = text.trim()
        if (value.isNotEmpty()) {
            onAdd(value)
            text = ""
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.editor_tags)) },
            placeholder = { Text(stringResource(R.string.editor_tags_hint)) },
            singleLine = true,
            trailingIcon = {
                if (text.isNotBlank()) {
                    IconButton(onClick = { submit() }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_create))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_remove),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        if (filtered.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filtered.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { onAdd(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
            }
        }
    }
}
