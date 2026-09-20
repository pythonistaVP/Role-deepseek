package com.pythonistavp.roledeepseek.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Ссылки проекта и точка входа в DeepSeek. */
object AppLinks {
    const val GITHUB_AUTHOR = "https://github.com/pythonistaVP"
    const val GITHUB_REPO = "https://github.com/pythonistaVP/Role-deepseek"
    const val MIT_LICENSE = "https://github.com/pythonistaVP/Role-deepseek/blob/main/LICENSE"

    const val DEEPSEEK_PACKAGE = "com.deepseek.chat"
    const val DEEPSEEK_WEB = "https://chat.deepseek.com"
    const val DEEPSEEK_NEW_CHAT = "https://chat.deepseek.com/a/chat/s/new"

    fun open(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
