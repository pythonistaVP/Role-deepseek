package com.pythonistavp.roledeepseek.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.util.BlurHash
import java.io.File

/**
 * Круглый аватар. Пока файл грузится — размытая подложка из blurhash
 * (или инициал на градиенте), поэтому карточка никогда не «прыгает».
 *
 * Размер запроса к Coil подбирается под размер карточки: держать в памяти
 * полноразмерный 768px-битмап ради кружка 56dp — главный пожиратель памяти
 * в длинных списках. Отключается в настройках производительности.
 */
@Composable
fun CharacterAvatar(
    character: Character,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val options = LocalAvatarImageOptions.current
    val context = LocalContext.current
    val density = LocalDensity.current

    val targetPx = remember(size, density) {
        with(density) { size.roundToPx() }.coerceAtLeast(MIN_TARGET_PX)
    }

    val blurBitmap = if (options.blurHash) {
        remember(character.avatarBlurHash) {
            BlurHash.decodeCached(character.avatarBlurHash, 32, 32)?.asImageBitmap()
        }
    } else {
        null
    }

    val request = remember(character.avatarPath, options, targetPx) {
        val path = character.avatarPath
        if (path.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(File(path))
                .apply {
                    if (options.thumbnails) size(targetPx) else size(Size.ORIGINAL)
                    crossfade(options.crossfade)
                }
                .build()
        }
    }

    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(scheme.surfaceVariant)
            .border(1.dp, scheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (blurBitmap != null) {
            Image(
                bitmap = blurBitmap,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(listOf(scheme.primaryContainer, scheme.secondaryContainer)),
                    ),
            )
            Text(
                text = initials(character.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
            )
        }

        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = character.name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/** Ниже этого размера уменьшать смысла нет — только артефакты. */
private const val MIN_TARGET_PX = 64
