package com.pythonistavp.roledeepseek.navigation

import android.util.Base64

/** Маршруты навигации. Аргументы кодируем в base64url — без «/» и «?» внутри. */
object Routes {
    const val ARG_CHARACTER_ID = "characterId"
    const val ARG_PREFILL = "prefill"

    const val LIBRARY = "library"
    const val EDITOR = "editor?characterId={characterId}&prefill={prefill}"
    const val DETAIL = "detail/{characterId}"
    const val CHAT = "chat/{characterId}"
    const val FOLDERS = "folders"
    const val SETTINGS = "settings"

    fun editor(characterId: String? = null): String =
        if (characterId == null) "editor" else "editor?characterId=$characterId"

    fun editorWithPrefill(text: String): String = "editor?prefill=${encode(text)}"

    fun detail(characterId: String): String = "detail/$characterId"

    fun chat(characterId: String): String = "chat/$characterId"

    fun encode(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING,
    )

    fun decode(value: String): String? = runCatching {
        String(
            Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING),
            Charsets.UTF_8,
        )
    }.getOrNull()
}
