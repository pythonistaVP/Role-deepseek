package com.pythonistavp.roledeepseek.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pythonistavp.roledeepseek.ui.AppViewModel
import com.pythonistavp.roledeepseek.ui.components.AvatarImageOptions
import com.pythonistavp.roledeepseek.ui.components.LocalAvatarImageOptions
import com.pythonistavp.roledeepseek.ui.chatlauncher.ChatLauncherScreen
import com.pythonistavp.roledeepseek.ui.detail.DetailScreen
import com.pythonistavp.roledeepseek.ui.editor.EditorScreen
import com.pythonistavp.roledeepseek.ui.folders.FoldersScreen
import com.pythonistavp.roledeepseek.ui.library.LibraryScreen
import com.pythonistavp.roledeepseek.ui.settings.SettingsScreen
import com.pythonistavp.roledeepseek.ui.theme.RoleDeepSeekTheme

private const val TRANSITION_MS = 280

/**
 * Корень приложения: тема из настроек + граф навигации.
 * Виджет и «Поделиться текстом» приходят сюда как deep link.
 */
@Composable
fun RoleDeepSeekRoot(
    deepLinkCharacterId: String?,
    onDeepLinkConsumed: () -> Unit,
    sharedText: String?,
    onSharedTextConsumed: () -> Unit,
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val settings by appViewModel.settings.collectAsStateWithLifecycle()

    // Настройки отрисовки картинок раздаём через CompositionLocal: их читают
    // аватары внутри карточек, списков, редактора и деталей.
    val avatarOptions = remember(settings.thumbnailAvatars, settings.imageCrossfade, settings.blurHashEnabled) {
        AvatarImageOptions(
            thumbnails = settings.thumbnailAvatars,
            crossfade = settings.imageCrossfade,
            blurHash = settings.blurHashEnabled,
        )
    }

    CompositionLocalProvider(LocalAvatarImageOptions provides avatarOptions) {
        RoleDeepSeekTheme(
            themeMode = settings.theme,
            dynamicColor = settings.dynamicColor,
            animateColors = settings.animateTransitions,
        ) {
            val navController = rememberNavController()

            LaunchedEffect(deepLinkCharacterId) {
                val id = deepLinkCharacterId ?: return@LaunchedEffect
                navController.navigate(Routes.chat(id))
                onDeepLinkConsumed()
            }

            LaunchedEffect(sharedText) {
                val text = sharedText ?: return@LaunchedEffect
                navController.navigate(Routes.editorWithPrefill(text))
                onSharedTextConsumed()
            }

            RoleDeepSeekNavGraph(
                navController = navController,
                animate = settings.animateTransitions,
            )
        }
    }
}

@Composable
private fun RoleDeepSeekNavGraph(
    navController: NavHostController,
    animate: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        enterTransition = {
            if (animate) {
                slideInHorizontally(animationSpec = tween(TRANSITION_MS)) { it / 8 } +
                    fadeIn(animationSpec = tween(TRANSITION_MS))
            } else {
                EnterTransition.None
            }
        },
        exitTransition = {
            if (animate) fadeOut(animationSpec = tween(TRANSITION_MS)) else ExitTransition.None
        },
        popEnterTransition = {
            if (animate) fadeIn(animationSpec = tween(TRANSITION_MS)) else EnterTransition.None
        },
        popExitTransition = {
            if (animate) {
                slideOutHorizontally(animationSpec = tween(TRANSITION_MS)) { it / 8 } +
                    fadeOut(animationSpec = tween(TRANSITION_MS))
            } else {
                ExitTransition.None
            }
        },
    ) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenCharacter = { id -> navController.navigate(Routes.detail(id)) },
                onStartChat = { id -> navController.navigate(Routes.chat(id)) },
                onEditCharacter = { id -> navController.navigate(Routes.editor(id)) },
                onOpenFolders = { navController.navigate(Routes.FOLDERS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument(Routes.ARG_CHARACTER_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Routes.ARG_PREFILL) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            EditorScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_CHARACTER_ID) { type = NavType.StringType },
            ),
        ) {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onStartChat = { id -> navController.navigate(Routes.chat(id)) },
                onEdit = { id -> navController.navigate(Routes.editor(id)) },
                onOpenCharacter = { id ->
                    navController.navigate(Routes.detail(id)) {
                        popUpTo(Routes.DETAIL) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument(Routes.ARG_CHARACTER_ID) { type = NavType.StringType },
            ),
        ) {
            ChatLauncherScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.FOLDERS) {
            FoldersScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
