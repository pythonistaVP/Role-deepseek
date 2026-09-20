package com.pythonistavp.roledeepseek.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Чтение и запись файлов через Storage Access Framework. */
object DocumentIO {

    fun readText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()

    fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        }
        true
    }.getOrDefault(false)

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
}
