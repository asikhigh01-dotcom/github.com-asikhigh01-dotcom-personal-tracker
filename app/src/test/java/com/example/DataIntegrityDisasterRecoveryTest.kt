package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.DateUtils
import com.example.data.database.AppDatabase
import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import com.example.domain.calculator.DailyCalculator
import com.example.domain.calculator.ReviewCalculator
import com.example.domain.calculator.TradeCalculator
import com.example.domain.calculator.WorkCalculator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Senior Android Data-Integrity & Disaster-Recovery Verification Test Suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataIntegrityDisasterRecoveryTest {

    private lateinit var database: AppDatabase
    private lateinit var workRepository: WorkRepository
    private lateinit var tradeRepository: TradeRepository
    private lateinit var context: Context

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workRepository = WorkRepository(database.workDao())
        tradeRepository = TradeRepository(database.tradeDao(), database.ruleDao())
        tradeRepository.seedDefaultRulesIfNeeded()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun calculateEffectiveMinutes(entity: WorkEntity?, now: Long): Int {
        if (entity == null) return 0
        return when (entity.timerState) {
            WorkEntity.TIMER_STATE_WORKING -> {
                val elapsed = if (entity.activeStartTimestamp > 0L) {
                    maxOf(0L, (now - entity.activeStartTimestamp) / 1000L)
                } else 0L
                val totalSeconds = entity.accumulatedSeconds + elapsed
                (totalSeconds / 60L).toInt()
            }
            else -> entity.workTimeMinutes
        }
    }

    // 1. DATABASE SURVIVAL & CRITICAL CRUD INTEGRITY
    @Test
    fun test01_crudIntegrityAndCalculations() = runBlocking {
        val date = "2026-09-16"

        // Work: Create
        workRepository.updateManualWorkTime(date, 300)
        var work = workRepository.getWorkByDateDirect(date)
        assertNotNull(work)
        assertEquals(300, work?.workTimeMinutes)

        // Work: Edit
        workRepository.updateManualWorkTime(date, 480)
        work = workRepository.getWorkByDateDirect(date)
        assertEquals(480, work?.workTimeMinutes)
        assertTrue(WorkCalculator.is8HoursCompleted(work?.workTimeMinutes ?: 0))

        // Trades: Create
        val t1 = TradeEntity(0, date, 100.0, 150.0, 500.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList())
        val t2 = TradeEntity(0, date, 200.0, 180.0, -200.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1, 4))
        val id1 = tradeRepository.insertTrade(t1)
        val id2 = tradeRepository.insertTrade(t2)

        var trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(2, trades.size)
        assertEquals(300.0, TradeCalculator.calculateTotalPnl(trades), 0.001)

        // Trades: Edit
        val updatedT2 = t2.copy(tradeId = id2, pnl = -100.0, violatedRuleIds = listOf(4))
        tradeRepository.updateTrade(updatedT2)
        trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(400.0, TradeCalculator.calculateTotalPnl(trades), 0.001)
        val ruleBreakdown = DailyCalculator.calculateRuleBreakdown(RuleEntity.DEFAULT_RULES, trades)
        assertEquals(1, ruleBreakdown.find { it.rule.ruleId == 4 }?.violationCount)
        assertEquals(0, ruleBreakdown.find { it.rule.ruleId == 1 }?.violationCount)

        // Trades: Delete
        tradeRepository.deleteTradeById(id1)
        trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(1, trades.size)
        assertEquals(-100.0, TradeCalculator.calculateTotalPnl(trades), 0.001)

        // Work: Delete
        database.workDao().deleteByDate(date)
        work = workRepository.getWorkByDateDirect(date)
        assertNull(work)
    }

    // 2. TIMER PERSISTENCE SAFETY: NO TIME CORRUPTION, DUPLICATION, OR LEAKAGE
    @Test
    fun test02_timerPersistenceSafety() = runBlocking {
        val date = "2026-09-16"
        val t0 = 1000000000000L // arbitrary fixed epoch

        // Start
        workRepository.startWork(date, nowMillis = t0)
        var w = workRepository.getWorkByDateDirect(date)!!
        assertEquals(WorkEntity.TIMER_STATE_WORKING, w.timerState)
        assertEquals(t0, w.activeStartTimestamp)

        // Simulate app crash / restart after 30 minutes (1800s)
        val t1 = t0 + 1800000L
        val elapsedMinutes = calculateEffectiveMinutes(w, now = t1)
        assertEquals(30, elapsedMinutes)

        // Pause at t1
        workRepository.pauseWork(date, nowMillis = t1)
        w = workRepository.getWorkByDateDirect(date)!!
        assertEquals(WorkEntity.TIMER_STATE_PAUSED, w.timerState)
        assertEquals(1800L, w.accumulatedSeconds)
        assertEquals(0L, w.activeStartTimestamp)
        assertEquals(30, w.workTimeMinutes)

        // Wait 2 hours while paused (e.g. app in background or device restart)
        val t2 = t1 + 7200000L
        val pausedElapsed = calculateEffectiveMinutes(w, now = t2)
        // Paused time must NOT be added!
        assertEquals(30, pausedElapsed)

        // Resume at t2
        workRepository.resumeWork(date, nowMillis = t2)
        w = workRepository.getWorkByDateDirect(date)!!
        assertEquals(WorkEntity.TIMER_STATE_WORKING, w.timerState)
        assertEquals(t2, w.activeStartTimestamp)

        // Work for 50 more minutes (3000s)
        val t3 = t2 + 3000000L
        workRepository.stopWork(date, nowMillis = t3)
        w = workRepository.getWorkByDateDirect(date)!!
        assertEquals(WorkEntity.TIMER_STATE_STOPPED, w.timerState)
        assertEquals(0L, w.activeStartTimestamp)
        assertEquals(4800L, w.accumulatedSeconds) // 1800 + 3000 = 4800s
        assertEquals(80, w.workTimeMinutes) // 4800 / 60 = 80 min

        // Attempting to calculate after STOP at t4
        val t4 = t3 + 3600000L
        val stoppedElapsed = calculateEffectiveMinutes(w, now = t4)
        assertEquals(80, stoppedElapsed) // Timer does NOT continue after STOP
    }

    // 3. MIDNIGHT DATA SAFETY
    @Test
    fun test03_midnightBoundarySafety() = runBlocking {
        val yesterday = "2026-09-15"
        val today = "2026-09-16"
        val yesterdayEnd = DateUtils.getDayEndMillis(yesterday) // 2026-09-15 23:59:59.999

        // Start work yesterday at 22:00:00 (2 hours before midnight = 7200000ms = 7200s)
        val startTime = yesterdayEnd - 7200000L
        val activeWork = WorkEntity(
            date = yesterday,
            workTimeMinutes = 0,
            timerState = WorkEntity.TIMER_STATE_WORKING,
            activeStartTimestamp = startTime,
            accumulatedSeconds = 0L
        )
        database.workDao().insertOrUpdate(activeWork)

        // Simulate app reopening today at 08:00:00 AM
        val morningTime = yesterdayEnd + 28800000L // 8 hours into today
        workRepository.checkAndResolveMidnight(todayDate = today, nowMillis = morningTime)

        // Check yesterday's work
        val resolvedYesterday = workRepository.getWorkByDateDirect(yesterday)!!
        assertEquals(WorkEntity.TIMER_STATE_STOPPED, resolvedYesterday.timerState)
        assertEquals(0L, resolvedYesterday.activeStartTimestamp)
        // Capped exactly at midnight (120 minutes = 7200 seconds)
        assertEquals(120, resolvedYesterday.workTimeMinutes)

        // Check today's work: must NOT auto-start!
        val todayWork = workRepository.getWorkByDateDirect(today)
        assertNull("Today's work must not be automatically started", todayWork)
    }

    // 4. CALCULATION RECONSTRUCTION PURELY FROM RAW RECORDS
    @Test
    fun test04_calculationReconstructionFromRawRecords() = runBlocking {
        // Populate multi-day raw records
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val w1 = WorkEntity("2026-09-16", 480)
        val w2 = WorkEntity("2026-09-17", 479)
        val w3 = WorkEntity("2026-09-18", 500)
        database.workDao().insertOrUpdate(w1)
        database.workDao().insertOrUpdate(w2)
        database.workDao().insertOrUpdate(w3)

        val t1 = TradeEntity(0, "2026-09-16", 100.0, 110.0, 250.0, planFollowed = true, rulesViolated = false, emptyList())
        val t2 = TradeEntity(0, "2026-09-17", 100.0, 90.0, -150.0, planFollowed = true, rulesViolated = false, emptyList())
        val t3 = TradeEntity(0, "2026-09-18", 100.0, 120.0, 500.0, planFollowed = false, rulesViolated = true, listOf(2, 6))
        tradeRepository.insertTrade(t1)
        tradeRepository.insertTrade(t2)
        tradeRepository.insertTrade(t3)

        // Directly fetch raw data from DB
        val rawWork = database.workDao().getAllWorkDirect()
        val rawTrades = tradeRepository.getTradesByDateDirect("2026-09-16") +
                tradeRepository.getTradesByDateDirect("2026-09-17") +
                tradeRepository.getTradesByDateDirect("2026-09-18")

        // Reconstruct 3-day Review
        val review = ReviewCalculator.calculateReview(dates, rawWork, rawTrades)
        assertEquals(1459, review.totalWorkMinutes) // 480 + 479 + 500 = 1459m = 24h 19m
        assertEquals("24h 19m", review.formattedTotalWork)
        assertEquals("2/3 days", review.formatted8hCompleted) // 480 YES, 479 NO, 500 YES -> 2/3
        assertEquals(3, review.tradeCount)
        assertEquals(600.0, review.totalPnl, 0.001) // 250 - 150 + 500 = 600
        assertEquals("+₹600", review.formattedTotalPnl)
        assertEquals("2/3", review.formattedPlanFollowed)
        assertEquals("1/3", review.formattedViolationTrades)
        assertEquals(2, review.ruleViolationCount)

        // Reconstruct Daily metrics for Day 1
        val dailyDay1 = DailyCalculator.calculateDailyMetrics(rawWork.find { it.date == "2026-09-16" }, rawTrades.filter { it.date == "2026-09-16" })
        assertEquals("08h 00m", dailyDay1.formattedWorkTime)
        assertTrue(dailyDay1.is8HoursCompleted)
        assertEquals(1, dailyDay1.tradeCount)
        assertEquals("+₹250", dailyDay1.formattedTotalPnl)
        assertEquals("1 / 1", dailyDay1.planFollowedDisplay)
        assertEquals("0 / 1", dailyDay1.violationTradesDisplay)
        assertEquals(0, dailyDay1.ruleViolationCount)
    }

    // 5. HISTORICAL EDIT PROPAGATION WITHOUT CROSS-DATE CORRUPTION
    @Test
    fun test05_historicalEditIsolationAndPropagation() = runBlocking {
        val d1 = "2026-09-01"
        val d2 = "2026-09-02"

        workRepository.updateManualWorkTime(d1, 300)
        workRepository.updateManualWorkTime(d2, 400)

        // Mutate d1
        workRepository.updateManualWorkTime(d1, 500)

        // Verify d1 changed and d2 remained untouched
        val w1 = workRepository.getWorkByDateDirect(d1)
        val w2 = workRepository.getWorkByDateDirect(d2)
        assertEquals(500, w1?.workTimeMinutes)
        assertEquals(400, w2?.workTimeMinutes)
    }

    // 6. DATE BOUNDARY SAFETY: MONTH & YEAR BOUNDARIES
    @Test
    fun test06_dateBoundaryTransitions() {
        // Month boundary transition (Sep 30 -> Oct 01)
        val sep30 = "2026-09-30"
        val oct01 = DateUtils.getNextDate(sep30)
        assertEquals("2026-10-01", oct01)
        val prevOct01 = DateUtils.getPreviousDate(oct01)
        assertEquals(sep30, prevOct01)

        // Year boundary transition (Dec 31 -> Jan 01)
        val dec31 = "2026-12-31"
        val jan01 = DateUtils.getNextDate(dec31)
        assertEquals("2027-01-01", jan01)
        val prevJan01 = DateUtils.getPreviousDate(jan01)
        assertEquals(dec31, prevJan01)

        // Leap year boundary (Feb 28 in 2028 leap year -> Feb 29)
        val feb28_2028 = "2028-02-28"
        val feb29_2028 = DateUtils.getNextDate(feb28_2028)
        assertEquals("2028-02-29", feb29_2028)
    }

    // 7. FUTURE-DATE GUARD
    @Test
    fun test07_futureDateGuard() {
        val today = DateUtils.getTodayLocalDateString()
        val tomorrow = DateUtils.getNextDate(today)

        assertFalse(DateUtils.isFuture(today))
        assertTrue(DateUtils.isFuture(tomorrow))
        assertFalse(DateUtils.isFuture("2020-01-01"))
    }
}
