package com.pythonistavp.roledeepseek.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pythonistavp.roledeepseek.R

/**
 * Плавающая подсказка «нажмите и удерживайте → Вставить» поверх DeepSeek.
 * Работает только с разрешением «Поверх других окон» и живёт несколько секунд.
 * Сервис foreground-типа, чтобы система не убила его при переходе в другое приложение.
 */
class FloatingHintService : Service() {

    private var overlay: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Никогда не роняем приложение из-за подсказки: если система запретила
        // foreground-сервис (редкий ROM, отозванное разрешение) — просто выходим.
        val started = runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        hideOverlay()
        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#E61B2350"))
                setStroke(dp(1), Color.parseColor("#7FD8F0"))
            }
        }

        val label = TextView(this).apply {
            text = getString(R.string.overlay_hint_text)
            setTextColor(Color.WHITE)
            textSize = 14f
            setLineSpacing(0f, 1.1f)
        }
        container.addView(label)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(96)
        }

        container.setOnClickListener { stopSelf() }

        runCatching { manager.addView(container, params) }
        overlay = container
        handler.postDelayed({ stopSelf() }, HIDE_DELAY_MS)
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { manager?.removeView(view) }
        overlay = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.chat_overlay_title))
            .setContentText(getString(R.string.overlay_hint_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "role_deepseek_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val HIDE_DELAY_MS = 9000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingHintService::class.java),
            )
        }
    }
}
