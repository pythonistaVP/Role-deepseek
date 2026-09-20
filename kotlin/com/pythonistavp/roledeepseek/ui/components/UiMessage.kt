package com.pythonistavp.roledeepseek.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/** Сообщение для Snackbar, привязанное к строковому ресурсу (полностью локализовано). */
@Immutable
data class UiMessage(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
) {
    companion object {
        fun of(@StringRes resId: Int, vararg args: Any): UiMessage = UiMessage(resId, args.toList())
    }
}
