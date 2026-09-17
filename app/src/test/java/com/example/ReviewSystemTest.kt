package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.TradeEntity
import com.example.data.entity.WorkEntity
import com.example.data.repository.ReviewRepository
import com.example.data.repository.TradeRepository
import com.example.data.repository.WorkRepository
import com.example.domain.calculator.ReviewBlockGenerator
import com.example.domain.calculator.ReviewCalculator
import com.example.domain.calculator.TradeCalculator
import com.example.domain.calculator.WorkCalculator
import com.example.domain.model.ReviewBlock
import com.example.domain.model.ReviewBlockUiModel
import com.example.ui.reviews.ReviewsViewModel
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReviewSystemTest {

    private lateinit var database: AppDatabase
    private lateinit var workRepository: WorkRepository
    private lateinit var tradeRepository: TradeRepository
    private lateinit var reviewRepository: ReviewRepository

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workRepository = WorkRepository(database.workDao())
        tradeRepository = TradeRepository(database.tradeDao(), database.ruleDao())
        reviewRepository = ReviewRepository(database.workDao(), database.tradeDao())
        tradeRepository.seedDefaultRulesIfNeeded()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. 3-day block does not become official before day 3 ends.
    @Test
    fun test01_blockNotOfficialBeforeDay3Ends() {
        val block = ReviewBlock(
            id = "2026-09-16_2026-09-18",
            startDate = "2026-09-16",
            endDate = "2026-09-18",
            dates = listOf("2026-09-16", "2026-09-17", "2026-09-18"),
            label = "16–18 SEP"
        )
        assertFalse(block.isCompleted("2026-09-16"))
        assertFalse(block.isCompleted("2026-09-17"))
        assertFalse(block.isCompleted("2026-09-18")) // Day 18 has not ended yet
        assertTrue(block.isCompleted("2026-09-19"))  // Available after day 18 ends
    }

    // 2. Inactive days still count.
    @Test
    fun test02_inactiveDaysStillCount() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        // Only 16 SEP has a work record; 17 and 18 are inactive
        val workList = listOf(WorkEntity(date = "2026-09-16", workTimeMinutes = 300))
        val metrics = ReviewCalculator.calculateReview(dates, workList, emptyList())

        assertEquals(3, metrics.calendarDays)
        assertEquals(300, metrics.totalWorkMinutes)
        assertEquals("05h 00m", metrics.formattedTotalWork)
        assertEquals(0, metrics.completedWorkDays) // 300 < 480
        assertEquals("0/3 days", metrics.formatted8hCompleted)
    }

    // 3. Three inactive days produce a valid completed review.
    @Test
    fun test03_threeInactiveDaysProduceValidCompletedReview() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), emptyList())

        assertEquals(3, metrics.calendarDays)
        assertEquals(0, metrics.totalWorkMinutes)
        assertEquals("00h 00m", metrics.formattedTotalWork)
        assertEquals(0, metrics.completedWorkDays)
        assertEquals("0/3 days", metrics.formatted8hCompleted)
        assertEquals(0, metrics.tradeCount)
        assertEquals(0.0, metrics.totalPnl, 0.001)
        assertEquals("₹0", metrics.formattedTotalPnl)
        assertEquals("—", metrics.formattedPlanFollowed)
        assertEquals("—", metrics.formattedViolationTrades)
        assertEquals(0, metrics.ruleViolationCount)
    }

    // 4. Work total is the sum of raw Work Time.
    @Test
    fun test04_workTotalIsSumOfRawWorkTime() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val workList = listOf(
            WorkEntity(date = "2026-09-16", workTimeMinutes = 495), // 08h 15m
            WorkEntity(date = "2026-09-17", workTimeMinutes = 450), // 07h 30m
            WorkEntity(date = "2026-09-18", workTimeMinutes = 390)  // 06h 30m
        )
        val metrics = ReviewCalculator.calculateReview(dates, workList, emptyList())

        assertEquals(1335, metrics.totalWorkMinutes)
        assertEquals("22h 15m", metrics.formattedTotalWork)
    }

    // 5. 8h Done counts calendar days, not work sessions.
    @Test
    fun test05_8hDoneCountsCalendarDaysNotSessions() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val workList = listOf(
            WorkEntity(date = "2026-09-16", workTimeMinutes = 480), // Day 16: YES
            WorkEntity(date = "2026-09-17", workTimeMinutes = 479), // Day 17: NO
            WorkEntity(date = "2026-09-18", workTimeMinutes = 200)  // Day 18: NO
        )
        val metrics = ReviewCalculator.calculateReview(dates, workList, emptyList())

        assertEquals(1, metrics.completedWorkDays)
        assertEquals("1/3 days", metrics.formatted8hCompleted)
        assertEquals("1/3", metrics.formatted8hCompact)
    }

    // 6. Trades aggregate across all three dates.
    @Test
    fun test06_tradesAggregateAcrossAllThreeDates() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val trades = listOf(
            TradeEntity(date = "2026-09-16", entry = 10.0, exit = 12.0, pnl = 20.0, planFollowed = true, rulesViolated = false),
            TradeEntity(date = "2026-09-16", entry = 10.0, exit = 12.0, pnl = 20.0, planFollowed = true, rulesViolated = false),
            TradeEntity(date = "2026-09-17", entry = 15.0, exit = 14.0, pnl = -10.0, planFollowed = true, rulesViolated = false),
            TradeEntity(date = "2026-09-18", entry = 20.0, exit = 25.0, pnl = 50.0, planFollowed = true, rulesViolated = false),
            TradeEntity(date = "2026-09-18", entry = 20.0, exit = 25.0, pnl = 50.0, planFollowed = true, rulesViolated = false),
            TradeEntity(date = "2026-09-18", entry = 20.0, exit = 25.0, pnl = 50.0, planFollowed = true, rulesViolated = false),
            TradeEntity(date = "2026-09-18", entry = 20.0, exit = 25.0, pnl = 50.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1))
        )
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)

        assertEquals(7, metrics.tradeCount)
    }

    // 7. P&L sums raw P&L values.
    @Test
    fun test07_pnlSumsRawPnlValues() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val trades = listOf(
            TradeEntity(date = "2026-09-16", entry = 100.0, exit = 105.0, pnl = 500.0, planFollowed = true, rulesViolated = false),
            TradeEntity(date = "2026-09-17", entry = 200.0, exit = 195.0, pnl = -200.0, planFollowed = true, rulesViolated = false),
            TradeEntity(date = "2026-09-18", entry = 300.0, exit = 310.0, pnl = 950.0, planFollowed = true, rulesViolated = false)
        )
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)

        assertEquals(1250.0, metrics.totalPnl, 0.001)
        assertEquals("+₹1,250", metrics.formattedTotalPnl)
    }

    // 8. Plan Followed counts YES trades.
    @Test
    fun test08_planFollowedCountsYesTrades() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val trades = (1..5).map {
            TradeEntity(date = "2026-09-16", entry = 10.0, exit = 11.0, pnl = 10.0, planFollowed = true, rulesViolated = false)
        } + (1..2).map {
            TradeEntity(date = "2026-09-17", entry = 10.0, exit = 9.0, pnl = -10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1))
        }
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)

        assertEquals(7, metrics.tradeCount)
        assertEquals(5, metrics.planFollowedCount)
        assertEquals("5/7", metrics.formattedPlanFollowed)
    }

    // 9. Violation Trades counts Rules Violated = YES trades.
    @Test
    fun test09_violationTradesCountsRulesViolatedYesTrades() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val trades = (1..5).map {
            TradeEntity(date = "2026-09-16", entry = 10.0, exit = 11.0, pnl = 10.0, planFollowed = true, rulesViolated = false)
        } + (1..2).map {
            TradeEntity(date = "2026-09-17", entry = 10.0, exit = 9.0, pnl = -10.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(2))
        }
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)

        assertEquals(2, metrics.violationTradeCount)
        assertEquals("2/7", metrics.formattedViolationTrades)
    }

    // 10. Rule Violations sums individual selected rules.
    @Test
    fun test10_ruleViolationsSumsIndividualSelectedRules() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val trades = listOf(
            TradeEntity(date = "2026-09-16", entry = 10.0, exit = 8.0, pnl = -20.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1, 2)), // 2 violations
            TradeEntity(date = "2026-09-17", entry = 10.0, exit = 8.0, pnl = -20.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(3)),       // 1 violation
            TradeEntity(date = "2026-09-18", entry = 10.0, exit = 12.0, pnl = 20.0, planFollowed = true, rulesViolated = false, violatedRuleIds = emptyList())     // 0 violations
        )
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)

        assertEquals(3, metrics.tradeCount)
        assertEquals(2, metrics.violationTradeCount)
        assertEquals(3, metrics.ruleViolationCount)
    }

    // 11. One trade with three violations = one Violation Trade and three Rule Violations.
    @Test
    fun test11_oneTradeWithThreeViolations() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val trades = listOf(
            TradeEntity(
                date = "2026-09-16",
                entry = 100.0,
                exit = 70.0,
                pnl = -300.0,
                planFollowed = false,
                rulesViolated = true,
                violatedRuleIds = listOf(1, 4, 7)
            )
        )
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)

        assertEquals(1, metrics.tradeCount)
        assertEquals(1, metrics.violationTradeCount)
        assertEquals("1/1", metrics.formattedViolationTrades)
        assertEquals(3, metrics.ruleViolationCount)
    }

    // 12. No-trade review displays P&L ₹0.
    @Test
    fun test12_noTradeReviewDisplaysPnlZero() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), emptyList())
        assertEquals("₹0", metrics.formattedTotalPnl)
    }

    // 13. No-trade review displays Plan Followed —.
    @Test
    fun test13_noTradeReviewDisplaysPlanFollowedDash() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), emptyList())
        assertEquals("—", metrics.formattedPlanFollowed)
    }

    // 14. No-trade review displays Violation Trades —.
    @Test
    fun test14_noTradeReviewDisplaysViolationTradesDash() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val metrics = ReviewCalculator.calculateReview(dates, emptyList(), emptyList())
        assertEquals("—", metrics.formattedViolationTrades)
    }

    // 15. Historical Work edit updates the affected review.
    @Test
    fun test15_historicalWorkEditUpdatesAffectedReview() = runBlocking {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        workRepository.updateManualWorkTime("2026-09-16", 480) // 8h
        workRepository.updateManualWorkTime("2026-09-17", 300) // 5h
        workRepository.updateManualWorkTime("2026-09-18", 200) // 3h20m

        var workRecords = reviewRepository.getAllWorkDirect()
        var metrics = ReviewCalculator.calculateReview(dates, workRecords, emptyList())
        assertEquals("1/3 days", metrics.formatted8hCompleted)
        assertEquals(980, metrics.totalWorkMinutes)

        // Historical edit: change 17 SEP from 5h to 8h
        workRepository.updateManualWorkTime("2026-09-17", 480)

        workRecords = reviewRepository.getAllWorkDirect()
        metrics = ReviewCalculator.calculateReview(dates, workRecords, emptyList())
        assertEquals("2/3 days", metrics.formatted8hCompleted)
        assertEquals(1160, metrics.totalWorkMinutes)
    }

    // 16. Historical Trade edit updates the affected review.
    @Test
    fun test16_historicalTradeEditUpdatesAffectedReview() = runBlocking {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val trade = TradeEntity(
            date = "2026-09-16",
            entry = 100.0,
            exit = 110.0,
            pnl = 500.0,
            planFollowed = true,
            rulesViolated = false
        )
        val id = tradeRepository.insertTrade(trade)

        var trades = reviewRepository.getAllTradesDirect()
        var metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)
        assertEquals("+₹500", metrics.formattedTotalPnl)

        // Historical edit: update trade PnL
        tradeRepository.updateTrade(trade.copy(tradeId = id, pnl = 700.0))

        trades = reviewRepository.getAllTradesDirect()
        metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)
        assertEquals("+₹700", metrics.formattedTotalPnl)
    }

    // 17. Historical Trade deletion updates the affected review.
    @Test
    fun test17_historicalTradeDeletionUpdatesAffectedReview() = runBlocking {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val trade = TradeEntity(
            date = "2026-09-18",
            entry = 50.0,
            exit = 60.0,
            pnl = 1000.0,
            planFollowed = true,
            rulesViolated = false
        )
        val id = tradeRepository.insertTrade(trade)

        var trades = reviewRepository.getAllTradesDirect()
        var metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)
        assertEquals(1, metrics.tradeCount)

        tradeRepository.deleteTrade(trade.copy(tradeId = id))

        trades = reviewRepository.getAllTradesDirect()
        metrics = ReviewCalculator.calculateReview(dates, emptyList(), trades)
        assertEquals(0, metrics.tradeCount)
        assertEquals("₹0", metrics.formattedTotalPnl)
    }

    // 18. Overall aggregates raw records directly.
    @Test
    fun test18_overallAggregatesRawRecordsDirectly() = runBlocking {
        // Block 1 (16–18 SEP)
        // Work = 22h 15m (1335 min), 8h Done = 1/3
        workRepository.updateManualWorkTime("2026-09-16", 495) // 8h 15m (YES)
        workRepository.updateManualWorkTime("2026-09-17", 450) // 7h 30m (NO)
        workRepository.updateManualWorkTime("2026-09-18", 390) // 6h 30m (NO)

        // Trades = 7, P&L = +1250, Plan = 5/7, Viol = 2/7, Rule Viol = 3
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-16", entry = 10.0, exit = 15.0, pnl = 500.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-16", entry = 10.0, exit = 15.0, pnl = 200.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-16", entry = 10.0, exit = 15.0, pnl = 100.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-17", entry = 20.0, exit = 18.0, pnl = -200.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-17", entry = 20.0, exit = 18.0, pnl = -100.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-18", entry = 30.0, exit = 35.0, pnl = 500.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(1, 2)))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-18", entry = 30.0, exit = 35.0, pnl = 250.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(3)))

        // Block 2 (19–21 SEP)
        // Work = 24h 30m (1470 min), 8h Done = 2/3
        workRepository.updateManualWorkTime("2026-09-19", 500) // 8h 20m (YES)
        workRepository.updateManualWorkTime("2026-09-20", 490) // 8h 10m (YES)
        workRepository.updateManualWorkTime("2026-09-21", 480) // 8h 00m (YES) -> Total = 500 + 490 + 480 = 1470 min = 24h 30m

        // Trades = 5, P&L = -400, Plan = 4/5, Viol = 1/5, Rule Viol = 1
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-19", entry = 50.0, exit = 40.0, pnl = -500.0, planFollowed = false, rulesViolated = true, violatedRuleIds = listOf(4)))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-19", entry = 50.0, exit = 55.0, pnl = 100.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-20", entry = 50.0, exit = 55.0, pnl = 0.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-21", entry = 50.0, exit = 55.0, pnl = 0.0, planFollowed = true, rulesViolated = false))
        tradeRepository.insertTrade(TradeEntity(date = "2026-09-21", entry = 50.0, exit = 55.0, pnl = 0.0, planFollowed = true, rulesViolated = false))

        val completedBlocks = ReviewBlockGenerator.getCompletedBlocks("2026-09-22")
        assertEquals(2, completedBlocks.size)

        val overallLabel = ReviewBlockGenerator.formatOverallLabel(completedBlocks)
        assertEquals("16–21 SEP", overallLabel)

        val allOverallDates = completedBlocks.flatMap { it.dates }
        assertEquals(6, allOverallDates.size)

        val allWork = reviewRepository.getAllWorkDirect()
        val allTrades = reviewRepository.getAllTradesDirect()
        val overallMetrics = ReviewCalculator.calculateReview(allOverallDates, allWork, allTrades)

        // Total Work = 1335 + 1470 = 2805 min = 46h 45m
        assertEquals(2805, overallMetrics.totalWorkMinutes)
        assertEquals("46h 45m", overallMetrics.formattedTotalWork)
        assertEquals(4, overallMetrics.completedWorkDays) // 16, 19, 20, 21 had >= 480
        assertEquals(12, overallMetrics.tradeCount)
        assertEquals(850.0, overallMetrics.totalPnl, 0.001)
        assertEquals("+₹850", overallMetrics.formattedTotalPnl)
        assertEquals("9/12", overallMetrics.formattedPlanFollowed)
        assertEquals("3/12", overallMetrics.formattedViolationTrades)
        assertEquals(4, overallMetrics.ruleViolationCount)
    }

    // 19. Overall does not average 3-day review values.
    @Test
    fun test19_overallDoesNotAverage3DayValues() {
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18", "2026-09-19", "2026-09-20", "2026-09-21")
        val workList = listOf(
            WorkEntity(date = "2026-09-16", workTimeMinutes = 480), // 1/3 in block 1
            WorkEntity(date = "2026-09-19", workTimeMinutes = 480), // 2/3 in block 2
            WorkEntity(date = "2026-09-20", workTimeMinutes = 480)
        )
        val metrics = ReviewCalculator.calculateReview(dates, workList, emptyList())

        // 3 of 6 calendar days met the threshold
        assertEquals(3, metrics.completedWorkDays)
        assertEquals(6, metrics.calendarDays)
        assertEquals("3/6 days", metrics.formatted8hCompleted)
    }

    // 20. Overall updates after historical edits.
    @Test
    fun test20_overallUpdatesAfterHistoricalEdits() = runBlocking {
        val completedBlocks = ReviewBlockGenerator.getCompletedBlocks("2026-09-22")
        val allDates = completedBlocks.flatMap { it.dates }

        workRepository.updateManualWorkTime("2026-09-16", 300)
        workRepository.updateManualWorkTime("2026-09-19", 300)

        var allWork = reviewRepository.getAllWorkDirect()
        var overallMetrics = ReviewCalculator.calculateReview(allDates, allWork, emptyList())
        assertEquals(600, overallMetrics.totalWorkMinutes)

        // Historical edit
        workRepository.updateManualWorkTime("2026-09-16", 480)

        allWork = reviewRepository.getAllWorkDirect()
        overallMetrics = ReviewCalculator.calculateReview(allDates, allWork, emptyList())
        assertEquals(780, overallMetrics.totalWorkMinutes)
        assertEquals("1/6 days", overallMetrics.formatted8hCompleted)
    }

    // 21. Reviews display newest completed block first.
    @Test
    fun test21_reviewsDisplayNewestCompletedBlockFirst() {
        val completed = ReviewBlockGenerator.getCompletedBlocks("2026-09-23")
        assertEquals(2, completed.size)

        val reversed = completed.reversed()
        assertEquals("19–21 SEP", reversed[0].label)
        assertEquals("16–18 SEP", reversed[1].label)
    }

    // 22. Future incomplete blocks are not shown as completed reviews.
    @Test
    fun test22_futureIncompleteBlocksNotShown() {
        // On 2026-09-20, block 19–21 has not completed yet
        val completed = ReviewBlockGenerator.getCompletedBlocks("2026-09-20")
        assertEquals(1, completed.size)
        assertEquals("16–18 SEP", completed[0].label)
    }

    // 23. No monthly automatic Overall reset occurs.
    @Test
    fun test23_noMonthlyAutomaticOverallResetOccurs() {
        // On 2026-10-04, 5 blocks from September and 1 block from October have completed
        val completed = ReviewBlockGenerator.getCompletedBlocks("2026-10-04")
        assertEquals(6, completed.size)
        val allDates = completed.flatMap { it.dates }
        assertEquals(18, allDates.size)

        val overallLabel = ReviewBlockGenerator.formatOverallLabel(completed)
        assertEquals("16 SEP – 3 OCT", overallLabel)

        val metrics = ReviewCalculator.calculateReview(allDates, emptyList(), emptyList())
        assertEquals(18, metrics.calendarDays)
    }

    // 24. App restart preserves all underlying raw data.
    @Test
    fun test24_appRestartPreservesAllUnderlyingRawData() = runBlocking {
        workRepository.updateManualWorkTime("2026-09-16", 500)
        tradeRepository.insertTrade(
            TradeEntity(
                date = "2026-09-16",
                entry = 100.0,
                exit = 120.0,
                pnl = 200.0,
                planFollowed = true,
                rulesViolated = false
            )
        )

        // Raw records in database
        val workBefore = workRepository.getWorkByDateDirect("2026-09-16")
        val tradesBefore = tradeRepository.getTradesByDateDirect("2026-09-16")
        assertNotNull(workBefore)
        assertEquals(500, workBefore?.workTimeMinutes)
        assertEquals(1, tradesBefore.size)
        assertEquals(200.0, tradesBefore[0].pnl, 0.001)

        // Calculate review metrics from raw data
        val dates = listOf("2026-09-16", "2026-09-17", "2026-09-18")
        val metrics = ReviewCalculator.calculateReview(dates, listOf(workBefore!!), tradesBefore)
        assertEquals("08h 20m", metrics.formattedTotalWork)
        assertEquals("+₹200", metrics.formattedTotalPnl)
        assertEquals("1/3 days", metrics.formatted8hCompleted)
        assertEquals("1/1", metrics.formattedPlanFollowed)
    }

    // 25. UI selection state for ReviewDetailDialog
    @Test
    fun test25_reviewDetailDialogSelection() {
        val viewModel = ReviewsViewModel(reviewRepository, initialDate = "2026-09-22")
        val block = ReviewBlock(
            id = "2026-09-16_2026-09-18",
            startDate = "2026-09-16",
            endDate = "2026-09-18",
            dates = listOf("2026-09-16", "2026-09-17", "2026-09-18"),
            label = "16–18 SEP"
        )
        val uiModel = ReviewBlockUiModel(block, ReviewCalculator.calculateReview(block.dates, emptyList(), emptyList()))

        assertNull(viewModel.selectedDetailBlock.value)

        viewModel.openReviewDetail(uiModel)
        assertEquals(uiModel, viewModel.selectedDetailBlock.value)

        viewModel.dismissReviewDetail()
        assertNull(viewModel.selectedDetailBlock.value)
    }
}
