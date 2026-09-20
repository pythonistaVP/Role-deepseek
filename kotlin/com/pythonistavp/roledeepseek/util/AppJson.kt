package com.pythonistavp.roledeepseek.util

import kotlinx.serialization.json.Json

/** Единый JSON для импорта, экспорта и резервных копий. */
object AppJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }
}
