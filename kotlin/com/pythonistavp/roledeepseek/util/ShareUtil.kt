package com.pythonistavp.roledeepseek.util

import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import java.io.File

object ShareUtil {

    fun shareText(context: Context, title: String, text: String) {
        val intent = ShareCompat.IntentBuilder(context)
            .setType("text/plain")
            .setSubject(title)
            .setText(text)
            .setChooserTitle(title)
            .createChooserIntent()
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Кладём .json в cacheDir/share и отдаём через FileProvider. */
    fun shareJsonFile(context: Context, fileName: String, json: String, chooserTitle: String) {
        val directory = File(context.cacheDir, "share").apply { if (!exists()) mkdirs() }
        val file = File(directory, fileName)
        runCatching {
            file.writeText(json)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = ShareCompat.IntentBuilder(context)
                .setType("application/json")
                .setStream(uri)
                .setChooserTitle(chooserTitle)
                .createChooserIntent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }
    }
}
