package com.pythonistavp.roledeepseek.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.pythonistavp.roledeepseek.data.datastore.SettingsDataStore
import com.pythonistavp.roledeepseek.data.model.AppSettings
import com.pythonistavp.roledeepseek.util.BlurHash
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Путь к аватару в приватном хранилище + его blurhash. */
data class AvatarInfo(val path: String, val blurHash: String?)

/** Настройки сохранения аватара (берутся из раздела «Производительность»). */
data class AvatarSaveOptions(
    val maxPx: Int = AvatarStore.DEFAULT_MAX_PX,
    val quality: Int = AvatarStore.DEFAULT_QUALITY,
    val blurHash: Boolean = true,
) {
    companion object {
        val Default = AvatarSaveOptions()
    }
}

/**
 * Аватары живут в filesDir/avatars/<id>.jpg — приватно, без внешних разрешений
 * и без сетевых загрузок.
 *
 * Всё декодирование идёт через `BitmapFactory.Options` с inSampleSize: фото с
 * камеры на 50 Мп весит в памяти ~200 МБ и раньше роняло приложение с OOM.
 * Теперь картинка уменьшается ещё на этапе чтения.
 */
@Singleton
class AvatarStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsDataStore,
) {

    private val directory: File
        get() = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    private fun fileFor(id: String) = File(directory, "$id.jpg")

    /** Настройки из DataStore — читаются один раз на операцию. */
    private suspend fun options(): AvatarSaveOptions {
        val settings = settingsStore.current()
        return AvatarSaveOptions(
            maxPx = settings.avatarMaxPx.coerceIn(MIN_AVATAR_PX, MAX_AVATAR_PX),
            quality = settings.avatarQuality.coerceIn(MIN_QUALITY, MAX_QUALITY),
            blurHash = settings.blurHashEnabled,
        )
    }

    suspend fun importFromUri(uri: Uri, id: String): AvatarInfo? = withContext(Dispatchers.IO) {
        val saveOptions = options()
        runCatching {
            val bounds = readBounds(uri) ?: return@runCatching null
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.first, bounds.second, saveOptions.maxPx)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@runCatching null
            saveBitmap(bitmap, id, saveOptions)
        }.getOrNull()
    }

    /** Импорт аватара из base64 (карточки SillyTavern / Character.AI). */
    suspend fun importFromBase64(payload: String, id: String): AvatarInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val saveOptions = options()
                val clean = payload.substringAfter("base64,", payload).trim()
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                val bitmap = decodeBytes(bytes, saveOptions.maxPx) ?: return@runCatching null
                saveBitmap(bitmap, id, saveOptions)
            }.getOrNull()
        }

    /** Импорт аватара по ссылке из карточки (SillyTavern хранит там URL). */
    suspend fun importFromUrl(url: String, id: String): AvatarInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val saveOptions = options()
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = true
                }
                try {
                    val bytes = connection.inputStream.use { stream ->
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            val read = stream.read(chunk)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_DOWNLOAD_BYTES) break
                            buffer.write(chunk, 0, read)
                        }
                        buffer.toByteArray()
                    }
                    if (bytes.isEmpty()) return@runCatching null
                    val bitmap = decodeBytes(bytes, saveOptions.maxPx) ?: return@runCatching null
                    saveBitmap(bitmap, id, saveOptions)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    /**
     * Копирует аватар другого персонажа (дублирование карточек).
     * Файл копируется побайтово, без перекодирования — быстро и без потери качества.
     */
    suspend fun copyFrom(sourcePath: String?, id: String, blurHash: String?): AvatarInfo? =
        withContext(Dispatchers.IO) {
            if (sourcePath.isNullOrBlank()) return@withContext null
            runCatching {
                val source = File(sourcePath)
                if (!source.exists() || source.length() == 0L) return@runCatching null
                val target = fileFor(id)
                source.copyTo(target, overwrite = true)
                AvatarInfo(target.absolutePath, blurHash)
            }.getOrNull()
        }

    fun load(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() && it.parentFile == directory }?.delete() }
    }

    fun deleteAll() {
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    fun count(): Int = directory.listFiles()?.count { it.isFile } ?: 0

    fun sizeBytes(): Long = directory.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    /** Base64 для экспорта карточки (уже сжатый JPEG, поэтому не раздувается). */
    suspend fun toBase64(path: String?): String? = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        runCatching {
            "data:image/jpeg;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun readBounds(uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outWidth to options.outHeight
    }

    private fun decodeBytes(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** Степень двойки: во сколько раз уменьшить картинку, чтобы она влезла в [targetPx]. */
    private fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        var sample = 1
        val largest = maxOf(width, height)
        while (largest / (sample * 2) >= targetPx) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun saveBitmap(bitmap: Bitmap, id: String, options: AvatarSaveOptions): AvatarInfo? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
            return null
        }
        val size = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - size) / 2,
            (bitmap.height - size) / 2,
            size,
            size,
        )
        val limit = options.maxPx.coerceIn(MIN_AVATAR_PX, MAX_AVATAR_PX)
        val scaled = if (size > limit) {
            Bitmap.createScaledBitmap(cropped, limit, limit, true)
        } else {
            cropped
        }
        val blurHash = if (options.blurHash) {
            runCatching { BlurHash.encode(scaled) }.getOrNull()
        } else {
            null
        }
        val target = fileFor(id)
        runCatching {
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, options.quality, out)
            }
        }.onFailure {
            runCatching { target.delete() }
            recycleQuietly(bitmap, cropped, scaled)
            return null
        }
        recycleQuietly(bitmap, cropped, scaled)
        return AvatarInfo(target.absolutePath, blurHash)
    }

    /** Освобождает каждую отдельную картинку ровно один раз (createBitmap может вернуть тот же объект). */
    private fun recycleQuietly(vararg bitmaps: Bitmap) {
        val seen = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Bitmap, Boolean>(),
        )
        bitmaps.forEach { bitmap ->
            if (seen.add(bitmap) && !bitmap.isRecycled) runCatching { bitmap.recycle() }
        }
    }

    companion object {
        const val DIR = "avatars"

        // Единственный источник правды по пределам — AppSettings (раздел «Производительность»).
        const val DEFAULT_MAX_PX = AppSettings.AVATAR_PX_DEFAULT
        const val MIN_AVATAR_PX = AppSettings.AVATAR_PX_MIN
        const val MAX_AVATAR_PX = AppSettings.AVATAR_PX_MAX

        const val DEFAULT_QUALITY = AppSettings.AVATAR_QUALITY_DEFAULT
        const val MIN_QUALITY = AppSettings.AVATAR_QUALITY_MIN
        const val MAX_QUALITY = AppSettings.AVATAR_QUALITY_MAX

        /** Сколько максимум байт скачиваем для аватара по ссылке. */
        private const val MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024
        private const val TIMEOUT_MS = 8000
    }
}
