package com.pythonistavp.roledeepseek.ui.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.ui.components.CharacterAvatar
import com.pythonistavp.roledeepseek.ui.components.ConfirmDialog
import com.pythonistavp.roledeepseek.ui.components.FolderPickerDialog
import com.pythonistavp.roledeepseek.ui.components.SectionHeader
import com.pythonistavp.roledeepseek.ui.components.TagsInput
import com.pythonistavp.roledeepseek.util.ClipboardUtil
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qr.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var folderPicker by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var cropSource by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // Галерея: системный выбор изображений. Разрешения на чтение галереи не нужны
    // ни на одной версии Android — и диалогов меньше, и отказов.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> if (uri != null) cropSource = uri }

    // Камера пишет кадр в наш FileProvider-URI (cacheDir/camera).
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved: Boolean ->
        val uri = cameraUri
        cameraUri = null
        if (saved && uri != null) {
            cropSource = uri
        } else {
            // Снимок отменён — подчищаем пустой файл камеры.
            viewModel.onCameraCancelled()
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val output = if (result.resultCode == Activity.RESULT_OK && data != null) {
            runCatching { UCrop.getOutput(data) }.getOrNull()
        } else {
            null
        }
        when {
            output != null -> viewModel.onAvatarPicked(output)
            // RESULT_OK без картинки или ошибка внутри uCrop — говорим об этом,
            // но приложение продолжает работать.
            result.resultCode == Activity.RESULT_OK -> viewModel.onAvatarFailed()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> if (uri != null) viewModel.export(uri) }

    // Обрезаем выбранный кадр в квадрат через uCrop. Любая проблема (нет activity,
    // нет FileProvider-пути) — не краш, а сообщение и импорт картинки как есть.
    LaunchedEffect(cropSource) {
        val source = cropSource ?: return@LaunchedEffect
        cropSource = null
        val intent = cropIntent(context, source)
        if (intent == null) {
            viewModel.onAvatarPicked(source)
            return@LaunchedEffect
        }
        val launched = runCatching { cropLauncher.launch(intent) }.isSuccess
        if (!launched) {
            // Экран обрезки недоступен — берём картинку как есть.
            viewModel.onCropUnavailable()
            viewModel.onAvatarPicked(source)
        }
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            context.resources.getString(current.resId, *current.args.toTypedArray()),
        )
        viewModel.consumeMessage()
    }

    LaunchedEffect(finished) {
        if (finished) onDone()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.editor_title_create
                            else R.string.editor_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(
                            Icons.Rounded.Save,
                            contentDescription = stringResource(R.string.action_save),
                        )
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_qr)) },
                                leadingIcon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) },
                                onClick = { moreMenu = false; viewModel.showQr() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_export)) },
                                leadingIcon = { Icon(Icons.Rounded.Save, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    exportLauncher.launch(viewModel.exportFileName())
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.action_delete),
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
                                onClick = { moreMenu = false; deleteDialog = true },
                            )
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionHeader(title = stringResource(R.string.editor_avatar))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CharacterAvatar(
                        character = Character(
                            id = state.id,
                            name = state.name,
                            avatarPath = state.avatarPath,
                            avatarBlurHash = state.avatarBlurHash,
                        ),
                        size = 96.dp,
                    )
                    if (state.avatarLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Text(
                            text = stringResource(R.string.editor_pick_gallery),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val uri = newCameraUri(context)
                            if (uri == null) {
                                viewModel.onAvatarFailed()
                            } else {
                                cameraUri = uri
                                runCatching { cameraLauncher.launch(uri) }.onFailure {
                                    cameraUri = null
                                    viewModel.onAvatarFailed()
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                        Text(
                            text = stringResource(R.string.editor_pick_camera),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (!state.avatarPath.isNullOrBlank()) {
                        TextButton(onClick = { viewModel.onAvatarCleared() }) {
                            Text(stringResource(R.string.action_remove))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.editor_name)) },
                placeholder = { Text(stringResource(R.string.editor_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            SectionHeader(title = stringResource(R.string.editor_prompt))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        val text = ClipboardUtil.read(context)
                        if (text.isNullOrBlank()) {
                            viewModel.onClipboardEmpty()
                        } else {
                            viewModel.appendToPrompt(text)
                        }
                    },
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.editor_paste),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::onPromptChange,
                placeholder = { Text(stringResource(R.string.editor_prompt_hint)) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.editor_symbols, state.prompt.length),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
            )

            SectionHeader(title = stringResource(R.string.editor_templates))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val name = state.name.ifBlank { stringResource(R.string.editor_template_fallback_name) }
                listOf(
                    stringResource(R.string.template_rpg, name),
                    stringResource(R.string.template_assistant, name),
                    stringResource(R.string.template_detective),
                    stringResource(R.string.template_tutor),
                    stringResource(R.string.template_universe),
                ).forEach { template ->
                    AssistChip(
                        onClick = { viewModel.applyTemplate(template) },
                        label = { Text(template.take(24)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.editor_greeting))
            OutlinedTextField(
                value = state.greeting,
                onValueChange = viewModel::onGreetingChange,
                placeholder = { Text(stringResource(R.string.editor_greeting_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader(title = stringResource(R.string.editor_tags))
            TagsInput(
                tags = state.tags,
                suggestions = state.tagSuggestions,
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag,
            )

            SectionHeader(title = stringResource(R.string.editor_folder))
            OutlinedButton(
                onClick = { folderPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null)
                Text(
                    text = state.folders.firstOrNull { it.id == state.folderId }?.name
                        ?: stringResource(R.string.editor_folder_none),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            SectionHeader(
                title = stringResource(R.string.editor_temperature),
                subtitle = stringResource(R.string.editor_temperature_desc),
            )
            Text(text = String.format(Locale.getDefault(), "%.1f", state.temperature))
            Slider(
                value = state.temperature,
                onValueChange = viewModel::onTemperatureChange,
                valueRange = 0f..2f,
                steps = 19,
            )

            SectionHeader(title = stringResource(R.string.editor_top_p))
            Text(text = String.format(Locale.getDefault(), "%.2f", state.topP))
            Slider(
                value = state.topP,
                onValueChange = viewModel::onTopPChange,
                valueRange = 0f..1f,
            )

            SectionHeader(title = stringResource(R.string.editor_note))
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                placeholder = { Text(stringResource(R.string.editor_note_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader(title = stringResource(R.string.editor_visibility))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (state.isPublic) R.string.editor_visibility_public
                        else R.string.editor_visibility_private,
                    ),
                )
                Switch(checked = state.isPublic, onCheckedChange = viewModel::onPublicChange)
            }
            Text(
                text = stringResource(R.string.editor_visibility_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.draftSaved) {
                Text(
                    text = stringResource(R.string.editor_autosaved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { viewModel.save() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_save))
                }
                FilledTonalButton(
                    onClick = { exportLauncher.launch(viewModel.exportFileName()) },
                ) {
                    Text(stringResource(R.string.action_export))
                }
                if (!state.isNew) {
                    TextButton(onClick = { deleteDialog = true }) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (folderPicker) {
        FolderPickerDialog(
            folders = state.folders,
            selectedFolderId = state.folderId,
            onPick = { folderId ->
                viewModel.onFolderChange(folderId)
                folderPicker = false
            },
            onCreate = { name ->
                viewModel.createFolder(name)
                folderPicker = false
            },
            onDismiss = { folderPicker = false },
        )
    }

    if (deleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.editor_delete_title),
            message = stringResource(R.string.editor_delete_message, state.name),
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

/**
 * Намерение для uCrop. Кадр пишется в cacheDir/crop и отдаётся через FileProvider —
 * путь прописан в res/xml/file_paths.xml. Если что-то не так, возвращаем null и
 * просто сохраняем картинку без обрезки, вместо того чтобы падать.
 */
private fun cropIntent(context: Context, source: Uri): Intent? = runCatching {
    val directory = File(context.cacheDir, CROP_DIR).apply { mkdirs() }
    val file = File(directory, "avatar_${System.currentTimeMillis()}.jpg")
    val destination = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    UCrop.of(source, destination)
        .withAspectRatio(1f, 1f)
        .withMaxResultSize(CROP_MAX_PX, CROP_MAX_PX)
        .getIntent(context)
}.getOrNull()

/** Куда камера положит кадр: наш FileProvider-URI, тот же провайдер, что у uCrop. */
private fun newCameraUri(context: Context): Uri? = runCatching {
    val directory = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
    val file = File(directory, "shot_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private const val CROP_DIR = "crop"
private const val CAMERA_DIR = "camera"

/** Исходник для обрезки держим с запасом: итоговый размер задаётся в настройках. */
private const val CROP_MAX_PX = 1024
