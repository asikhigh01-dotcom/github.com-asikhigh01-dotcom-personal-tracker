package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Utility functions for local device date handling.
 * Ensures consistent "yyyy-MM-dd" format for Room storage,
 * using local device time and preventing UTC day-shifting.
 */
object DateUtils {
    private const val DATE_FORMAT = "yyyy-MM-dd"

    fun getTodayLocalDateString(): String {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return sdf.format(Date())
    }

    fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return sdf.format(date)
    }

    fun parseDate(dateStr: String): Date? {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return try {
            sdf.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }

    fun getPreviousDate(dateStr: String): String {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val date = parseDate(dateStr) ?: Date()
        val calendar = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return sdf.format(calendar.time)
    }

    fun getNextDate(dateStr: String): String {
        val sdf = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val date = parseDate(dateStr) ?: Date()
        val calendar = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return sdf.format(calendar.time)
    }

    fun isToday(dateStr: String): Boolean {
        return dateStr == getTodayLocalDateString()
    }

    fun isFuture(dateStr: String): Boolean {
        return dateStr > getTodayLocalDateString()
    }

    fun getDayStartMillis(dateStr: String): Long {
        val date = parseDate(dateStr) ?: return 0L
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun getDayEndMillis(dateStr: String): Long {
        val start = getDayStartMillis(dateStr)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    fun formatDisplayDate(dateStr: String): String {
        val date = parseDate(dateStr) ?: return dateStr
        val displayFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        val formatted = displayFormat.format(date)
        return if (isToday(dateStr)) {
            "Today • $formatted"
        } else {
            formatted
        }
    }

    fun formatHeaderDate(dateStr: String): String {
        val date = parseDate(dateStr) ?: return dateStr
        val headerFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
        return headerFormat.format(date).uppercase()
    }

    fun parseDateToMillis(dateStr: String): Long {
        return parseDate(dateStr)?.time ?: System.currentTimeMillis()
    }
}

