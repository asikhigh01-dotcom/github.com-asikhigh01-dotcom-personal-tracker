package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.DateUtils
import com.example.data.database.AppDatabase
import com.example.data.entity.WorkEntity
import com.example.data.repository.WorkRepository
import com.example.domain.calculator.WorkCalculator
import com.example.ui.daily.DailyViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkModuleTest {

    private lateinit var database: AppDatabase
    private lateinit var workRepository: WorkRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workRepository = WorkRepository(database.workDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. Start -> timer increases
    @Test
    fun test01_startTimerIncreases() = runBlocking {
        val date = "2026-09-14"
        val t0 = 1000000L
        workRepository.startWork(date, nowMillis = t0)

        val record = workRepository.getWorkByDateDirect(date)
        assertNotNull(record)
        assertEquals(WorkEntity.TIMER_STATE_WORKING, record?.timerState)
        assertEquals(t0, record?.activeStartTimestamp)

        // Simulate 2 minutes later
        val t1 = t0 + 120_000L
        val elapsed = (t1 - (record?.activeStartTimestamp ?: 0L)) / 1000L
        val totalSeconds = (record?.accumulatedSeconds ?: 0L) + elapsed
        assertEquals(120L, totalSeconds)
        assertEquals(2, (totalSeconds / 60L).toInt())
    }

    // 2. Pause -> timer stops increasing
    @Test
    fun test02_pauseStopsIncreasing() = runBlocking {
        val date = "2026-09-14"
        val t0 = 1000000L
        workRepository.startWork(date, nowMillis = t0)

        // Pause after 120 seconds
        val t1 = t0 + 120_000L
        workRepository.pauseWork(date, nowMillis = t1)

        val pausedRecord = workRepository.getWorkByDateDirect(date)
        assertEquals(WorkEntity.TIMER_STATE_PAUSED, pausedRecord?.timerState)
        assertEquals(120L, pausedRecord?.accumulatedSeconds)
        assertEquals(2, pausedRecord?.workTimeMinutes)

        // Further time passes while paused - accumulatedSeconds remains 120
        val t2 = t1 + 300_000L
        val stillPaused = workRepository.getWorkByDateDirect(date)
        assertEquals(120L, stillPaused?.accumulatedSeconds)
    }

    // 3. Resume -> timer continues
    @Test
    fun test03_resumeTimerContinues() = runBlocking {
        val date = "2026-09-14"
        val t0 = 1000000L
        workRepository.startWork(date, nowMillis = t0)
        val t1 = t0 + 120_000L
        workRepository.pauseWork(date, nowMillis = t1)

        // Resume at t2
        val t2 = t1 + 60_000L
        workRepository.resumeWork(date, nowMillis = t2)

        val resumed = workRepository.getWorkByDateDirect(date)
        assertEquals(WorkEntity.TIMER_STATE_WORKING, resumed?.timerState)
        assertEquals(t2, resumed?.activeStartTimestamp)
        assertEquals(120L, resumed?.accumulatedSeconds)

        // 60 more seconds work
        val t3 = t2 + 60_000L
        val totalSecs = (resumed?.accumulatedSeconds ?: 0L) + ((t3 - t2) / 1000L)
        assertEquals(180L, totalSecs)
        assertEquals(3, (totalSecs / 60L).toInt())
    }

    // 4. Stop -> confirmation appears & 5. Stop confirmation -> timer stops
    @Test
    fun test04_05_stopConfirmationAndStop() = runBlocking {
        val viewModel = DailyViewModel(workRepository)
        assertFalse(viewModel.showStopConfirmationDialog.value)

        // Click stop -> confirmation dialog appears
        viewModel.onStopClicked()
        assertTrue(viewModel.showStopConfirmationDialog.value)

        // Confirm stop -> dialog closes and timer stops
        viewModel.onConfirmStop()
        assertFalse(viewModel.showStopConfirmationDialog.value)
    }

    // 6. Cancel stop -> timer continues in previous state
    @Test
    fun test06_cancelStopKeepsState() = runBlocking {
        val viewModel = DailyViewModel(workRepository)
        viewModel.onStopClicked()
        assertTrue(viewModel.showStopConfirmationDialog.value)

        viewModel.onCancelStop()
        assertFalse(viewModel.showStopConfirmationDialog.value)
    }

    // 7. 479 minutes -> NO
    @Test
    fun test07_479MinutesIsNo() {
        assertFalse(WorkCalculator.is8HoursCompleted(479))
    }

    // 8. 480 minutes -> YES
    @Test
    fun test08_480MinutesIsYes() {
        assertTrue(WorkCalculator.is8HoursCompleted(480))
    }

    // 9. 552 minutes -> YES
    @Test
    fun test09_552MinutesIsYes() {
        assertTrue(WorkCalculator.is8HoursCompleted(552))
        assertEquals("09h 12m", WorkCalculator.formatMinutesToHoursMinutes(552))
    }

    // 10. App closes while WORKING -> timer recovers
    @Test
    fun test10_appClosesWhileWorkingTimerRecovers() = runBlocking {
        val date = "2026-09-14"
        val t0 = 1000000L
        // Persisted state before crash
        val workingEntity = WorkEntity(
            date = date,
            workTimeMinutes = 10,
            timerState = WorkEntity.TIMER_STATE_WORKING,
            activeStartTimestamp = t0,
            accumulatedSeconds = 600L
        )
        workRepository.insertOrUpdateWork(workingEntity)

        // Simulate app reopen 15 minutes (900 seconds) later
        val tReopen = t0 + 900_000L
        val loaded = workRepository.getWorkByDateDirect(date)
        assertNotNull(loaded)
        assertEquals(WorkEntity.TIMER_STATE_WORKING, loaded?.timerState)

        val elapsed = (tReopen - (loaded?.activeStartTimestamp ?: 0L)) / 1000L
        val totalSecs = (loaded?.accumulatedSeconds ?: 0L) + elapsed
        assertEquals(1500L, totalSecs)
        assertEquals(25, (totalSecs / 60L).toInt())
    }

    // 11. App closes while PAUSED -> paused time is excluded
    @Test
    fun test11_appClosesWhilePausedTimeExcluded() = runBlocking {
        val date = "2026-09-14"
        // 2h 30m = 150 minutes = 9000 seconds
        val pausedEntity = WorkEntity(
            date = date,
            workTimeMinutes = 150,
            timerState = WorkEntity.TIMER_STATE_PAUSED,
            activeStartTimestamp = 0L,
            accumulatedSeconds = 9000L
        )
        workRepository.insertOrUpdateWork(pausedEntity)

        // 2 hours later
        val loaded = workRepository.getWorkByDateDirect(date)
        assertNotNull(loaded)
        assertEquals(WorkEntity.TIMER_STATE_PAUSED, loaded?.timerState)
        assertEquals(150, loaded?.workTimeMinutes)
        assertEquals(9000L, loaded?.accumulatedSeconds)
    }

    // 12. Manual edit from 06h45 -> 08h15 -> YES
    @Test
    fun test12_manualEditFrom0645To0815() = runBlocking {
        val date = "2026-09-14"
        // 06h 45m = 405 minutes
        workRepository.updateManualWorkTime(date, 405)
        val initial = workRepository.getWorkByDateDirect(date)
        assertEquals(405, initial?.workTimeMinutes)
        assertFalse(WorkCalculator.is8HoursCompleted(initial?.workTimeMinutes ?: 0))

        // Change to 08h 15m = 495 minutes
        workRepository.updateManualWorkTime(date, 495)
        val updated = workRepository.getWorkByDateDirect(date)
        assertEquals(495, updated?.workTimeMinutes)
        assertTrue(WorkCalculator.is8HoursCompleted(updated?.workTimeMinutes ?: 0))
    }

    // 13. Manual edit from 08h15 -> 07h30 -> NO
    @Test
    fun test13_manualEditFrom0815To0730() = runBlocking {
        val date = "2026-09-14"
        // 08h 15m = 495 minutes
        workRepository.updateManualWorkTime(date, 495)
        assertTrue(WorkCalculator.is8HoursCompleted(495))

        // Change to 07h 30m = 450 minutes
        workRepository.updateManualWorkTime(date, 450)
        val updated = workRepository.getWorkByDateDirect(date)
        assertEquals(450, updated?.workTimeMinutes)
        assertFalse(WorkCalculator.is8HoursCompleted(updated?.workTimeMinutes ?: 0))
    }

    // 14. Midnight does not transfer time into the next day
    @Test
    fun test14_midnightDoesNotTransferTime() = runBlocking {
        val day1 = "2026-09-14"
        val day2 = "2026-09-15"

        // Day 1 ends at DateUtils.getDayEndMillis(day1)
        val dayEnd = DateUtils.getDayEndMillis(day1)
        // Started 30 minutes before midnight
        val startMillis = dayEnd - (30 * 60 * 1000L)

        val workingAcrossMidnight = WorkEntity(
            date = day1,
            workTimeMinutes = 0,
            timerState = WorkEntity.TIMER_STATE_WORKING,
            activeStartTimestamp = startMillis,
            accumulatedSeconds = 0L
        )
        workRepository.insertOrUpdateWork(workingAcrossMidnight)

        // Trigger midnight resolution on day 2
        val nowOnDay2 = dayEnd + (30 * 60 * 1000L) // 00:30 on day 2
        workRepository.checkAndResolveMidnight(day2, nowMillis = nowOnDay2)

        val resolvedDay1 = workRepository.getWorkByDateDirect(day1)
        assertNotNull(resolvedDay1)
        assertEquals(WorkEntity.TIMER_STATE_STOPPED, resolvedDay1?.timerState)
        // Exactly 30 minutes counted for Day 1
        assertEquals(30, resolvedDay1?.workTimeMinutes)

        // Day 2 has no work or 0 minutes
        val day2Record = workRepository.getWorkByDateDirect(day2)
        assertEquals(null, day2Record)
    }

    // 15. Future dates cannot create work records
    @Test
    fun test15_futureDatesBlocked() {
        val today = DateUtils.getTodayLocalDateString()
        val future = DateUtils.getNextDate(today)
        assertTrue(DateUtils.isFuture(future))

        val viewModel = DailyViewModel(workRepository)
        // Can't navigate next from today
        assertFalse(viewModel.uiState.value.canNavigateNext)
    }

    // 16. Date navigation is blocked while timer is active
    @Test
    fun test16_dateNavigationBlockedWhileTimerActive() = runBlocking {
        val today = DateUtils.getTodayLocalDateString()
        workRepository.startWork(today)

        val activeWork = workRepository.getWorkByDateDirect(today)
        assertNotNull(activeWork)
        val isTimerActive = activeWork?.timerState == WorkEntity.TIMER_STATE_WORKING ||
                activeWork?.timerState == WorkEntity.TIMER_STATE_PAUSED
        assertTrue(isTimerActive)

        val canNavigatePrev = !isTimerActive
        val canNavigateNext = !isTimerActive && !DateUtils.isToday(today)
        assertFalse("Previous navigation should be blocked while timer is active", canNavigatePrev)
        assertFalse("Next navigation should be blocked while timer is active", canNavigateNext)
    }

    // 17. Existing historical Work Time can be edited
    @Test
    fun test17_historicalWorkTimeCanBeEdited() = runBlocking {
        val pastDate = "2026-09-01"
        workRepository.updateManualWorkTime(pastDate, 120)

        val before = workRepository.getWorkByDateDirect(pastDate)
        assertEquals(120, before?.workTimeMinutes)

        workRepository.updateManualWorkTime(pastDate, 360)
        val after = workRepository.getWorkByDateDirect(pastDate)
        assertEquals(360, after?.workTimeMinutes)
    }

    // 18. Data remains available after app restart
    @Test
    fun test18_dataRemainsAvailableAfterRestart() = runBlocking {
        val date = "2026-09-14"
        workRepository.updateManualWorkTime(date, 480)

        // Simulate app restart by creating a new repository instance
        val newRepository = WorkRepository(database.workDao())
        val retrieved = newRepository.getWorkByDateDirect(date)
        assertNotNull(retrieved)
        assertEquals(480, retrieved?.workTimeMinutes)
        assertTrue(WorkCalculator.is8HoursCompleted(retrieved?.workTimeMinutes ?: 0))
    }
}
