package com.pythonistavp.roledeepseek

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pythonistavp.roledeepseek.data.datastore.SettingsDataStore
import com.pythonistavp.roledeepseek.data.model.AppSettings
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Бюджет кэша картинок. Меняется в настройках — при изменении ImageLoader
 * пересобирается на лету (Coil.setImageLoader), старый кэш уходит в GC.
 */
private data class ImageBudget(
    val memoryPercent: Int,
    val diskMegabytes: Int,
    val crossfade: Boolean,
) {
    companion object {
        val Default = ImageBudget(
            memoryPercent = AppSettings.CACHE_PERCENT_DEFAULT,
            diskMegabytes = AppSettings.DISK_CACHE_DEFAULT_MB,
            crossfade = true,
        )
    }
}

@HiltAndroidApp
class RoleDeepSeekApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var settingsStore: SettingsDataStore

    /**
     * Подписываемся на настройки один раз на весь процесс приложения.
     * Именно Main (без .immediate): тело корутины должно выполниться уже после
     * super.onCreate(), когда Hilt гарантированно проставил [settingsStore].
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var appliedBudget: ImageBudget? = null

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            settingsStore.settings.collect { settings ->
                val budget = ImageBudget(
                    memoryPercent = settings.imageMemoryCachePercent,
                    diskMegabytes = settings.diskCacheMb,
                    crossfade = settings.imageCrossfade,
                )
                if (budget != appliedBudget) {
                    appliedBudget = budget
                    // setImageLoader обязан вызываться из главного потока.
                    Coil.setImageLoader(buildImageLoader(budget))
                }
            }
        }
    }

    /** Первый вариант до того, как DataStore отдал настройки. */
    override fun newImageLoader(): ImageLoader {
        val budget = appliedBudget ?: ImageBudget.Default
        appliedBudget = budget
        return buildImageLoader(budget)
    }

    private fun buildImageLoader(budget: ImageBudget): ImageLoader {
        // MemoryCache.Builder в Coil 2.x принимает Int — держим бюджет в его пределах.
        val memoryBytes = (Runtime.getRuntime().maxMemory() * (budget.memoryPercent / 100.0))
            .toLong()
            .coerceIn(MIN_MEMORY_CACHE_BYTES, MAX_MEMORY_CACHE_BYTES)
            .toInt()
        val builder = ImageLoader.Builder(this)
            .crossfade(budget.crossfade)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(memoryBytes)
                    .build()
            }
        // DiskCache.Builder.maxSizeBytes требует size > 0 и бросает
        // IllegalArgumentException на нуле, поэтому «выключено» = diskCache(null).
        if (budget.diskMegabytes > 0) {
            builder.diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, DISK_CACHE_DIR))
                    .maxSizeBytes(budget.diskMegabytes.toLong() * 1024L * 1024L)
                    .build()
            }
        } else {
            builder.diskCache(null)
        }
        return builder.build()
    }

    private companion object {
        const val DISK_CACHE_DIR = "image_cache"
        const val MIN_MEMORY_CACHE_BYTES = 8L * 1024 * 1024
        /** Больше Int.MAX_VALUE не влезет в MemoryCache.Builder.maxSizeBytes. */
        const val MAX_MEMORY_CACHE_BYTES = Int.MAX_VALUE.toLong()
    }
}
