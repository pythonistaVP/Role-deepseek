package com.pythonistavp.roledeepseek.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.AppLanguage
import com.pythonistavp.roledeepseek.data.model.AppSettings
import com.pythonistavp.roledeepseek.data.model.AppTheme
import com.pythonistavp.roledeepseek.data.model.ImportMode
import com.pythonistavp.roledeepseek.data.model.PerfPreset
import com.pythonistavp.roledeepseek.ui.components.BetaBadge
import com.pythonistavp.roledeepseek.ui.components.ChoiceDialog
import com.pythonistavp.roledeepseek.ui.components.ConfirmDialog
import com.pythonistavp.roledeepseek.ui.components.SectionHeader
import com.pythonistavp.roledeepseek.ui.components.StatRow
import com.pythonistavp.roledeepseek.util.AppLinks
import com.pythonistavp.roledeepseek.util.Formatters
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val settings = state.settings

    var importModeVisible by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var resetDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> if (uri != null) viewModel.exportBackup(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImport = uri
            importModeVisible = true
        }
    }

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_title))
                        BetaBadge(modifier = Modifier.padding(start = 8.dp))
                    }
                },
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
                .padding(horizontal = 16.dp),
        ) {
            // ---------- Внешний вид ----------
            SectionHeader(title = stringResource(R.string.settings_appearance))

            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { viewModel.setTheme(theme) },
                        label = { Text(stringResource(theme.labelRes())) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_theme_dynamic_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            IntSliderRow(
                title = stringResource(R.string.settings_grid_columns),
                value = settings.gridColumns,
                range = AppSettings.GRID_MIN..AppSettings.GRID_MAX,
                valueText = stringResource(R.string.settings_grid_columns_value, settings.gridColumns),
                onValueChange = viewModel::setGridColumns,
            )

            SwitchRow(
                title = stringResource(R.string.settings_motion),
                checked = settings.animateTransitions,
                onCheckedChange = viewModel::setAnimateTransitions,
            )
            SwitchRow(
                title = stringResource(R.string.settings_overlay_hint),
                checked = settings.overlayHintEnabled,
                onCheckedChange = viewModel::setOverlayHint,
            )

            // ---------- Производительность ----------
            SectionHeader(
                title = stringResource(R.string.settings_perf),
                subtitle = stringResource(R.string.settings_perf_desc),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.settings_perf_preset),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PerfPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = { viewModel.applyPreset(preset) },
                        label = { Text(stringResource(preset.labelRes())) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_perf_preset_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            IntSliderRow(
                title = stringResource(R.string.settings_perf_avatar_px),
                value = settings.avatarMaxPx,
                range = AppSettings.AVATAR_PX_MIN..AppSettings.AVATAR_PX_MAX,
                step = 256,
                valueText = stringResource(R.string.settings_perf_avatar_px_value, settings.avatarMaxPx),
                onValueChange = viewModel::setAvatarMaxPx,
            )
            IntSliderRow(
                title = stringResource(R.string.settings_perf_avatar_quality),
                value = settings.avatarQuality,
                range = AppSettings.AVATAR_QUALITY_MIN..AppSettings.AVATAR_QUALITY_MAX,
                step = 5,
                valueText = stringResource(R.string.settings_perf_avatar_quality_value, settings.avatarQuality),
                onValueChange = viewModel::setAvatarQuality,
            )

            SwitchRow(
                title = stringResource(R.string.settings_perf_thumbnails),
                subtitle = stringResource(R.string.settings_perf_thumbnails_desc),
                checked = settings.thumbnailAvatars,
                onCheckedChange = viewModel::setThumbnailAvatars,
            )
            SwitchRow(
                title = stringResource(R.string.settings_perf_blur_hash),
                subtitle = stringResource(R.string.settings_perf_blur_hash_desc),
                checked = settings.blurHashEnabled,
                onCheckedChange = viewModel::setBlurHash,
            )
            SwitchRow(
                title = stringResource(R.string.settings_perf_crossfade),
                checked = settings.imageCrossfade,
                onCheckedChange = viewModel::setImageCrossfade,
            )

            IntSliderRow(
                title = stringResource(R.string.settings_perf_memory_cache),
                value = settings.imageMemoryCachePercent,
                range = AppSettings.CACHE_PERCENT_MIN..AppSettings.CACHE_PERCENT_MAX,
                step = 5,
                valueText = stringResource(
                    R.string.settings_perf_memory_cache_value,
                    settings.imageMemoryCachePercent,
                ),
                onValueChange = viewModel::setImageMemoryCachePercent,
            )
            IntSliderRow(
                title = stringResource(R.string.settings_perf_disk_cache),
                value = settings.diskCacheMb,
                range = AppSettings.DISK_CACHE_MIN_MB..AppSettings.DISK_CACHE_MAX_MB,
                step = 32,
                valueText = stringResource(R.string.settings_perf_disk_cache_value, settings.diskCacheMb),
                onValueChange = viewModel::setDiskCacheMb,
            )
            Text(
                text = stringResource(R.string.settings_perf_caches_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SwitchRow(
                title = stringResource(R.string.settings_perf_analytics),
                subtitle = stringResource(R.string.settings_perf_analytics_desc),
                checked = settings.analyticsEnabled,
                onCheckedChange = viewModel::setAnalyticsEnabled,
            )

            // ---------- Язык ----------
            SectionHeader(title = stringResource(R.string.settings_language))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = settings.language == language,
                        onClick = { viewModel.setLanguage(language) },
                        label = { Text(stringResource(language.labelRes())) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            // ---------- Данные ----------
            SectionHeader(title = stringResource(R.string.settings_data))

            SettingCard(
                title = stringResource(R.string.settings_export_backup),
                description = stringResource(R.string.settings_export_backup_desc),
                icon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                onClick = { exportLauncher.launch(viewModel.backupFileName()) },
            )
            SettingCard(
                title = stringResource(R.string.settings_import_backup),
                description = stringResource(R.string.settings_import_backup_desc),
                icon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream"),
                    )
                },
            )
            SettingCard(
                title = stringResource(R.string.settings_clear_cache),
                description = stringResource(R.string.settings_clear_cache_desc),
                icon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
                onClick = { viewModel.clearImageCache() },
            )
            SettingCard(
                title = stringResource(R.string.settings_clear_analytics),
                description = stringResource(R.string.settings_analytics_desc),
                icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = { viewModel.clearAnalytics() },
            )
            SettingCard(
                title = stringResource(R.string.settings_reset),
                description = stringResource(R.string.settings_reset_desc),
                icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                onClick = { resetDialog = true },
            )

            // ---------- Статистика ----------
            SectionHeader(title = stringResource(R.string.settings_stats))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatRow(
                        label = stringResource(R.string.settings_stat_characters),
                        value = state.statistics.characters.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.settings_stat_folders),
                        value = state.statistics.folders.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.settings_stat_pinned),
                        value = state.statistics.pinned.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.settings_stat_chats),
                        value = state.statistics.chatLaunches.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.settings_stat_avatars),
                        value = state.statistics.avatars.toString(),
                    )
                    StatRow(
                        label = stringResource(R.string.settings_stat_avatar_size),
                        value = Formatters.fileSize(state.statistics.avatarBytes),
                    )
                    StatRow(
                        label = stringResource(R.string.settings_stat_db_size),
                        value = Formatters.fileSize(state.statistics.databaseBytes),
                    )
                    StatRow(
                        label = stringResource(R.string.settings_stat_actions),
                        value = state.statistics.totalActions.toString(),
                    )
                    if (!settings.analyticsEnabled) {
                        Text(
                            text = stringResource(R.string.settings_perf_analytics_off_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            // ---------- О приложении ----------
            SectionHeader(title = stringResource(R.string.settings_about))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        BetaBadge(modifier = Modifier.padding(start = 8.dp))
                    }
                    Text(
                        text = stringResource(
                            R.string.settings_about_version,
                            state.version,
                            state.build,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_beta_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_about_beta_text),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_about_author),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    LinkRow(
                        title = stringResource(R.string.settings_about_author_link),
                        onClick = { AppLinks.open(context, AppLinks.GITHUB_AUTHOR) },
                    )
                    LinkRow(
                        title = stringResource(R.string.settings_about_repo),
                        onClick = { AppLinks.open(context, AppLinks.GITHUB_REPO) },
                    )
                    LinkRow(
                        title = stringResource(R.string.settings_about_license),
                        subtitle = stringResource(R.string.settings_about_license_desc),
                        onClick = { AppLinks.open(context, AppLinks.MIT_LICENSE) },
                    )
                    TextButton(
                        onClick = { AppLinks.open(context, AppLinks.GITHUB_REPO) },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = null)
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(stringResource(R.string.settings_star))
                            Text(
                                text = stringResource(R.string.settings_star_desc),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.settings_changelog))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ChangelogEntry(
                        version = "1.1.0",
                        items = listOf(
                            stringResource(R.string.changelog_110_fix_photo),
                            stringResource(R.string.changelog_110_perf),
                            stringResource(R.string.changelog_110_avatars),
                            stringResource(R.string.changelog_110_speed),
                            stringResource(R.string.changelog_110_draft),
                            stringResource(R.string.changelog_110_stability),
                        ),
                    )
                    ChangelogEntry(
                        version = "1.0.0",
                        items = listOf(stringResource(R.string.changelog_100_first)),
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (resetDialog) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset_title),
            message = stringResource(R.string.settings_reset_message),
            confirmLabel = stringResource(R.string.action_reset),
            onConfirm = {
                viewModel.resetSettings()
                resetDialog = false
            },
            onDismiss = { resetDialog = false },
        )
    }

    if (importModeVisible) {
        ChoiceDialog(
            title = stringResource(R.string.settings_import_mode_title),
            options = listOf(
                Icons.Rounded.FileUpload to stringResource(R.string.settings_import_replace),
                Icons.Rounded.FileDownload to stringResource(R.string.settings_import_merge),
            ),
            onPick = { index ->
                val uri = pendingImport
                importModeVisible = false
                pendingImport = null
                if (uri != null) {
                    viewModel.importBackup(
                        uri,
                        if (index == 0) ImportMode.REPLACE else ImportMode.MERGE,
                    )
                }
            },
            onDismiss = {
                importModeVisible = false
                pendingImport = null
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    visible: Boolean = true,
) {
    if (!visible) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Ползунок по целым значениям с шагом: рисуем заголовок, текущее значение
 * и сам Slider. Значение «прищёлкивается» к ближайшему шагу.
 */
@Composable
private fun IntSliderRow(
    title: String,
    value: Int,
    range: IntRange,
    valueText: String,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val intervals = (range.last - range.first) / step
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = (range.first +
                    ((raw - range.first) / step).roundToInt() * step)
                    .coerceIn(range.first, range.last)
                if (snapped != value) onValueChange(snapped)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (intervals - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun SettingCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Одна версия в списке «Что нового»: заголовок и пункты. */
@Composable
private fun ChangelogEntry(version: String, items: List<String>) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.settings_changelog_version, version),
            style = MaterialTheme.typography.titleSmall,
        )
        items.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

private fun AppTheme.labelRes(): Int = when (this) {
    AppTheme.SYSTEM -> R.string.settings_theme_system
    AppTheme.LIGHT -> R.string.settings_theme_light
    AppTheme.DARK -> R.string.settings_theme_dark
    AppTheme.DYNAMIC -> R.string.settings_theme_dynamic
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.RU -> R.string.settings_language_ru
    AppLanguage.EN -> R.string.settings_language_en
}
