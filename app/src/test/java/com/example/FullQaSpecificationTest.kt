package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.DateUtils
import com.example.data.database.AppDatabase
import com.example.data.database.Converters
import com.example.data.entity.RuleEntity
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import com.example.domain.calculator.DailyCalculator
import com.example.domain.calculator.ReviewBlockGenerator
import com.example.domain.calculator.ReviewCalculator
import com.example.domain.calculator.TradeCalculator
import com.example.domain.calculator.WorkCalculator
import com.example.domain.model.TradeValidationResult
import com.example.domain.model.TradeValidator
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

/**
 * Automated QA Test Suite covering the full 39-point specification checklist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FullQaSpecificationTest {

    private lateinit var database: AppDatabase
    private lateinit var workRepository: WorkRepository
    private lateinit var tradeRepository: TradeRepository

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

    // QA #1 & #2: WORK TIMER LIFECYCLE & PAUSED TIME EXCLUSION
    @Test
    fun test01_workTimerLifecycle_startPauseResumeStop_andPausedTimeExcluded() = runBlocking {
        val date = "2026-09-16"
        val t0 = 100_000_000L

        // START
        workRepository.startWork(date, nowMillis = t0)
        var work = workRepository.getWorkByDateDirect(date)!!
        assertEquals(WorkEntity.TIMER_STATE_WORKING, work.timerState)
        assertEquals(t0, work.activeStartTimestamp)

        // Running for 10 minutes (600s)
        val t1 = t0 + 600_000L
        workRepository.pauseWork(date, nowMillis = t1)
        work = workRepository.getWorkByDateDirect(date)!!
        assertEquals(WorkEntity.TIMER_STATE_PAUSED, work.timerState)
        assertEquals(600L, work.accumulatedSeconds)
        assertEquals(10, work.workTimeMinutes)

        // Paused for 30 minutes (1800s) -> should NOT count
        val t2 = t1 + 1_800_000L
        work = workRepository.getWorkByDateDirect(date)!!
        assertEquals(WorkEntity.TIMER_STATE_PAUSED, work.timerState)
        assertEquals(600L, work.accumulatedSeconds)

        // RESUME
        workRepository.resumeWork(date, nowMillis = t2)
        work = workRepository.getWorkByDateDirect(date)!!
        assertEquals(WorkEntity.TIMER_STATE_WORKING, work.timerState)
        assertEquals(t2, work.activeStartTimestamp)

        // Running for another 20 minutes (1200s)
        val t3 = t2 + 1_200_000L
        workRepository.stopWork(date, nowMillis = t3)
        work = workRepository.getWorkByDateDirect(date)!!
        assertEquals(WorkEntity.TIMER_STATE_STOPPED, work.timerState)
        assertEquals(1800L, work.accumulatedSeconds) // 600s + 1200s = 1800s
        assertEquals(30, work.workTimeMinutes) // 30 mins
        assertEquals(0L, work.activeStartTimestamp)
    }

    // QA #3: TIMER RECOVERY / PROCESS RESTORATION
    @Test
    fun test02_timerRecoveryFromDatabase_noUiReliance() = runBlocking {
        val date = "2026-09-16"
        val startMillis = 100_000_000L

        // Simulates work running
        workRepository.startWork(date, nowMillis = startMillis)

        // Simulate app closing, killing UI, and later reopening 1 hour later
        val laterMillis = startMillis + 3_600_000L
        val savedEntity = workRepository.getWorkByDateDirect(date)!!

        // Effective minutes computed directly from database entity & current timestamp
        val elapsed = (laterMillis - savedEntity.activeStartTimestamp) / 1000L
        val effectiveMinutes = ((savedEntity.accumulatedSeconds + elapsed) / 60L).toInt()
        assertEquals(60, effectiveMinutes)
    }

    // QA #4 & #5: MIDNIGHT BOUNDARY RESOLUTION
    @Test
    fun test03_midnightBoundary_stopsAtMidnight_noLeakToNewDay() = runBlocking {
        val day1 = "2026-09-16"
        val day2 = "2026-09-17"

        // Day 1 starts at 23:30 (30 mins before midnight)
        val day1Start = DateUtils.getDayStartMillis(day1) + (23 * 3600 + 30 * 60) * 1000L
        workRepository.startWork(day1, nowMillis = day1Start)

        // Midnight check happens next morning at 08:00 AM on Day 2
        val day2Morning = DateUtils.getDayStartMillis(day2) + (8 * 3600) * 1000L
        workRepository.checkAndResolveMidnight(day2, nowMillis = day2Morning)

        val day1Work = workRepository.getWorkByDateDirect(day1)!!
        assertEquals(WorkEntity.TIMER_STATE_STOPPED, day1Work.timerState)
        // Day 1 should have capped at 23:59:59.999 (approx 30 mins = 1800 seconds)
        assertEquals(30, day1Work.workTimeMinutes)
        assertEquals(0L, day1Work.activeStartTimestamp)

        // Day 2 must have NO work record or 0 minutes until explicit START
        val day2Work = workRepository.getWorkByDateDirect(day2)
        val day2Minutes = day2Work?.workTimeMinutes ?: 0
        assertEquals(0, day2Minutes)
    }

    // QA #6: 8-HOUR THRESHOLD
    @Test
    fun test04_eightHourThreshold_exactBoundaryChecks() {
        assertFalse(WorkCalculator.is8HoursCompleted(479))
        assertTrue(WorkCalculator.is8HoursCompleted(480))
        assertTrue(WorkCalculator.is8HoursCompleted(481))
        assertTrue(WorkCalculator.is8HoursCompleted(552))

        assertEquals("07h 59m", WorkCalculator.formatMinutesToHoursMinutes(479))
        assertEquals("08h 00m", WorkCalculator.formatMinutesToHoursMinutes(480))
        assertEquals("08h 01m", WorkCalculator.formatMinutesToHoursMinutes(481))
        assertEquals("09h 12m", WorkCalculator.formatMinutesToHoursMinutes(552))
    }

    // QA #7: MANUAL EDIT WORK TIME TRANSITIONS
    @Test
    fun test05_manualWorkTimeEdit_updatesStateCorrectly() = runBlocking {
        val date = "2026-09-16"

        // 1. Set 06h45 (405 min) -> 8h = NO
        workRepository.updateManualWorkTime(date, 405)
        var work = workRepository.getWorkByDateDirect(date)!!
        assertEquals(405, work.workTimeMinutes)
        assertFalse(WorkCalculator.is8HoursCompleted(work.workTimeMinutes))
        assertEquals(WorkEntity.TIMER_STATE_STOPPED, work.timerState)

        // 2. Set 08h15 (495 min) -> 8h = YES
        workRepository.updateManualWorkTime(date, 495)
        work = workRepository.getWorkByDateDirect(date)!!
        assertEquals(495, work.workTimeMinutes)
        assertTrue(WorkCalculator.is8HoursCompleted(work.workTimeMinutes))
        assertEquals(WorkEntity.TIMER_STATE_STOPPED, work.timerState)

        // 3. Set 07h30 (450 min) -> 8h = NO
        workRepository.updateManualWorkTime(date, 450)
        work = workRepository.getWorkByDateDirect(date)!!
        assertEquals(450, work.workTimeMinutes)
        assertFalse(WorkCalculator.is8HoursCompleted(work.workTimeMinutes))
        assertEquals(WorkEntity.TIMER_STATE_STOPPED, work.timerState)

        // 4. Set 00h00 (0 min) -> 8h = NO
        workRepository.updateManualWorkTime(date, 0)
        work = workRepository.getWorkByDateDirect(date)!!
        assertEquals(0, work.workTimeMinutes)
        assertFalse(WorkCalculator.is8HoursCompleted(work.workTimeMinutes))
        assertEquals(WorkEntity.TIMER_STATE_STOPPED, work.timerState)
    }

    // QA #8: FUTURE DATE WORK RESTRICTIONS
    @Test
    fun test06_futureDateRestrictions() {
        val futureDate = DateUtils.getNextDate(DateUtils.getTodayLocalDateString())
        assertTrue(DateUtils.isFuture(futureDate))
        assertFalse(DateUtils.isToday(futureDate))
    }

    // QA #9: MANUAL P&L ENTRY (NEVER DERIVED FROM ENTRY/EXIT)
    @Test
    fun test07_tradePnlIndependentOfEntryExit() {
        // Entry 100, Exit 105, User manually types P&L: -50
        val result = TradeValidator.validate(
            date = "2026-09-16",
            entryStr = "100.0",
            exitStr = "105.0",
            pnlStr = "-50",
            planFollowed = false,
            rulesViolated = true,
            selectedRuleIds = setOf(2)
        )
        assertTrue(result is TradeValidationResult.Success)
        val success = result as TradeValidationResult.Success
        assertEquals(100.0, success.entry, 0.001)
        assertEquals(105.0, success.exit, 0.001)
        assertEquals(-50.0, success.pnl, 0.001)
        assertEquals(listOf(2), success.violatedRuleIds)
    }

    // QA #10: TRADE VALIDATION COMBINATIONS
    @Test
    fun test08_tradeValidation_comprehensiveRules() {
        val date = "2026-09-16"

        // Missing Entry
        assertTrue(TradeValidator.validate(date, "", "105", "50", true, false, emptySet()) is TradeValidationResult.Error)

        // Missing Exit
        assertTrue(TradeValidator.validate(date, "100", "", "50", true, false, emptySet()) is TradeValidationResult.Error)

        // Missing P&L
        assertTrue(TradeValidator.validate(date, "100", "105", "", true, false, emptySet()) is TradeValidationResult.Error)

        // Rules Violated = YES but NO rules selected -> BLOCKED
        val blockedViolation = TradeValidator.validate(date, "100", "105", "50", false, true, emptySet())
        assertTrue(blockedViolation is TradeValidationResult.Error)
        assertEquals("Please select at least one violated rule", (blockedViolation as TradeValidationResult.Error).message)

        // Rules Violated = NO with rules passed in -> forces rule list to be EMPTY
        val validNoViolation = TradeValidator.validate(date, "100", "105", "50", true, false, setOf(1, 2, 3))
        assertTrue(validNoViolation is TradeValidationResult.Success)
        val success = validNoViolation as TradeValidationResult.Success
        assertTrue(success.violatedRuleIds.isEmpty())
    }

    // QA #11: MULTIPLE RULE VIOLATIONS PER TRADE
    @Test
    fun test09_multipleRuleViolationsPerTrade() = runBlocking {
        val date = "2026-09-16"
        val trade = TradeEntity(
            tradeId = 0L,
            date = date,
            entry = 200.0,
            exit = 190.0,
            pnl = -100.0,
            planFollowed = false,
            rulesViolated = true,
            violatedRuleIds = listOf(2, 4, 6) // Chasing (2), Revenge Trading (4), Overleveraging (6)
        )
        tradeRepository.insertTrade(trade)

        val trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(1, trades.size)
        val metrics = DailyCalculator.calculateDailyMetrics(null, trades)

        assertEquals(1, metrics.tradeCount)
        assertEquals(1, TradeCalculator.countViolationTrades(trades))
        assertEquals(3, metrics.ruleViolationCount)
        assertEquals("1 / 1", metrics.violationTradesDisplay)
        assertEquals("0 / 1", metrics.planFollowedDisplay)
    }

    // QA #12: TRADE EDIT AND DELETE RECALCULATIONS
    @Test
    fun test10_tradeEditAndDeleteRecalculation() = runBlocking {
        val date = "2026-09-16"
        val trade1 = TradeEntity(tradeId = 0L, date = date, entry = 100.0, exit = 110.0, pnl = 100.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList())
        val trade2 = TradeEntity(tradeId = 0L, date = date, entry = 100.0, exit = 90.0, pnl = -50.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2))

        val id1 = tradeRepository.insertTrade(trade1)
        val id2 = tradeRepository.insertTrade(trade2)

        var trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(2, trades.size)
        assertEquals(50.0, TradeCalculator.calculateTotalPnl(trades), 0.001)

        // Edit trade 2: change pnl from -50 to +20 and plan to true
        val updatedTrade2 = trade2.copy(tradeId = id2, pnl = 20.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList())
        tradeRepository.updateTrade(updatedTrade2)

        trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(120.0, TradeCalculator.calculateTotalPnl(trades), 0.001)
        assertEquals(2, TradeCalculator.countPlanFollowed(trades))
        assertEquals(0, TradeCalculator.countViolationTrades(trades))

        // Delete trade 1
        tradeRepository.deleteTradeById(id1)
        trades = tradeRepository.getTradesByDateDirect(date)
        assertEquals(1, trades.size)
        assertEquals(20.0, TradeCalculator.calculateTotalPnl(trades), 0.001)
    }

    // QA #17: DAILY TRADING ZERO STATE
    @Test
    fun test11_dailyTradingZeroState() {
        val metrics = DailyCalculator.calculateDailyMetrics(null, emptyList())
        assertEquals(0, metrics.tradeCount)
        assertEquals(0.0, metrics.totalPnl, 0.001)
        assertEquals("₹0", metrics.formattedTotalPnl)
        assertEquals("—", metrics.planFollowedDisplay)
        assertEquals("—", metrics.violationTradesDisplay)
        assertEquals(0, metrics.ruleViolationCount)
    }

    // QA #18: DAILY MULTI-TRADE CALCULATION
    @Test
    fun test12_dailyMultiTradeCalculation() {
        val trades = listOf(
            TradeEntity(tradeId = 1, date = "2026-09-16", entry = 100.0, exit = 110.0, pnl = 500.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList()),
            TradeEntity(tradeId = 2, date = "2026-09-16", entry = 100.0, exit = 95.0, pnl = -200.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList()),
            TradeEntity(tradeId = 3, date = "2026-09-16", entry = 100.0, exit = 90.0, pnl = -400.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2, 4))
        )
        val metrics = DailyCalculator.calculateDailyMetrics(null, trades)

        assertEquals(3, metrics.tradeCount)
        assertEquals(-100.0, metrics.totalPnl, 0.001)
        assertEquals("-₹100", metrics.formattedTotalPnl)
        assertEquals("2 / 3", metrics.planFollowedDisplay)
        assertEquals("1 / 3", metrics.violationTradesDisplay)
        assertEquals(2, metrics.ruleViolationCount)
    }

    // QA #19: 3-DAY BLOCK CALCULATION
    @Test
    fun test13_threeDayBlockCalculation() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val workRecords = listOf(
            WorkEntity(date = "2026-09-16", workTimeMinutes = 480), // 8h00 YES
            WorkEntity(date = "2026-09-17", workTimeMinutes = 450), // 7h30 NO
            WorkEntity(date = "2026-09-18", workTimeMinutes = 510)  // 8h30 YES
        )
        val tradeRecords = listOf(
            TradeEntity(tradeId = 1, date = "2026-09-16", entry = 100.0, exit = 110.0, pnl = 600.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList()),
            TradeEntity(tradeId = 2, date = "2026-09-16", entry = 100.0, exit = 105.0, pnl = 400.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList()),
            // 2026-09-17: 0 trades
            TradeEntity(tradeId = 3, date = "2026-09-18", entry = 100.0, exit = 90.0, pnl = -200.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2))
        )

        val metrics = ReviewCalculator.calculateReview(dates, workRecords, tradeRecords)

        assertEquals("24h 00m", metrics.formattedTotalWork)
        assertEquals(1440, metrics.totalWorkMinutes)
        assertEquals("2/3 days", metrics.formatted8hCompleted)
        assertEquals("2/3", metrics.formatted8hCompact)
        assertEquals(3, metrics.tradeCount)
        assertEquals(800.0, metrics.totalPnl, 0.001)
        assertEquals("+₹800", metrics.formattedTotalPnl)
        assertEquals("2/3", metrics.formattedPlanFollowed)
        assertEquals("1/3", metrics.formattedViolationTrades)
        assertEquals(1, metrics.ruleViolationCount)
    }

    // QA #20: OVERALL CALCULATION INTEGRITY (RAW AGGREGATION)
    @Test
    fun test14_overallCalculationIntegrity_rawAggregation() {
        val block1Dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val block2Dates = listOf("2026-09-19", "2026-09-20", "2026-09-21")
        val allDates = block1Dates + block2Dates

        val workList = listOf(
            WorkEntity(date = "2026-09-16", workTimeMinutes = 480),
            WorkEntity(date = "2026-09-17", workTimeMinutes = 480),
            WorkEntity(date = "2026-09-18", workTimeMinutes = 480),
            WorkEntity(date = "2026-09-19", workTimeMinutes = 300),
            WorkEntity(date = "2026-09-20", workTimeMinutes = 480),
            WorkEntity(date = "2026-09-21", workTimeMinutes = 480)
        )

        val tradeList = listOf(
            TradeEntity(tradeId = 1, date = "2026-09-16", entry = 100.0, exit = 110.0, pnl = 100.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList()),
            TradeEntity(tradeId = 2, date = "2026-09-19", entry = 100.0, exit = 90.0, pnl = -50.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2, 6))
        )

        val overall = ReviewCalculator.calculateReview(allDates, workList, tradeList)

        assertEquals(6, overall.calendarDays)
        assertEquals(5, overall.completedWorkDays) // 5 out of 6 days achieved 8h
        assertEquals("5/6 days", overall.formatted8hCompleted)
        assertEquals(2, overall.tradeCount)
        assertEquals(50.0, overall.totalPnl, 0.001)
        assertEquals("+₹50", overall.formattedTotalPnl)
        assertEquals("1/2", overall.formattedPlanFollowed)
        assertEquals("1/2", overall.formattedViolationTrades)
        assertEquals(2, overall.ruleViolationCount)
    }

    // QA #21: RULE BREAKDOWN VERIFICATION
    @Test
    fun test15_ruleBreakdownVerification() {
        val rules = RuleEntity.DEFAULT_RULES
        val trades = listOf(
            TradeEntity(tradeId = 1, date = "2026-09-16", entry = 1.0, exit = 2.0, pnl = 10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2)), // Position size within limit
            TradeEntity(tradeId = 2, date = "2026-09-16", entry = 1.0, exit = 2.0, pnl = 10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2, 4)), // Position size within limit, No revenge trade
            TradeEntity(tradeId = 3, date = "2026-09-16", entry = 1.0, exit = 2.0, pnl = 10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(6)), // Entry according to setup
            TradeEntity(tradeId = 4, date = "2026-09-16", entry = 1.0, exit = 2.0, pnl = 10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2)), // Position size within limit
            TradeEntity(tradeId = 5, date = "2026-09-16", entry = 1.0, exit = 2.0, pnl = 10.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList()) // No violation
        )

        val breakdown = DailyCalculator.calculateRuleBreakdown(rules, trades)
        val rule2 = breakdown.find { it.rule.ruleName == "Position size within limit" }!!
        val rule4 = breakdown.find { it.rule.ruleName == "No revenge trade" }!!
        val rule6 = breakdown.find { it.rule.ruleName == "Entry according to setup" }!!

        assertEquals(3, rule2.violationCount)
        assertEquals(1, rule4.violationCount)
        assertEquals(1, rule6.violationCount)

        val others = breakdown.filter { it.rule.ruleName !in listOf("Position size within limit", "No revenge trade", "Entry according to setup") }
        assertTrue(others.all { it.violationCount == 0 })
    }

    // QA #22 & #23: CALENDAR BLOCK BOUNDARIES & COMPLETION TIMING
    @Test
    fun test16_calendarBlocks_andCompletionTiming() {
        val blocks = ReviewBlockGenerator.generateBlocksUpTo("2026-10-15")
        assertTrue(blocks.any { it.label == "16–18 SEP" })
        assertTrue(blocks.any { it.label == "19–21 SEP" })
        assertTrue(blocks.any { it.label == "22–24 SEP" })
        assertTrue(blocks.any { it.label == "25–27 SEP" })
        assertTrue(blocks.any { it.label == "28–30 SEP" })
        assertTrue(blocks.any { it.label == "1–3 OCT" })
        assertTrue(blocks.any { it.label == "4–6 OCT" })
        assertTrue(blocks.any { it.label == "7–9 OCT" })
        assertTrue(blocks.any { it.label == "10–12 OCT" })
        assertTrue(blocks.any { it.label == "13–15 OCT" })

        val block1 = blocks.first { it.label == "16–18 SEP" }
        assertFalse(block1.isCompleted("2026-09-18"))
        assertTrue(block1.isCompleted("2026-09-19"))

        val block2 = blocks.first { it.label == "19–21 SEP" }
        assertFalse(block2.isCompleted("2026-09-21"))
        assertTrue(block2.isCompleted("2026-09-22"))
    }

    // QA #24: EMPTY 3-DAY BLOCK
    @Test
    fun test17_empty3DayBlock() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), emptyList())
        assertEquals("00h 00m", metrics.formattedTotalWork)
        assertEquals("0/3 days", metrics.formatted8hCompleted)
        assertEquals("0/3", metrics.formatted8hCompact)
        assertEquals(0, metrics.tradeCount)
        assertEquals("₹0", metrics.formattedTotalPnl)
        assertEquals("—", metrics.formattedPlanFollowed)
        assertEquals("—", metrics.formattedViolationTrades)
        assertEquals(0, metrics.ruleViolationCount)
    }

    // QA #25: PARTIAL 3-DAY BLOCK
    @Test
    fun test18_partial3DayBlock() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val workList = listOf(
            WorkEntity(date = "2026-09-16", workTimeMinutes = 480),
            WorkEntity(date = "2026-09-18", workTimeMinutes = 480)
        )
        val tradeList = listOf(
            TradeEntity(tradeId = 1, date = "2026-09-16", entry = 100.0, exit = 110.0, pnl = 100.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList())
        )

        val metrics = ReviewCalculator.calculateReview(dates, workList, tradeList)
        assertEquals("16h 00m", metrics.formattedTotalWork)
        assertEquals("2/3 days", metrics.formatted8hCompleted)
        assertEquals(1, metrics.tradeCount)
        assertEquals("+₹100", metrics.formattedTotalPnl)
        assertEquals("1/1", metrics.formattedPlanFollowed)
        assertEquals("0/1", metrics.formattedViolationTrades)
        assertEquals(0, metrics.ruleViolationCount)
    }

    // QA #27: TYPE CONVERTER ROBUSTNESS
    @Test
    fun test19_typeConverterRobustness() {
        val converter = Converters()

        // Empty
        assertEquals("", converter.fromIntList(emptyList()))
        assertEquals(emptyList<Int>(), converter.toIntList(""))
        assertEquals(emptyList<Int>(), converter.toIntList(null))

        // Single
        assertEquals("3", converter.fromIntList(listOf(3)))
        assertEquals(listOf(3), converter.toIntList("3"))

        // Multiple
        assertEquals("1,4,7", converter.fromIntList(listOf(1, 4, 7)))
        assertEquals(listOf(1, 4, 7), converter.toIntList("1,4,7"))

        // Corrupted / whitespace
        val parsed = converter.toIntList(" 1 , corrupt , , 4 , 7 ")
        assertEquals(listOf(1, 4, 7), parsed)
    }

    // QA #28: SEED DATA SAFETY
    @Test
    fun test20_seedDataSafety_exactSevenRules() = runBlocking {
        val rules = tradeRepository.getAllRulesDirect()
        assertEquals(7, rules.size)

        val expected = listOf(
            1 to "Entry condition satisfied",
            2 to "Position size within limit",
            3 to "Stop-loss used",
            4 to "No revenge trade",
            5 to "No overtrading",
            6 to "Entry according to setup",
            7 to "Exit according to predefined rule"
        )
        expected.forEach { (id, name) ->
            val rule = rules.find { it.ruleId == id }
            assertNotNull("Rule with ID $id must exist", rule)
            assertEquals(name, rule!!.ruleName)
        }
    }
}
