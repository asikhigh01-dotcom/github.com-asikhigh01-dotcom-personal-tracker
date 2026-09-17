package com.example.domain.calculator

import java.util.Locale

/**
 * Domain calculator for Work Module.
 * - Daily Target: 8 hours = 480 active minutes.
 * - 8 Hours Completed: workTimeMinutes >= 480 -> YES, workTimeMinutes < 480 -> NO.
 * - Display format: HHh MMm (e.g., 00h 15m, 03h 25m, 08h 00m, 09h 12m, 46h 45m).
 */
object WorkCalculator {
    const val TARGET_WORK_MINUTES = 480

    /**
     * Determines whether the 8-hour target is met.
     * Rule: workTimeMinutes >= 480 -> true (YES), < 480 -> false (NO).
     */
    fun is8HoursCompleted(workTimeMinutes: Int): Boolean {
        return workTimeMinutes >= TARGET_WORK_MINUTES
    }

    /**
     * Formats total minutes to "HHh MMm".
     * E.g.:
     * 15 -> 00h 15m
     * 205 -> 03h 25m
     * 480 -> 08h 00m
     * 552 -> 09h 12m
     * 2805 -> 46h 45m
     */
    fun formatMinutesToHoursMinutes(totalMinutes: Int): String {
        val nonNegativeMinutes = totalMinutes.coerceAtLeast(0)
        val hours = nonNegativeMinutes / 60
        val minutes = nonNegativeMinutes % 60
        return String.format(Locale.US, "%02dh %02dm", hours, minutes)
    }

    /**
     * Validates and calculates total minutes from hours and minutes.
     * Returns null if invalid (hours < 0 or minutes not in 0..59).
     */
    fun calculateMinutes(hours: Int, minutes: Int): Int? {
        if (hours < 0 || minutes < 0 || minutes > 59) {
            return null
        }
        return (hours * 60) + minutes
    }
}

