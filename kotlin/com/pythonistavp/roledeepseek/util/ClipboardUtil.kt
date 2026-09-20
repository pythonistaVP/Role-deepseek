package com.pythonistavp.roledeepseek.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardUtil {

    fun copy(context: Context, label: String, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun read(context: Context): String? {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = manager?.primaryClip
        if (clip == null || clip.itemCount == 0) return null
        return runCatching { clip.getItemAt(0).coerceToText(context)?.toString() }.getOrNull()
    }
}
