package com.pythonistavp.roledeepseek

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pythonistavp.roledeepseek.navigation.RoleDeepSeekRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var deepLinkCharacterId by mutableStateOf<String?>(null)
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // При повороте экрана onCreate вызывается заново с тем же intent.
        // Без этой проверки приложение снова открывало бы чат/редактор и
        // «застревало» на нём, не давая вернуться назад.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            RoleDeepSeekRoot(
                deepLinkCharacterId = deepLinkCharacterId,
                onDeepLinkConsumed = { deepLinkCharacterId = null },
                sharedText = sharedText,
                onSharedTextConsumed = { sharedText = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Виджет передаёт id персонажа, другие приложения — текст для промпта. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra(EXTRA_CHARACTER_ID)?.let {
            deepLinkCharacterId = it
            // Забираем значение себе: повторный вызов не должен срабатывать снова.
            intent.removeExtra(EXTRA_CHARACTER_ID)
        }
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                sharedText = it
                intent.removeExtra(Intent.EXTRA_TEXT)
            }
        }
    }

    companion object {
        const val EXTRA_CHARACTER_ID = "extra_character_id"
    }
}
