package com.pythonistavp.roledeepseek.ui.chatlauncher

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.ui.components.CharacterAvatar
import com.pythonistavp.roledeepseek.ui.components.SectionHeader
import com.pythonistavp.roledeepseek.util.ShareUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatLauncherScreen(
    onBack: () -> Unit,
    viewModel: ChatLauncherViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val copiedText = stringResource(R.string.chat_snackbar_copied)
    val clipboardAction = stringResource(R.string.chat_snackbar_action)

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            context.resources.getString(current.resId, *current.args.toTypedArray()),
        )
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            state.character?.let { character ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterAvatar(character = character, size = 64.dp)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (character.tags.isNotEmpty()) {
                            Text(
                                text = character.tags.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.chat_prompt_preview))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SelectionContainer {
                        Text(
                            text = state.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = { viewModel.copyPrompt() }) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.openDeepSeek()
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = copiedText,
                            actionLabel = clipboardAction,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.showClipboard()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Icon(Icons.Rounded.Launch, contentDescription = null)
                Text(
                    text = stringResource(R.string.chat_open_deepseek),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                FilledTonalButton(onClick = { viewModel.copyPrompt() }) {
                    Text(stringResource(R.string.chat_copy_prompt))
                }
                FilledTonalButton(onClick = { viewModel.copyGreeting() }) {
                    Text(stringResource(R.string.chat_copy_greeting))
                }
                AssistChip(
                    onClick = {
                        val character = state.character
                        val text = buildString {
                            if (character != null) append(character.name).append("\n\n")
                            append(state.prompt)
                            append("\n\n")
                            append(context.getString(R.string.share_footer))
                        }
                        ShareUtil.shareText(
                            context,
                            context.getString(R.string.chat_share_prompt),
                            text,
                        )
                        viewModel.recordShare()
                    },
                    label = { Text(stringResource(R.string.chat_share_prompt)) },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }

            SectionHeader(title = stringResource(R.string.chat_steps_title))
            listOf(
                R.string.chat_step_1,
                R.string.chat_step_2,
                R.string.chat_step_3,
            ).forEachIndexed { index, res ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.chat_overlay_title))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_overlay_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.overlayHintEnabled,
                    onCheckedChange = { viewModel.setOverlayHint(it) },
                )
            }
            Text(
                text = stringResource(R.string.chat_overlay_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (state.overlayHintEnabled && !state.overlayPermissionGranted) {
                TextButton(onClick = { viewModel.requestOverlayPermission() }) {
                    Text(stringResource(R.string.chat_overlay_grant))
                }
            }
        }
    }

    if (state.confirmVisible && state.character != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirm() },
            title = {
                Text(stringResource(R.string.chat_copied_dialog_title, state.character?.name.orEmpty()))
            },
            text = { Text(stringResource(R.string.chat_copied_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.openDeepSeek()
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = copiedText,
                                actionLabel = clipboardAction,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.showClipboard()
                        }
                    },
                ) { Text(stringResource(R.string.chat_open_deepseek)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirm() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    state.clipboardPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissClipboard() },
            title = { Text(stringResource(R.string.chat_snackbar_action)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.chat_clipboard_preview,
                        preview.take(400),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissClipboard() }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}
