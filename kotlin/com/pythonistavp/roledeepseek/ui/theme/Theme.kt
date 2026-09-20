package com.pythonistavp.roledeepseek.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.pythonistavp.roledeepseek.data.model.AppTheme

/** Скругления 16dp — базовый радиус приложения. */
val RoleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RoleDeepSeekTheme(
    themeMode: AppTheme = AppTheme.DARK,
    dynamicColor: Boolean = true,
    animateColors: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val target = when {
        themeMode == AppTheme.DYNAMIC && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val scheme = if (animateColors) animatedScheme(target) else target

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = scheme, typography = RoleTypography, shapes = RoleShapes, content = content)
}

/**
 * Плавный crossfade при переключении темы: цвета интерполируются,
 * а не меняются рывком.
 */
@Composable
private fun animatedScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 450)

    val animated: @Composable (Color, String) -> Color = { color, label ->
        animateColorAsState(targetValue = color, animationSpec = spec, label = label).value
    }

    return target.copy(
        primary = animated(target.primary, "primary"),
        onPrimary = animated(target.onPrimary, "onPrimary"),
        primaryContainer = animated(target.primaryContainer, "primaryContainer"),
        onPrimaryContainer = animated(target.onPrimaryContainer, "onPrimaryContainer"),
        secondary = animated(target.secondary, "secondary"),
        onSecondary = animated(target.onSecondary, "onSecondary"),
        secondaryContainer = animated(target.secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = animated(target.onSecondaryContainer, "onSecondaryContainer"),
        tertiary = animated(target.tertiary, "tertiary"),
        tertiaryContainer = animated(target.tertiaryContainer, "tertiaryContainer"),
        background = animated(target.background, "background"),
        onBackground = animated(target.onBackground, "onBackground"),
        surface = animated(target.surface, "surface"),
        onSurface = animated(target.onSurface, "onSurface"),
        surfaceVariant = animated(target.surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = animated(target.onSurfaceVariant, "onSurfaceVariant"),
        outline = animated(target.outline, "outline"),
        outlineVariant = animated(target.outlineVariant, "outlineVariant"),
        error = animated(target.error, "error"),
        onError = animated(target.onError, "onError"),
    )
}
