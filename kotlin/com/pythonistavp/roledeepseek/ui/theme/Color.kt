package com.pythonistavp.roledeepseek.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Фирменная палитра: глубокий индиго + циан (как на логотипе).
val Indigo05 = Color(0xFF0A1024)
val Indigo10 = Color(0xFF0E1636)
val Indigo20 = Color(0xFF162150)
val Indigo40 = Color(0xFF2A3AA8)
val Indigo80 = Color(0xFFB7C4FF)
val Cyan80 = Color(0xFF7FD8F0)
val Cyan40 = Color(0xFF0E7490)
val Pink80 = Color(0xFFFFB1D0)
val Pink40 = Color(0xFFB03A6A)

val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = Color(0xFF111C4E),
    primaryContainer = Indigo40,
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Cyan80,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF00505E),
    onSecondaryContainer = Color(0xFFB6EBFF),
    tertiary = Pink80,
    onTertiary = Color(0xFF5E1136),
    tertiaryContainer = Color(0xFF7C2950),
    onTertiaryContainer = Color(0xFFFFD9E4),
    background = Indigo05,
    onBackground = Color(0xFFF1F0F7),
    surface = Color(0xFF0F1530),
    onSurface = Color(0xFFF1F0F7),
    surfaceVariant = Color(0xFF232A47),
    onSurfaceVariant = Color(0xFFC7CBE0),
    surfaceContainer = Color(0xFF141A38),
    surfaceContainerHigh = Color(0xFF1A2145),
    surfaceContainerHighest = Color(0xFF212A52),
    outline = Color(0xFF8E93AC),
    outlineVariant = Color(0xFF3A4166),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001158),
    secondary = Cyan40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB6EBFF),
    onSecondaryContainer = Color(0xFF001F27),
    tertiary = Pink40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E4),
    onTertiaryContainer = Color(0xFF3E0021),
    background = Color(0xFFF7F7FF),
    onBackground = Color(0xFF12142A),
    surface = Color(0xFFFCFAFF),
    onSurface = Color(0xFF12142A),
    surfaceVariant = Color(0xFFE1E1F0),
    onSurfaceVariant = Color(0xFF44475F),
    surfaceContainer = Color(0xFFF0EFFA),
    surfaceContainerHigh = Color(0xFFEAE9F5),
    surfaceContainerHighest = Color(0xFFE4E3EF),
    outline = Color(0xFF75778F),
    outlineVariant = Color(0xFFC5C6D8),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** Цвета папок, которые предлагаем при создании. */
val FolderColors: List<Long> = listOf(
    0xFF4F5BD5, 0xFF0E7490, 0xFF16A34A, 0xFFD97706,
    0xFFDC2626, 0xFF9333EA, 0xFFDB2777, 0xFF475569,
)
