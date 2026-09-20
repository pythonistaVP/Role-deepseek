package com.pythonistavp.roledeepseek.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Список тегов хранится в колонке как JSON-массив строк. */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        Json.encodeToString(ListSerializer(String.serializer()), value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { Json.decodeFromString(ListSerializer(String.serializer()), value) }
                .getOrDefault(emptyList())
        }
}
