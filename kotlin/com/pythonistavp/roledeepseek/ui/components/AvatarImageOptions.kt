package com.pythonistavp.roledeepseek.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Как рисовать аватары. Значения приходят из раздела «Производительность»
 * и раздаются вниз по дереву композиции, чтобы не протаскивать их параметрами
 * через каждый экран, список и карточку.
 */
data class AvatarImageOptions(
    /** Просить Coil уменьшить картинку до размера карточки (главная экономия памяти). */
    val thumbnails: Boolean = true,
    /** Плавное появление картинки. */
    val crossfade: Boolean = true,
    /** Рисовать blurhash-подложку, пока грузится аватар. */
    val blurHash: Boolean = true,
) {
    companion object {
        val Default = AvatarImageOptions()
    }
}

val LocalAvatarImageOptions = compositionLocalOf { AvatarImageOptions.Default }
