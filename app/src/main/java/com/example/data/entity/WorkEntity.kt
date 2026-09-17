package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing daily work tracking.
 * [date] is the logical unique identifier for the daily work record.
 * [workTimeMinutes] is stored as an integer representing total active work minutes.
 * Internal fields [timerState], [activeStartTimestamp], and [accumulatedSeconds]
 * provide crash and process-restart resilience for the timer.
 */
@Entity(tableName = "work")
data class WorkEntity(
    @PrimaryKey
    val date: String,
    val workTimeMinutes: Int,
    val timerState: String = TIMER_STATE_STOPPED,
    val activeStartTimestamp: Long = 0L,
    val accumulatedSeconds: Long = 0L
) {
    companion object {
        const val TIMER_STATE_STOPPED = "STOPPED"
        const val TIMER_STATE_WORKING = "WORKING"
        const val TIMER_STATE_PAUSED = "PAUSED"
    }
}

