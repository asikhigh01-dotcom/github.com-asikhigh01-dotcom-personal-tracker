package com.example.data.database

import androidx.room.TypeConverter

/**
 * Room TypeConverter for converting between List<Int> and a comma-delimited String.
 */
class Converters {
    @TypeConverter
    fun fromIntList(list: List<Int>?): String {
        return list?.joinToString(separator = ",") ?: ""
    }

    @TypeConverter
    fun toIntList(data: String?): List<Int> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    }
}
