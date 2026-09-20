package com.pythonistavp.roledeepseek.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    fun dateTime(timestamp: Long): String =
        if (timestamp <= 0L) "—" else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(timestamp))

    fun date(timestamp: Long): String =
        if (timestamp <= 0L) "—" else SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            .format(Date(timestamp))

    fun fileStamp(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(timestamp))

    /** «1,4 МБ» — разделитель дробной части берём из локали. */
    fun fileSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes ${unit("B", "Б")}"
        val kilobytes = bytes / 1024.0
        if (kilobytes < 1024.0) {
            return String.format(Locale.getDefault(), "%.1f %s", kilobytes, unit("KB", "КБ"))
        }
        return String.format(Locale.getDefault(), "%.1f %s", kilobytes / 1024.0, unit("MB", "МБ"))
    }

    /** Имя файла без символов, запрещённых файловыми системами. */
    fun safeFileName(name: String): String {
        val cleaned = name.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
        return cleaned.ifBlank { "character" }.take(60)
    }

    private fun unit(english: String, russian: String): String =
        if (Locale.getDefault().language == "ru") russian else english
}
