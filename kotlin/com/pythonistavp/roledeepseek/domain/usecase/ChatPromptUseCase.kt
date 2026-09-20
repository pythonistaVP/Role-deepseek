package com.pythonistavp.roledeepseek.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.util.AppLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Готовит промпт и открывает DeepSeek (приложение → сайт → новый чат). */
class ChatPromptUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun fullPrompt(character: Character): String = character.buildChatPrompt()

    fun greetingOnly(character: Character): String =
        character.greeting?.trim().orEmpty().ifBlank { character.buildChatPrompt() }

    fun isDeepSeekInstalled(): Boolean =
        context.packageManager.getLaunchIntentForPackage(AppLinks.DEEPSEEK_PACKAGE) != null

    fun openDeepSeek(): Boolean {
        val appIntent = context.packageManager
            .getLaunchIntentForPackage(AppLinks.DEEPSEEK_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (appIntent != null && start(appIntent)) return true
        if (openUrl(AppLinks.DEEPSEEK_WEB)) return true
        return openUrl(AppLinks.DEEPSEEK_NEW_CHAT)
    }

    fun openUrl(url: String): Boolean = start(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
