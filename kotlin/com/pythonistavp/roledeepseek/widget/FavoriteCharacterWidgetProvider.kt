package com.pythonistavp.roledeepseek.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.pythonistavp.roledeepseek.MainActivity
import com.pythonistavp.roledeepseek.R
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.domain.usecase.FavoriteCharacterUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun favoriteCharacterUseCase(): FavoriteCharacterUseCase
}

/** Виджет «любимый персонаж»: одно нажатие — сразу запуск чата. */
class FavoriteCharacterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java,
                )
                val favorite = runCatching { entryPoint.favoriteCharacterUseCase().resolve() }
                    .getOrNull()
                val manager = AppWidgetManager.getInstance(context)
                appWidgetIds.forEach { widgetId ->
                    manager.updateAppWidget(widgetId, buildViews(context, favorite, widgetId))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FavoriteCharacterWidgetProvider::class.java),
            )
            onUpdate(context, manager, ids)
        }
    }

    private fun buildViews(context: Context, character: Character?, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_favorite_character)

        if (character == null) {
            views.setTextViewText(R.id.widgetName, context.getString(R.string.app_name))
            views.setTextViewText(R.id.widgetHint, context.getString(R.string.widget_empty))
            views.setImageViewResource(R.id.widgetAvatar, R.mipmap.ic_launcher)
        } else {
            views.setTextViewText(R.id.widgetName, character.name)
            views.setTextViewText(
                R.id.widgetHint,
                character.promptPreview(80).ifBlank { context.getString(R.string.widget_launch) },
            )
            loadAvatar(character.avatarPath)?.let { bitmap ->
                views.setImageViewBitmap(R.id.widgetAvatar, bitmap)
            } ?: views.setImageViewResource(R.id.widgetAvatar, R.mipmap.ic_launcher)
        }

        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_CHARACTER_ID, character?.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
        views.setOnClickPendingIntent(R.id.widgetActionBtn, pendingIntent)
        return views
    }

    /** Гигантский аватар в RemoteViews не влезет в Binder (лимит ~1 МБ) и уронит виджет. */
    private fun loadAvatar(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= WIDGET_AVATAR_PX) {
                sample *= 2
            }
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull()
    }

    companion object {
        const val ACTION_REFRESH = "com.pythonistavp.roledeepseek.widget.REFRESH"

        /** 56dp на экране — 128 px с запасом хватает на любую плотность. */
        private const val WIDGET_AVATAR_PX = 128

        /** Просим систему перерисовать виджет (после смены любимого персонажа). */
        fun refresh(context: Context) {
            context.sendBroadcast(
                Intent(context, FavoriteCharacterWidgetProvider::class.java)
                    .setAction(ACTION_REFRESH),
            )
        }
    }
}
