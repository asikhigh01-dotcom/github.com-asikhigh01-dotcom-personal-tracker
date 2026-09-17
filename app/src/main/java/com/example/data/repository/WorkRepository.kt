package com.example.data.repository

import com.example.data.DateUtils
import com.example.data.database.WorkDao
import com.example.data.entity.WorkEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing abstracted access to Work data.
 * Hides direct Room DAO access from the UI and ViewModel layers.
 */
class WorkRepository(
    private val workDao: WorkDao
) {
    fun getWorkByDate(date: String): Flow<WorkEntity?> =
        workDao.getWorkByDate(date)

    suspend fun getWorkByDateDirect(date: String): WorkEntity? =
        workDao.getWorkByDateDirect(date)

    suspend fun getActiveOrPausedWork(): WorkEntity? =
        workDao.getActiveOrPausedWork()

    suspend fun insertOrUpdateWork(work: WorkEntity) =
        workDao.insertOrUpdate(work)

    suspend fun deleteWork(work: WorkEntity) =
        workDao.delete(work)

    suspend fun deleteWorkByDate(date: String) =
        workDao.deleteByDate(date)

    /**
     * Begins counting active work time for the given date.
     */
    suspend fun startWork(date: String, nowMillis: Long = System.currentTimeMillis()) {
        val existing = workDao.getWorkByDateDirect(date)
        if (existing?.timerState == WorkEntity.TIMER_STATE_WORKING) {
            return // Already actively working on this date; do not reset start timestamp
        }

        // Stop any active or paused timers on other dates to prevent split concurrent tracking
        val allActive = workDao.getAllActiveOrPausedWork()
        for (active in allActive) {
            if (active.date != date) {
                stopWork(active.date, nowMillis)
            }
        }

        val freshExisting = workDao.getWorkByDateDirect(date)
        val accumulated = freshExisting?.accumulatedSeconds ?: ((freshExisting?.workTimeMinutes ?: 0) * 60L)
        val updated = WorkEntity(
            date = date,
            workTimeMinutes = (accumulated / 60L).toInt(),
            timerState = WorkEntity.TIMER_STATE_WORKING,
            activeStartTimestamp = nowMillis,
            accumulatedSeconds = accumulated
        )
        workDao.insertOrUpdate(updated)
    }

    /**
     * Pauses active-time accumulation for the given date.
     */
    suspend fun pauseWork(date: String, nowMillis: Long = System.currentTimeMillis()) {
        val existing = workDao.getWorkByDateDirect(date) ?: return
        if (existing.timerState != WorkEntity.TIMER_STATE_WORKING) return

        val elapsedMillis = if (existing.activeStartTimestamp > 0L) {
            maxOf(0L, nowMillis - existing.activeStartTimestamp)
        } else 0L
        val newAccumulated = existing.accumulatedSeconds + (elapsedMillis / 1000L)
        val updated = existing.copy(
            workTimeMinutes = (newAccumulated / 60L).toInt(),
            timerState = WorkEntity.TIMER_STATE_PAUSED,
            activeStartTimestamp = 0L,
            accumulatedSeconds = newAccumulated
        )
        workDao.insertOrUpdate(updated)
    }

    /**
     * Resumes counting active work time for the given date.
     */
    suspend fun resumeWork(date: String, nowMillis: Long = System.currentTimeMillis()) {
        val existing = workDao.getWorkByDateDirect(date) ?: return
        if (existing.timerState != WorkEntity.TIMER_STATE_PAUSED) return

        val updated = existing.copy(
            timerState = WorkEntity.TIMER_STATE_WORKING,
            activeStartTimestamp = nowMillis
        )
        workDao.insertOrUpdate(updated)
    }

    /**
     * Stops the current work timer permanently for that day.
     */
    suspend fun stopWork(date: String, nowMillis: Long = System.currentTimeMillis()) {
        val existing = workDao.getWorkByDateDirect(date) ?: return
        val additionalSeconds = if (existing.timerState == WorkEntity.TIMER_STATE_WORKING && existing.activeStartTimestamp > 0L) {
            maxOf(0L, (nowMillis - existing.activeStartTimestamp) / 1000L)
        } else 0L
        val totalSeconds = existing.accumulatedSeconds + additionalSeconds
        val totalMinutes = (totalSeconds / 60L).toInt()

        val updated = existing.copy(
            workTimeMinutes = totalMinutes,
            timerState = WorkEntity.TIMER_STATE_STOPPED,
            activeStartTimestamp = 0L,
            accumulatedSeconds = totalSeconds
        )
        workDao.insertOrUpdate(updated)
    }

    /**
     * Manually updates the work time for a date directly.
     * Sets timerState to STOPPED and resets activeStartTimestamp.
     */
    suspend fun updateManualWorkTime(date: String, newMinutes: Int) {
        val safeMinutes = maxOf(0, newMinutes)
        val existing = workDao.getWorkByDateDirect(date)
        val updated = existing?.copy(
            workTimeMinutes = safeMinutes,
            accumulatedSeconds = safeMinutes * 60L,
            timerState = WorkEntity.TIMER_STATE_STOPPED,
            activeStartTimestamp = 0L
        ) ?: WorkEntity(
            date = date,
            workTimeMinutes = safeMinutes,
            timerState = WorkEntity.TIMER_STATE_STOPPED,
            activeStartTimestamp = 0L,
            accumulatedSeconds = safeMinutes * 60L
        )
        workDao.insertOrUpdate(updated)
    }

    /**
     * Handles midnight boundaries:
     * If live or paused timers from previous calendar days were left running,
     * stops them at 23:59:59.999 of their respective dates without transferring time to the new day.
     */
    suspend fun checkAndResolveMidnight(todayDate: String, nowMillis: Long = System.currentTimeMillis()) {
        val activeWorkList = workDao.getAllActiveOrPausedWork()
        for (activeWork in activeWorkList) {
            if (activeWork.date < todayDate) {
                val dayEndMillis = DateUtils.getDayEndMillis(activeWork.date)
                val additionalSeconds = if (activeWork.timerState == WorkEntity.TIMER_STATE_WORKING && activeWork.activeStartTimestamp > 0L) {
                    val effectiveEnd = minOf(dayEndMillis, nowMillis)
                    maxOf(0L, (effectiveEnd - activeWork.activeStartTimestamp) / 1000L)
                } else 0L
                val finalSeconds = activeWork.accumulatedSeconds + additionalSeconds
                val finalMinutes = (finalSeconds / 60L).toInt()
                val resolved = activeWork.copy(
                    workTimeMinutes = finalMinutes,
                    timerState = WorkEntity.TIMER_STATE_STOPPED,
                    activeStartTimestamp = 0L,
                    accumulatedSeconds = finalSeconds
                )
                workDao.insertOrUpdate(resolved)
            }
        }
    }
}

